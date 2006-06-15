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
    private static final int MAX_URL_LENGTH = 128;

    private Display display;
    private Displayable previous;

    private TextField fieldMapPath;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceDesktop;
    private TextField fieldSimulatorPath;
    private TextField fieldSimulatorDelay;

    public SettingsForm(Display display) {
        this.display = display;
        this.previous = display.getCurrent();
    }

    public void show() {
        Form form = new Form("Settings");

        // map path field
        fieldMapPath = new TextField("Default Map", Config.getSafeInstance().getMapPath(), MAX_URL_LENGTH, TextField.URL);
        form.append(fieldMapPath);

        // location provider radioboxes
        choiceProvider = new ChoiceGroup("Location Provider", ChoiceGroup.EXCLUSIVE);
        String[] providers = Config.getSafeInstance().getLocationProviders();
        for (int N = providers.length, i = 0; i < N; i++) {
            String provider = providers[i];
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

        // simulator path field
        fieldSimulatorPath = new TextField("Simulator File", Config.getSafeInstance().getSimulatorPath(), 32, TextField.URL);
        fieldSimulatorDelay = new TextField("Simulator Delay", Integer.toString(Config.getSafeInstance().getSimulatorDelay()), 8, TextField.NUMERIC);
        form.append(fieldSimulatorPath);
        form.append(fieldSimulatorDelay);

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
            config.setMapPath(fieldMapPath.getString());
            config.setLocationProvider(choiceProvider.getString(choiceProvider.getSelectedIndex()));
            config.setFullscreen(choiceDesktop.isSelected(0));
            config.setSimulatorPath(fieldSimulatorPath.getString());
            config.setSimulatorDelay(Integer.parseInt(fieldSimulatorDelay.getString()));
            if ("Save".equals(command.getLabel())) {
                try {
                    config.update();

                    // TODO use event
                    Desktop.showConfirmation(display, "Configuration saved", previous);

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
