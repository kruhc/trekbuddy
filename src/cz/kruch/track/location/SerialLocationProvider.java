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
//#ifdef __ALL__
        if (Config.locationProvider == Config.LOCATION_PROVIDER_HGE100) {
            return "comm:AT5";
        }
//#endif
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

    /** @overriden */
    public int start() throws LocationException {
        // start thread
        (new Thread(this)).start();

        return LocationProvider._STARTING;
    }

    /** @overriden */
    public void stop() throws LocationException {

        // die gracefully... unlikely :-(
        super.stop();

        // non-blocking forcible kill
        Desktop.getDiskWorker().enqueue(new UniversalSoldier(UniversalSoldier.MODE_KILLER));
    }

    private void startWatcher() {
        Desktop.schedule(watcher = new UniversalSoldier(UniversalSoldier.MODE_WATCHER),
                         15000, 5000); // delay = 15 sec, period = 5 sec
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

//#ifdef __ALL__
        OutputStream hge = null;
//#endif

        // reset data
        reset();

        try {

            // debug
            setStatus("opening connection/stream");

            // open connection and stream
            connection = (StreamConnection) Connector.open(url, rw, true);
            synchronized (lock) {
                stream = connection.openInputStream();
            }

//#ifdef __ALL__

            // HGE-100 start
            if (isHge100) {
                synchronized (lock) {
                    hge = connection.openOutputStream();
                    hge.write("$STA\r\n".getBytes());
                    hge.flush();
                }
            }

//#endif

            // debug
            setStatus("connection/stream opened");

            // start keep-alive
            startKeepAlive();

            // start watcher
            startWatcher();

            // clear throwable
            setThrowable(null);

            // read data until error or stop request
            while (isGo()) {

                // location instance
                Location location = null;

                try {

                    // get next location
                    location = nextLocation(stream, observer);

                } catch (IOException e) {

                    // record if happened unexpectedly
                    if (isGo()) {
                        setStatus("I/O error");
                        setThrowable(e);
                    }

                    /*
                     * location is null - loop ends
                     */

                } catch (Throwable t) {

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

                // notify listener
                notifyListener2(location);

//#if __ALL__ && !__SYMBIAN__
                // free CPU on Samsung
                if (cz.kruch.track.TrackingMIDlet.samsung) {
                    Thread.yield();
                }
//#endif

            } // for (; go ;)

        } finally {

            // debug
            setStatus("stopping");

//#ifdef __ALL__

            // HGE-100 stop
            if (isHge100) {
                if (hge != null) {
                    try {
                        hge.write("$STO\r\n".getBytes());
                        hge.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }

//#endif
            
            // stop watcher
            stopWatcher();

            // stop keep-alive
            stopKeepAlive();

            // debug
            setStatus("closing stream and connection");

            // close input stream and connection
            int ioc = 0;
            synchronized (lock) {
                if (stream != null) {
                    api.file.File.closeQuietly(stream);
                    stream = null;
                    ioc++;
                }
            }
            api.file.File.closeQuietly(connection);
            connection = null;

            // debug
            setStatus("stream and connection closed");

//#if !__RIM__ && !__SYMBIAN__

            /* native finalizers? */
            if (ioc > 0) {
                System.gc(); // unconditional!!!
            }

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
                    setStatus("forced stream/connection close");

                    // close stream
                    synchronized (lock) {
                        api.file.File.closeQuietly(stream); // hopefully forces a thread blocked in read() to receive IOException
                        stream = null;
                    }

                    // debug
                    setStatus("stream/connection forcibly closed");

//#if !__RIM__ && !__SYMBIAN__

                    /* native finalizers?!? */
                    System.gc(); // unconditional!!!

//#endif

                } break;
            }
        }
    }
}
