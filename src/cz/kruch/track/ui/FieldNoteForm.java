// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.TextField;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

import api.lang.Int;

/**
 * Field note form.
 *
 * @author kruhc@seznam.cz
 */
final class FieldNoteForm implements CommandListener {

    private Callback callback;
    private Displayable next;
    private Form form;
    private ChoiceGroup typeChoice;
    private TextField textField;

    private String[] note;

    public FieldNoteForm(final Waypoint wpt, final Displayable next,
                         final Callback callback) {
        this(next, callback);
        this.note = new String[4];

        // cache name
//        if (wpt.getUserObject() instanceof GroundspeakBean) {
//            this.name = ((GroundspeakBean) wpt.getUserObject()).getName();
//        } else {
            this.note[0] = wpt.getName();
//        }

        // note timestamp
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTime(new Date(System.currentTimeMillis()));
        final StringBuffer sb = new StringBuffer(32);
        sb.append(calendar.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.DAY_OF_MONTH)).append(',');
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE)).append('Z');
        this.note[1] = sb.toString();

        // default note text
        sb.delete(0, sb.length());
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE)).append(' ');
        this.note[3] = sb.toString();
    }

    public FieldNoteForm(final String[] note, final Displayable next,
                         final Callback callback) {
        this(next, callback);
        this.note = note;
    }

    private FieldNoteForm(final Displayable next, final Callback callback) {
        this.callback = callback;
        this.next = next;
    }

    public FieldNoteForm show() {
        // create form
        form = new Form(Resources.getString(Resources.NAV_TITLE_FIELD_NOTE));

        // populate form
        form.append(createStringItem(Resources.getString(Resources.NAV_FLD_WPT_NAME), note[0]));
        form.append(createStringItem(Resources.getString(Resources.NAV_FLD_TIME), note[1]));
        typeChoice = new ChoiceGroup(Resources.getString(Resources.NAV_FLD_TYPE), Desktop.CHOICE_POPUP_TYPE,
                                     new String[]{ "Found it", "Didn't Find It", "Needs Maintenance", "Archive" }, null);
        if (note[2] != null) {
            for (int N = typeChoice.size(), i = 0; i < N; i++) {
                typeChoice.setSelectedIndex(i, note[2].equals(typeChoice.getString(i)));
            }
        }
        typeChoice.setFitPolicy(Choice.TEXT_WRAP_ON);
        form.append(typeChoice);
        textField = new TextField(Resources.getString(Resources.NAV_FLD_TEXT), note[3], 128, TextField.ANY);
        form.append(textField);

        // add commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_OK), Desktop.POSITIVE_CMD_TYPE, 1));
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.setCommandListener(this);

        // show
        Desktop.display.setCurrent(form);

        return this;
    }

    public void commandAction(Command command, Displayable displayable) {
        // form action
        final boolean fire = Desktop.POSITIVE_CMD_TYPE == command.getCommandType();

        // grab form data
        if (fire) {
            note[2] = typeChoice.getString(typeChoice.getSelectedIndex());
            note[3] = textField.getString();
        }

        // show next
        form.setCommandListener(null);
        Desktop.display.setCurrent(next);

        // callback
        if (fire) {
            callback.invoke(note, null, this);
        }
    }

    private static Item createStringItem(final String label, final String text) {
        final StringItem item = new StringItem(label + ": ", text);
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_SHRINK | Item.LAYOUT_NEWLINE_AFTER);
        item.setFont(Desktop.fontStringItems);
        return item;
    }

    private static StringBuffer appendTwoDigitStr(StringBuffer sb, final int i) {
        if (i < 10) {
            sb.append('0');
        }
        sb.append(i);

        return sb;
    }

    static String format(final String[] note, final StringBuffer sb) {
        sb.append(note[0]).append(',')
          .append(note[1]).append(',')
          .append(note[2]).append(',')
          .append('"').append(note[3]).append('"');
        return sb.toString();
    }
}
