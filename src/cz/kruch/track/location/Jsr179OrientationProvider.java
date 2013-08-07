// @LICENSE@

package cz.kruch.track.location;

//#ifndef __CN1__

import cz.kruch.track.event.Callback;

import java.util.TimerTask;

import api.location.LocationListener;

/**
 * Hackish support for terminal orientation.
 */
public final class Jsr179OrientationProvider
        extends TimerTask
        implements Callback {

    private LocationListener listener;

    /**
     * Invoke provider-specific action.
     *
     * @param action 0 - start, 1 - stop
     * @param throwable always <tt>null</tt>
     * @param param listener or <tt>null</tt>
     */
    public void invoke(Object action, Throwable throwable, Object param) {
        switch (((Integer) action).intValue()) {
            case 0: { // start
                this.listener = (LocationListener) param;
                cz.kruch.track.ui.Desktop.schedule(this, 0, 1000);
            } break;
            case 1: { // stop
                cancel();
            } break;
        }
    }

    public void run() {
        String status;
        try {
            javax.microedition.location.Orientation orientation = javax.microedition.location.Orientation.getOrientation();
            if (orientation != null) {
                final int azimuth = (int) orientation.getCompassAzimuth();
                notifySenser(azimuth);
                status = "azimuth ok";
            } else {
                status = "not calibrated";
            }
        } catch (javax.microedition.location.LocationException e) {
            if (e.getMessage() != null) {
                status = e.getMessage();
            } else {
                status = "not supported";
            }
        } catch (Throwable t) {
            cancel();
            status = t.toString();
        }
        cz.kruch.track.ui.nokia.DeviceControl.setSensorStatus(status);
    }

    private void notifySenser(final int heading) {
        if (listener != null) {
            try {
                listener.orientationChanged(null, heading);
            } catch (Throwable t) {
                // TODO
            }
        }
    }
}

//#endif