// @LICENSE@

package cz.kruch.track.location;

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
            case 0: {
                this.listener = (LocationListener) param;
                cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(this, 1000, 1000);
            } break;
            case 1: {
                cancel();
            } break;
        }
    }

    public void run() {
        try {
            javax.microedition.location.Orientation orientation = javax.microedition.location.Orientation.getOrientation();
            if (orientation != null/* && orientation.isOrientationMagnetic()*/) {
                notifySenser((int) orientation.getCompassAzimuth());
            }
        } catch (Throwable t) {
            // ignore
        }
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
