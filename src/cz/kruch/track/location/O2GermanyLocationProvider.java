// @LICENSE@

package cz.kruch.track.location;

import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.location.CartesianCoordinates;
import api.location.Datum;
import api.location.LocationProvider;
import api.location.ProjectionSetup;

import java.io.IOException;

import javax.microedition.io.Connector;

import cz.kruch.track.util.Mercator;
import cz.kruch.track.configuration.Config;

/**
 * O2 Germany provider implementation.
 *
 * @author kruhc@seznam.cz
 */
public final class O2GermanyLocationProvider
        extends api.location.LocationProvider
        implements javax.wireless.messaging.MessageListener, Runnable {

    private javax.wireless.messaging.MessageConnection impl;
    private int[] x, y, avg;
    private int offset;

    private volatile Object trigger;

    private static final ProjectionSetup[] zones = {
        new ProjectionSetup("GK 2", null, 6D, 0D, 1D, 2500000, 0),
        new ProjectionSetup("GK 3", null, 9D, 0D, 1D, 3500000, 0),
        new ProjectionSetup("GK 4", null, 12D, 0D, 1D, 4500000, 0),
        new ProjectionSetup("GK 5", null, 15D, 0D, 1D, 5500000, 0)
    };
    private static final Datum potsdam = Config.getDatum("Potsdam");

    public O2GermanyLocationProvider() {
        super("O2 Germany");
        this.x = new int[Config.o2Depth];
        this.y = new int[Config.o2Depth];
        this.avg = new int[2];
    }

    public int start() throws LocationException {
        try {
            this.impl = (javax.wireless.messaging.MessageConnection) Connector.open("cbs://:221", Connector.READ);
            this.impl.setMessageListener(this);
        } catch (Exception e) {
            throw new LocationException(e);
        }

        // start service thread
        (new Thread(this)).start();

        return LocationProvider._STARTING;
    }

    public void notifyIncomingMessage(javax.wireless.messaging.MessageConnection messageConnection) {
        synchronized (this) {
            trigger = Boolean.TRUE;
            notify();
        }
    }

    public void run() {
        // statistics
        restarts++;
        
        // let's roll
        baby();

        try {

            // pop messages until end
            while (isGo()) {
                synchronized (this) {
                    while (isGo() && trigger == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    trigger = null;
                }

                if (!isGo()) break;

                // result
                int state = TEMPORARILY_UNAVAILABLE;
                QualifiedCoordinates coords = null;

                // get and parse message
                final javax.wireless.messaging.Message message = impl.receive();
                if (message instanceof javax.wireless.messaging.TextMessage) {
                    final String text = ((javax.wireless.messaging.TextMessage) message).getPayloadText();
                    if (text != null && text.length() == 12) {
                        final int x = Integer.parseInt(text.substring(0, 6)) * 10;
                        final int y = Integer.parseInt(text.substring(6)) * 10;
                        final int[] avg = update(x, y);
                        final int idx = avg[0] / 1000000 - 2;
                        if (idx >= 0 && idx <= 3) {
                            final CartesianCoordinates xy = CartesianCoordinates.newInstance(null, avg[0], avg[1]);
                            final QualifiedCoordinates localQc = Mercator.MercatortoLL(xy, potsdam.ellipsoid, zones[idx]);
                            coords = potsdam.toWgs84(localQc);
                            QualifiedCoordinates.releaseInstance(localQc);
                            CartesianCoordinates.releaseInstance(xy);
                            state = AVAILABLE;
                        }
                    }
                }

                // signal state change
                if (updateLastState(state)) {
                    notifyListener(state);
                }

                // create location
                final Location location;
                if (coords != null) {
                    location = Location.newInstance(coords, System.currentTimeMillis(), 1);
                } else {
                    coords = QualifiedCoordinates.newInstance(Double.NaN, Double.NaN);
                    location = Location.newInstance(coords, System.currentTimeMillis(), 0);
                }

                // notify
                notifyListener(location);
            }

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // remove listener, and close and gc-free native provider
            try {
                impl.setMessageListener(null);
                impl.close();
            } catch (Exception e) {
                // ignore
            }
            impl = null;

            // almost dead
            zombie();
        }
    }

    private int[] update(final int easting, final int northing) {
        final int[] x = this.x;
        final int[] y = this.y;
        final int l = x.length;
        int avgx = 0;
        int avgy = 0;
        int c = 0;

        x[offset] = easting;
        y[offset] = northing;
        offset++;
        if (offset == l) {
            offset = 0;
        }
        for (int i = l; --i >= 0; ) {
            if (x[i] == 0 || y[i] == 0)
                continue;
            avgx += x[i];
            avgy += y[i];
            c++;
        }
        avg[0] = avgx / c;
        avg[1] = avgy / c;

        return avg;
    }
}
