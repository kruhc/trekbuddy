// @LICENSE@

package cz.kruch.track.device;

//#ifdef __SYMBIAN__

import java.io.IOException;

public class ApSelector {

    public static StringBuffer debug = new StringBuffer();

    private static ApSelector selector;

    String getApURL(String url) {
        return url;
    }

    void initDebugInfo() {
    }

    static String getURL(String url) throws IOException {
        if (selector == null) {
            try {
                Class.forName("com.nokia.mid.iapinfo.AccessPoint");
                selector = new NokiaApSelector();
            } catch (Throwable t) {
                selector = new ApSelector();
            }
            selector.initDebugInfo();
        }
        return selector.getApURL(url);
    }
}

//#endif