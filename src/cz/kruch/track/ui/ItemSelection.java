// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import java.util.Enumeration;

/**
 * Generic select-from-list form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class ItemSelection implements CommandListener {
    private final Callback callback;
    private final Displayable next;
    private final String title, selectLabel;

    ItemSelection(Displayable next, String title, Callback callback) {
        this(next, title, Resources.getString(Resources.DESKTOP_CMD_SELECT), callback);
    }

    private ItemSelection(Displayable next, String title, String selectLabel, Callback callback) {
        this.callback = callback;
        this.next = next;
        this.title = Resources.prefixed(title);
        this.selectLabel = selectLabel;
    }

    public void show(final Enumeration items, final String currentItem) {
        // add items and commands
        final List list = FileBrowser.sort2list(title, items, null);
        if (currentItem != null) {
            for (int i = list.size(); --i >= 0; ) {
                if (list.getString(i).equals(currentItem)) {
                    list.setSelectedIndex(i, true);
                    break;
                }
            }
        }
        list.setSelectCommand(new Command(selectLabel, Desktop.SELECT_CMD_TYPE, 0));
        list.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        list.setCommandListener(this);

        // show selection
        Desktop.display.setCurrent(list);
    }

    public void commandAction(Command command, Displayable displayable) {
        // close selection
        Desktop.display.setCurrent(next);

        // invoke callback
        if (command.getCommandType() == Desktop.SELECT_CMD_TYPE) {
            List list = (List) displayable;
            callback.invoke(list.getString(list.getSelectedIndex()), null, this);
        } else {
            callback.invoke(null, null, this);
        }
    }
}
