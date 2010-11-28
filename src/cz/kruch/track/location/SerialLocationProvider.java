// @LICENSE@

package cz.kruch.track.location;

import api.location.LocationException;
import api.location.LocationProvider;
import api.location.Location;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimerTask;

/**
 * Stream port (comm, btspp, tcp/ip) location provider implemenation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class SerialLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable {
    
    private static final long MAX_PARSE_PERIOD = 15 * 1000; // 15 sec
    private static final long MAX_STALL_PERIOD = 60 * 1000; // 1 min

    protected volatile String url;
    protected StreamConnection connection;

    private InputStream stream;
    private TimerTask watcher;

    private final Object lock = new Object();

    public SerialLocationProvider() throws LocationException {
        this("Serial");
    }

    /* package access */
    SerialLocationProvider(String name) throws LocationException {
        super(name);
    }

    public boolean isRestartable() {
        return !(getThrowable() instanceof RuntimeException);
    }

    protected String getKnownUrl() {
        if (Config.locationProvider == Config.LOCATION_PROVIDER_HGE100) {
            return "comm:AT5";
        }
        return Config.commUrl;
    }

    protected void refresh() {
    }

    protected void startKeepAlive() {
    }

    protected void stopKeepAlive() {
    }

    public void run() {
        // be gentle and safe
        if (restarts++ > 0) {

            // debug
            setStatus("refresh");

            // not so fast
            if (getLastState() == LocationProvider._STALLED) { // give hardware a while
                refresh();
            } else { // take your time (5 sec)
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        // start with last known?
        if (url == null) {
            url = getKnownUrl();
        }

        // reset last I/O stamp
        setLastIO(System.currentTimeMillis());

        // let's roll
        baby();

        try {

            // main loop
            gps();

        } catch (Throwable t) {

//#ifdef __LOG__
            t.printStackTrace();
//#endif

            // record
            setStatus("top level error");
            setThrowable(t);

        } finally {

            // almost dead
            zombie();
        }
    }

    public int start() throws LocationException {
        // start thread
        (new Thread(this)).start();

        return LocationProvider._STARTING;
    }

    /** @overriden */
    public void stop() throws LocationException {

        // die gracefully... unlikely :-(
        die();

        // non-blocking forcible kill
        (new Thread(new UniversalSoldier(UniversalSoldier.MODE_KILLER))).start();
    }

    private void startWatcher() {
        watcher = new UniversalSoldier(UniversalSoldier.MODE_WATCHER);
        Desktop.timer.schedule(watcher, 15000, 5000); // delay = 15 sec, period = 5 sec
    }

    private void stopWatcher() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
    }

    private void gps() throws IOException {
        final boolean isHge100 = Config.locationProvider == Config.LOCATION_PROVIDER_HGE100;
        final int rw = isHge100 || Config.btKeepAlive != 0 ? Connector.READ_WRITE : Connector.READ;

        // write access
        OutputStream os;

        // reset data
        reset();

        try {
            // debug
            setStatus("opening connection");

            // open connection
            connection = (StreamConnection) Connector.open(url, rw);

            // debug
            setStatus("opening input stream");

            // open stream for reading
            synchronized (lock) {
                stream = connection.openInputStream();
            }

            // HGE-100 start
            if (isHge100) {
                os = connection.openOutputStream();
                os.write("$STA\r\n".getBytes());
                os.flush();
            }

            // debug
            setStatus("stream opened");

            // start keep-alive
            startKeepAlive();

            // start watcher
            startWatcher();

            // clear throwable
            setThrowable(null);

            // read data until error or stop request
            while (isGo()) {

                Location location = null;

                try {

                    // get next location
                    location = nextLocation(stream, observer);

                } catch (IOException e) {
//#ifdef __LOG__
                    e.printStackTrace();
//#endif

                    // record if happened unexpectedly
                    if (isGo()) {
                        setStatus("I/O error");
                        setThrowable(e);
                    }

                    /*
                     * location is null - loop ends
                     */

                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
//#endif

                    // stop request?
                    if (t instanceof InterruptedException) {
                        setStatus("interrupted");
                        break;
                    }

                    // record
                    setThrowable(t);

                    // counter
                    errors++;

                    // blocked?
                    if (t instanceof SecurityException) {
                        break;
                    }

                    // ignore - let's go on
                    continue;
                }

                // end of data?
                if (location == null) {
                    break;
                }

                // new location timestamp
                setLast(System.currentTimeMillis());

                // send new location
                notifyListener(location);

                // state change?
                final int newState = location.getFix() > 0 ? AVAILABLE : TEMPORARILY_UNAVAILABLE;
                if (updateLastState(newState)) {
                    notifyListener(newState);
                }

//#ifdef __ALL__
                // free CPU on Samsung
                if (cz.kruch.track.TrackingMIDlet.samsung) {
                    Thread.yield();
                }
//#endif
            } // for (; go ;)

        } finally {

            // debug
            setStatus("stopping");

            // HGE-100 start
            if (isHge100) {
                os = connection.openOutputStream();
                os.write("$STA\r\n".getBytes());
                os.close();
            }
            
            // stop watcher
            stopWatcher();

            // stop keep-alive
            stopKeepAlive();

            // debug
            setStatus("closing stream and connection");

            // close input stream
            synchronized (lock) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        // ignore
                    }
                    stream = null;
                }
            }

            // close connection
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // ignore
                }
                connection = null;
            }

            // debug
            setStatus("stream and connection closed");

//#ifndef __RIM__
            /* native finalizers? */
            System.gc(); // unconditional!!!
//#endif            
        }
    }

    private final class UniversalSoldier extends TimerTask {
        private static final int MODE_WATCHER   = 0;
        private static final int MODE_KILLER    = 1;

        private int mode;

        public UniversalSoldier(int mode) {
            this.mode = mode;
        }

        public void run() {
            switch (mode) {
                case MODE_WATCHER: {
                    boolean notify = false;
                    final long now = System.currentTimeMillis();

                    if (now > (getLastIO() + MAX_STALL_PERIOD)) {
                        notify = updateLastState(_STALLED);
                        if (notify) {
                            stalls++;
                        }
                        cancel(); // we're done, only one stall per lifecycle
                    } else if (now > (getLast() + MAX_PARSE_PERIOD)) {
                        if (getLastState() == AVAILABLE) {
                            notify = updateLastState(TEMPORARILY_UNAVAILABLE);
                        }
                    }

                    if (notify) {
                        notifyListener(getLastState());
                    }
                } break;

                case MODE_KILLER: {
                    // debug
                    setStatus("forced stream close");

                    // close stream
                    synchronized (lock) {
                        if (stream != null) {
                            try {
                                stream.close(); // hopefully forces a thread blocked in read() to receive IOException
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }

                    // debug
                    setStatus("stream forcibly closed");
//#ifndef __RIM__
                    /* native finalizers?!? */
                    System.gc(); // unconditional!!!
//#endif
                } break;
            }
        }
    }
}
