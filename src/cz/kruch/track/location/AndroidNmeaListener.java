// @LICENSE@

package cz.kruch.track.location;

//#ifdef __ANDROID__

import cz.kruch.track.event.Callback;

public class AndroidNmeaListener implements android.location.GpsStatus.NmeaListener, Callback {
    
    private AndroidLocationProvider provider;

    AndroidNmeaListener() {
    }

    public void invoke(Object action, Throwable throwable, Object params) {
        switch (((Integer) action).intValue()) {
            case 0: {
                ((android.location.LocationManager) params).removeNmeaListener(this);
                provider = null;
            } break;
            case 1: {
                Object[] refs = (Object[]) params;
                provider = (AndroidLocationProvider) refs[0];
                ((android.location.LocationManager) refs[1]).addNmeaListener(this);
            } break;
        }
    }

    public void onNmeaReceived(long timestamp, String nmea) {
        provider.onNmeaReceived(timestamp, nmea);
    }
}

//#endif
