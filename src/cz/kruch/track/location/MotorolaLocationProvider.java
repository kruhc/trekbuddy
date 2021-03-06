// @LICENSE@

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.io.Connector;

import api.location.LocationException;
import api.location.LocationProvider;
import api.location.QualifiedCoordinates;
import api.location.Location;

/**
 * Motorola (Location API) provider implementation.
 *  
 * @author kruhc@seznam.cz
 */
public final class MotorolaLocationProvider
        extends api.location.LocationProvider
        implements com.motorola.location.PositionListener, Runnable {

    private com.motorola.location.PositionSource impl;

    private int accuracy, age, timeout;

    public MotorolaLocationProvider() {
        super("Motorola");
    }

    public int start() throws LocationException {
        try {
            // parse initialization params
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(Config.getLocationTimings(Config.LOCATION_PROVIDER_MOTOROLA),
                           CharArrayTokenizer.DEFAULT_DELIMS, false);
            this.accuracy = tokenizer.nextInt();
            this.age = tokenizer.nextInt();
            this.timeout = tokenizer.nextInt();
            tokenizer = null; // gc hint

            // init provider
            impl = (com.motorola.location.PositionSource) Connector.open("location://");

        } catch (Exception e) {
            throw new LocationException(e);
        }

        // start service thread
        (new Thread(this)).start();

        return LocationProvider._STARTING;
    }

    public void run() {
        // statistics
        restarts++;

        // let's roll
        baby();

        try {
            // set listener
            impl.addPositionListener(this);
            impl.generatePosition(accuracy, age, timeout);

            // wait for end
            synchronized (this) {
                while (isGo()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // remove listener and close and gc-free native provider
            impl.removePositionListener(this);
            impl.close();
            impl = null;

            // almost dead
            zombie();
        }
    }

    public void newPosition(com.motorola.location.AggregatePosition aggregatePosition) {
        try {
            if (aggregatePosition == null || aggregatePosition.hasLatLon() == false) {
                // signal state change
                if (updateLastState(TEMPORARILY_UNAVAILABLE)) {
                    notifyListener(TEMPORARILY_UNAVAILABLE);
                }
            } else {
                // signal state change
                if (updateLastState(AVAILABLE)) {
                    notifyListener(AVAILABLE);
                }

                final double lat = aggregatePosition.getLatitude() ;
                final double lon = aggregatePosition.getLongitude();
                final float alt = aggregatePosition.hasAltitude() ? aggregatePosition.getAltitude() : Float.NaN;

                // create up-to-date location
                final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(lat / 60 * 0.00001, lon / 60 * 0.00001, alt,
                                                                                 aggregatePosition.getLatLonAccuracy(), Float.NaN);
                final Location location = Location.newInstance(qc, aggregatePosition.getTimeStamp(), 1);
                if (aggregatePosition.hasTravelDirection()) {
                    location.setCourse(aggregatePosition.getTravelDirection());
                }
                if (aggregatePosition.hasSpeed()) {
                    location.setSpeed((float) aggregatePosition.getSpeed() / 1000);
                }

                // notify
                notifyListener(location);
            }
            
        } catch (Throwable t) {

            // record problem
            setStatus(t.toString());

        } finally {

            // generate another fix
            impl.generatePosition(accuracy, age, timeout);

        }
    }
}
