// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.TextField;

public class SettingsForm implements CommandListener {
    private Display display;
    private Displayable previous;

    private TextField fieldBook;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceDesktop;

    public SettingsForm(Display display) {
        this.display = display;
        this.previous = display.getCurrent();
    }

    public void show() {
        Form form = new Form("Settings");

        // map path field
        fieldBook = new TextField("Default Map", Config.getSafeInstance().getMapPath(), 32, TextField.URL);
        form.append(fieldBook);

        // location provider radioboxes
        choiceProvider = new ChoiceGroup("Location Provider", ChoiceGroup.EXCLUSIVE);
        for (int N = Config.LOCATION_PROVIDERS.length, i = 0; i < N; i++) {
            String provider = Config.LOCATION_PROVIDERS[i];
            int idx = choiceProvider.append(provider, null);
            if (provider.equals(Config.getSafeInstance().getLocationProvider())) {
                choiceProvider.setSelectedIndex(idx, true);
            }
        }
        form.append(choiceProvider);

        // desktop settings
        choiceDesktop = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceDesktop.setSelectedIndex(choiceDesktop.append("fullscreen", null), Config.getSafeInstance().isFullscreen());

        form.append(choiceDesktop);

        // add command and handling
        form.addCommand(new Command("Back", Command.BACK, 1));
        form.addCommand(new Command("Apply", Command.SCREEN, 2));
        form.addCommand(new Command("Save", Command.SCREEN, 2));
        form.setCommandListener(this);

        // show
        display.setCurrent(form);
    }

    public void commandAction(Command command, Displayable displayable) {
        boolean changed = false;

        if (command.getCommandType() == Command.BACK) {
            // restore previous screen
            display.setCurrent(previous);
        } else if (command.getCommandType() == Command.SCREEN) {
            // "Apply", "Save"
            Config config = Config.getSafeInstance();
            config.setMapPath(fieldBook.getString());
            config.setLocationProvider(choiceProvider.getString(choiceProvider.getSelectedIndex()));
            config.setFullscreen(choiceDesktop.isSelected(0));
            if ("Save".equals(command.getLabel())) {
                try {
                    config.update();

                    // TODO use event
                    Desktop.showInfo(display, "Configuration saved", previous);

                } catch (ConfigurationException e) {
                    // TODO use event
                    Desktop.showError(display, "Failed to save configuration", e);
                }
            } else {
                // restore previous screen
                display.setCurrent(previous);
            }

            // TODO detect any change and set 'changed' flagged accordingly
        }

        // notify that we are done
        (new Desktop.Event(Desktop.Event.EVENT_CONFIGURATION_CHANGED, changed ? Boolean.TRUE : Boolean.FALSE, null)).fire();
    }
}
