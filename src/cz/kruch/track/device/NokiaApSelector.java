// @LICENSE@

package cz.kruch.track.device;

//#ifdef __SYMBIAN__

import com.nokia.mid.iapinfo.IAPInfo;
import com.nokia.mid.iapinfo.AccessPoint;
import com.nokia.mid.iapinfo.IAPInfoException;
import com.nokia.mid.iapinfo.DestinationNetwork;

class NokiaApSelector extends ApSelector {

    private static final String NAME_LOOPBACK = "Loopback";

    NokiaApSelector() {
    }

    String getApURL(String url) {
        try {
            final AccessPoint ap = getLoopbackAp();
            if (ap != null) {
                return ap.getURL(url);
            }
        } catch (Exception e) {
            cz.kruch.track.maps.Map.networkInputStreamError = e.toString();
        }
        return url;
    }

    void initDebugInfo() {
        final StringBuffer sb = ApSelector.debug;
        try {
            final IAPInfo apinfo = IAPInfo.getIAPInfo();
            final AccessPoint[] aps = apinfo.getAccessPoints();
            if (aps != null) {
                sb.append("AP: ");
                for (int i = 0, N = aps.length; i < N; i++) {
                    final AccessPoint ap = aps[i];
                    sb.append(ap.getName()).append(':').append(ap.getID());
                    sb.append(',');
                }
            }
            final DestinationNetwork[] dns = apinfo.getDestinationNetworks();
            if (dns != null) {
                sb.append("DN: ");
                for (int i = 0, N = dns.length; i < N; i++) {
                    final DestinationNetwork dn = dns[i];
                    sb.append(dn.getName()).append(':').append(dn.getID());
                    sb.append(',');
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static AccessPoint getLoopbackAp() throws IAPInfoException {
        final IAPInfo apinfo = IAPInfo.getIAPInfo();
        final AccessPoint[] aps = apinfo.getAccessPoints();
        if (aps != null) {
            for (int i = 0, N = aps.length; i < N; i++) {
                final AccessPoint ap = aps[i];
                if (NAME_LOOPBACK.equals(ap.getName())) {
                    return ap;
                }
            }
        }
        return null;
    }
}

//#endif