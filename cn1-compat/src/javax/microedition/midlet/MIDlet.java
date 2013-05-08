package javax.microedition.midlet;

public abstract class MIDlet {

    protected MIDlet() {
    }

    public final String getAppProperty(String key) {
        if (key.equals("App-Flags")) {
            return "log_enabled";
        }
        return null;
    }

    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() {
        System.err.println("WARN MIDlet.notifyDestroyed");
        System.exit(0);
    }

    // CN1 simulator

    public void start() throws MIDletStateChangeException {
        startApp();
    }

    public void pause() {
        pauseApp();
    }

    public void destroy() throws MIDletStateChangeException {
        destroyApp(true);
    }
}
