// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.event.Callback;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.Item;

public class SettingsForm extends Form implements CommandListener {
    private static final int MAX_URL_LENGTH = 128;

    private Display display;
    private Callback callback;
    private Displayable previous;

    private TextField fieldMapPath;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceTracklogs;
    private ChoiceGroup choiceTracklogsFormat;
    private TextField fieldTracklogsDir;
    private TextField fieldSimulatorDelay;
    private ChoiceGroup choiceDesktop;

    public SettingsForm(Display display, Callback callback) {
        super("Settings");
        this.display = display;
        this.callback = callback;
        this.previous = display.getCurrent();
    }

    public void show() {
        // default map path field
        fieldMapPath = new TextField("Default Map", Config.getSafeInstance().getMapPath(), MAX_URL_LENGTH, TextField.URL);
        append(fieldMapPath);

        // location provider choice
        choiceProvider = new ChoiceGroup("Location Provider", ChoiceGroup.EXCLUSIVE);
        String[] providers = Config.getSafeInstance().getLocationProviders();
        for (int N = providers.length, i = 0; i < N; i++) {
            String provider = providers[i];
            int idx = choiceProvider.append(provider, null);
            if (provider.equals(Config.getSafeInstance().getLocationProvider())) {
                choiceProvider.setSelectedIndex(idx, true);
            }
        }
        append(choiceProvider);

        // tracklogs
        choiceTracklogs = new ChoiceGroup("Tracklogs", ChoiceGroup.MULTIPLE);
        choiceTracklogs.setSelectedIndex(choiceTracklogs.append("enabled", null), Config.getSafeInstance().isTracklogsOn());
        choiceTracklogsFormat = new ChoiceGroup("Tracklog Fomat", ChoiceGroup.EXCLUSIVE);
        for (int N = Config.TRACKLOGS_FORMAT.length, i = 0; i < N; i++) {
            String format = Config.TRACKLOGS_FORMAT[i];
            int idx = choiceTracklogsFormat.append(format, null);
            if (format.equals(Config.getSafeInstance().getTracklogsFormat())) {
                choiceTracklogsFormat.setSelectedIndex(idx, true);
            }
        }
        fieldTracklogsDir = new TextField("Tracklogs Dir", Config.getSafeInstance().getTracklogsDir(), MAX_URL_LENGTH, TextField.URL);

        // simulator
        fieldSimulatorDelay = new TextField("Simulator Delay", Integer.toString(Config.getSafeInstance().getSimulatorDelay()), 8, TextField.NUMERIC);

        // desktop settings
        choiceDesktop = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceDesktop.setSelectedIndex(choiceDesktop.append("fullscreen", null), Config.getSafeInstance().isFullscreen());
        append(choiceDesktop);

        // show provider specific options
        showProviderOptions();

        // add command and handling
        addCommand(new Command("Cancel", Command.BACK, 1));
        addCommand(new Command("Apply", Command.SCREEN, 1));
        addCommand(new Command("Save", Command.SCREEN, 2));
        setCommandListener(this);
        setItemStateListener(new ItemStateListener() {
            public void itemStateChanged(Item item) {
                if (item == choiceProvider) {
                    showProviderOptions();
                }
            }
        });

        // show
        display.setCurrent(this);
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
            config.setTracklogsOn(choiceTracklogs.isSelected(0));
            config.setTracklogsFormat(choiceTracklogsFormat.getString(choiceTracklogsFormat.getSelectedIndex()));
            config.setTracklogsDir(fieldTracklogsDir.getString());
            config.setSimulatorDelay(Integer.parseInt(fieldSimulatorDelay.getString()));
            config.setFullscreen(choiceDesktop.isSelected(0));
            if ("Save".equals(command.getLabel())) {
                try {
                    config.update();

                    // TODO use event
                    Desktop.showConfirmation(display, "Configuration saved", previous);

                } catch (ConfigurationException e) {
                    // TODO use event
                    Desktop.showError(display, "Failed to save configuration", e, null);
                }
            } else {
                // restore previous screen
                display.setCurrent(previous);
            }

            // TODO detect any change and set 'changed' flagged accordingly
        }

        // notify that we are done
        callback.invoke(changed ? Boolean.TRUE : Boolean.FALSE, null);
    }

    private void showProviderOptions() {
        for (int i = 0; i < size(); i++) {
            Item item = get(i);
            if (!(fieldMapPath == item || choiceProvider == item || choiceDesktop == item)) {
                delete(i);
                i = 0;
            }
        }

        int insertAt = 0;
        for (int N = size(), i = 0; i < N; i++) {
            Item item = get(i);
            if (choiceDesktop == item) {
                insertAt = i;
                break;
            }
        }

        String provider = choiceProvider.getString(choiceProvider.getSelectedIndex());
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(provider)) {
            insert(insertAt, fieldSimulatorDelay);
        } else if (Config.LOCATION_PROVIDER_JSR82.equals(provider)) {
            insert(insertAt, fieldTracklogsDir);
            insert(insertAt, choiceTracklogsFormat);
            insert(insertAt, choiceTracklogs);
        } else if (Config.LOCATION_PROVIDER_JSR179.equals(provider)) {
            insert(insertAt, fieldTracklogsDir);
            insert(insertAt, choiceTracklogs);
        }
    }
}
