// @LICENSE@

package cz.kruch.track.util;

//#ifdef __LOG__

/**
 * Logger for debugging - output goes to standard out/err or platform log.
 *
 * @author kruhc@seznam.cz
 */
public final class Logger {
    private static final String LEVEL_DEBUG  = "DEBUG";
    private static final String LEVEL_INFO   = "INFO";
    private static final String LEVEL_WARN   = "WARN";
    private static final String LEVEL_ERROR  = "ERROR";

    private String cname;
    private boolean enabled;

    public static void out(String message) {
//#ifdef __ANDROID__
        android.util.Log.i("TrekBuddy", message);
//#elifdef __CN1__
        com.codename1.io.Log.p(message, com.codename1.io.Log.INFO);
//#else
        System.out.println(message);
//#endif
    }

    public static void printStackTrace(Throwable t) {
//#ifndef __CN1__
        t.printStackTrace();
//#else
        com.codename1.io.Log.e(t);
//#endif
    }

    public Logger(String componentName) {
        this.cname = componentName;
        this.enabled = cz.kruch.track.TrackingMIDlet.isLogEnabled();
    }

    public boolean isEnabled() { // effectively isDebugEnabled()
//#ifndef __CN1__
        return enabled;
//#else
        return com.codename1.io.Log.getLevel() == com.codename1.io.Log.DEBUG; 
//#endif
    }

    public void debug(String message) {
        log(LEVEL_DEBUG, message);
    }

    public void debug(String message, Throwable t) {
        log(LEVEL_DEBUG, message);
        log(t);
    }

    public void info(String message) {
        log(LEVEL_INFO, message);
    }

    public void warn(String message) {
        log(LEVEL_WARN, message);
    }

    public void warn(String message, Throwable t) {
        log(LEVEL_WARN, message);
        log(t);
    }

    public void error(String message) {
        log(LEVEL_ERROR, message);
    }

    public void error(String message, Throwable t) {
        log(LEVEL_ERROR, message);
        log(t);
    }

    private void log(String severity, String message) {
        if (enabled) {
//#ifndef __CN1__
            System.out.println("[" + (new java.util.Date()) + "] " + cname + " - " + message);
            System.out.flush();
//#else
            com.codename1.io.Log.p(cname + " - " + message);
//#endif
        }
    }

    private void log(Throwable t) {
        if (enabled) {
//#ifndef __CN1__
            t.printStackTrace();
            System.err.flush();
//#else
            com.codename1.io.Log.e(t);
//#endif
        }
    }
}

//#endif
