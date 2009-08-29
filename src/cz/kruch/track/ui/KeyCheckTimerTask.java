// @LICENSE@

package cz.kruch.track.ui;

import java.util.TimerTask;

final class KeyCheckTimerTask extends TimerTask {

    public KeyCheckTimerTask() {
    }

    /*
    * "A key's bit will be 1 if the key is currently down or has
    * been pressed at least once since the last time this method
    * was called."
    *
    * Therefore the dummy getKeyStates() call before invoking run().
    */
    public void run() {
/* must correspond with DeviceScreen.run()!!!
        Desktop.screen.getKeyStates(); // trick
*/
        Desktop.screen.callSerially(Desktop.screen);
        Desktop.screen.firedKeyRepeated();
    }
}
