// @LICENSE@

package cz.kruch.track.util;

//#ifdef __LOG__

/**
 * Logger for debugging - output goes to standard out/err or platform log.
 *
 * @author kruhc@seznam.cz
 */
public final class Logger /*extends net.trekbuddy.util.Logger*/ {

    private static boolean enabled;

    public static void out(String message) {
//#ifdef __ANDROID__
        android.util.Log.i("TrekBuddy", message);
//#elifdef __CN1__
        // no out
//#else
        System.out.println(message);
//#endif
    }

    public static void out(Throwable t) {
//#ifdef __ANDROID__
        android.util.Log.e("TrekBuddy", null, t);
//#elifdef __CN1__
        // no out
//#else
        t.printStackTrace();
//#endif
    }

    public static void setEnabled(boolean enabled) {
//#ifndef __CN1__
        Logger.enabled = enabled;
//#endif
    }

    public Logger(String category) {
//        super(category);
    }

    public boolean isEnabled() { // effectively isDebugEnabled()
//#ifndef __CN1__
        return enabled;
//#else
        return net.trekbuddy.util.Logger.getLevel() == net.trekbuddy.util.Logger.DEBUG;
//#endif
    }

    public void debug(String message) {
        out(message);
    }

    public void debug(String message, Throwable throwable) {
        out(message);
        out(throwable);
    }

    public void warn(String message) {
        out(message);
    }

    public void error(String message) {
        out(message);
    }

    public void error(String message, Throwable throwable) {
        out(message);
        out(throwable);
    }
}

//#endif
