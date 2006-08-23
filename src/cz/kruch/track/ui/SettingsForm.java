// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.event.Callback;
import cz.kruch.track.TrackingMIDlet;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.Item;

final class SettingsForm extends Form implements CommandListener, ItemStateListener {
    private static final int MAX_URL_LENGTH = 256;

    private Callback callback;

    private TextField fieldMapPath;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceTracklog;
    private ChoiceGroup choiceTracklogsFormat;
    private TextField fieldTracklogsDir;
    private TextField fieldSimulatorDelay;
    private ChoiceGroup choiceMisc;
    private ChoiceGroup choiceTimezone;

    public SettingsForm(Callback callback) {
        super("Settings");
        this.callback = callback;
    }

    public void show() {
        // default map path field
        if (TrackingMIDlet.isFs()) {
            fieldMapPath = new TextField("Default Map", Config.getSafeInstance().getMapPath(), MAX_URL_LENGTH, TextField.URL);
            append(fieldMapPath);
        }

        // location provider choice
        String[] providers = Config.getSafeInstance().getLocationProviders();
        choiceProvider = new ChoiceGroup("Location Provider", ChoiceGroup.EXCLUSIVE);
        for (int N = providers.length, i = 0; i < N; i++) {
            String provider = providers[i];
            int idx = choiceProvider.append(provider, null);
            if (provider.equals(Config.getSafeInstance().getLocationProvider())) {
                choiceProvider.setSelectedIndex(idx, true);
            }
        }
        if (providers.length > 0) {
            append(choiceProvider);
        }

        // timezone
        choiceTimezone = new ChoiceGroup("Timezone", ChoiceGroup.POPUP);
        for (int i = -12; i <= 12; i++) {
            boolean selected = Config.getSafeInstance().getTimeZone() == i;
            if (i == 0) {
                choiceTimezone.setSelectedIndex(choiceTimezone.append("GMT", null), selected);
            } else if (i > 0) {
                choiceTimezone.setSelectedIndex(choiceTimezone.append("GMT+" + Integer.toString(i), null), selected);
            } else {
                choiceTimezone.setSelectedIndex(choiceTimezone.append("GMT" + Integer.toString(i), null), selected);
            }
        }
        append(choiceTimezone);

        // tracklogs
        if (TrackingMIDlet.isFs()) {
            choiceTracklog = new ChoiceGroup("Tracklog", ChoiceGroup.MULTIPLE);
            choiceTracklog.setSelectedIndex(choiceTracklog.append("enabled", null), Config.getSafeInstance().isTracklogsOn());
            choiceTracklogsFormat = new ChoiceGroup("Tracklog Fomat", ChoiceGroup.EXCLUSIVE);
            for (int N = Config.TRACKLOGS_FORMAT.length, i = 0; i < N; i++) {
                String format = Config.TRACKLOGS_FORMAT[i];
                int idx = choiceTracklogsFormat.append(format, null);
                if (format.equals(Config.getSafeInstance().getTracklogsFormat())) {
                    choiceTracklogsFormat.setSelectedIndex(idx, true);
                }
            }
            fieldTracklogsDir = new TextField("Tracklogs Dir", Config.getSafeInstance().getTracklogsDir(), MAX_URL_LENGTH, TextField.URL);
        }

        // simulator
        if (TrackingMIDlet.isFs()) {
            fieldSimulatorDelay = new TextField("Simulator Delay", Integer.toString(Config.getSafeInstance().getSimulatorDelay()), 8, TextField.NUMERIC);
        }

        // misc settings
        choiceMisc = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceMisc.setSelectedIndex(choiceMisc.append("fullscreen", null), Config.getSafeInstance().isFullscreen());
        choiceMisc.setSelectedIndex(choiceMisc.append("extended OSD", null), Config.getSafeInstance().isOsdExtended());
        choiceMisc.setSelectedIndex(choiceMisc.append("no sounds", null), Config.getSafeInstance().isNoSounds());
        append(choiceMisc);

        // show current provider and tracklog specific options
        showProviderOptions(false);

        // add command and handling
        addCommand(new Command("Cancel", Command.BACK, 1));
        addCommand(new Command("Apply", Command.SCREEN, 1));
        addCommand(new Command("Save", Command.SCREEN, 2));
        setCommandListener(this);
        setItemStateListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void itemStateChanged(Item item) {
        if (choiceProvider == item) {
            showProviderOptions(false);
        } else if (choiceTracklog == item) {
            showProviderOptions(true);
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        boolean changed = false;

        // restore desktop
        Desktop.display.setCurrent(Desktop.screen);

        if (command.getCommandType() == Command.SCREEN) { // "Apply", "Save"
            Config config = Config.getSafeInstance();
            // map path
            if (TrackingMIDlet.isFs()) {
                config.setMapPath(fieldMapPath.getString());
            }
            // provider
            if (choiceProvider.size() > 0) {
                config.setLocationProvider(choiceProvider.getString(choiceProvider.getSelectedIndex()));
            }
            // tracklog
            if (TrackingMIDlet.isFs()) {
                config.setTracklogsOn(choiceTracklog.isSelected(0));
                config.setTracklogsFormat(choiceTracklogsFormat.getString(choiceTracklogsFormat.getSelectedIndex()));
                config.setTracklogsDir(fieldTracklogsDir.getString());
            }
            // provider-specific
            if (TrackingMIDlet.isFs()) {
                config.setSimulatorDelay(Integer.parseInt(fieldSimulatorDelay.getString()));
            }
            // misc
            boolean[] misc = new boolean[choiceMisc.size()];
            choiceMisc.getSelectedFlags(misc);
            changed = config.isFullscreen() != misc[0];
            config.setFullscreen(misc[0]);
            config.setOsdExtended(misc[1]);
            config.setNoSounds(misc[2]);
            // timezone
            String tz = choiceTimezone.getString(choiceTimezone.getSelectedIndex());
            if ("GMT".equals(tz)) {
                config.setTimeZone(0);
            } else {
                if (tz.charAt(3) == '-') {
                    config.setTimeZone(Integer.parseInt(tz.substring(3)));
                } else {
                    config.setTimeZone(Integer.parseInt(tz.substring(4)));
                }
            }

            // save
            if ("Save".equals(command.getLabel())) {
                try {
                    // update config
                    config.update();

                    // show confirmation
                    Desktop.showConfirmation("Configuration saved.", Desktop.screen);

                } catch (ConfigurationException e) {
                    // show error
                    Desktop.showError("Failed to save configuration.", e, Desktop.screen);
                }
            }
        }

        // notify that we are done
        callback.invoke(changed ? Boolean.TRUE : Boolean.FALSE, null);
    }

    private void showProviderOptions(boolean soft) {
        if (choiceProvider.size() == 0) { // dumb phone
            return;
        }

        if (!TrackingMIDlet.isFs()) { // all options are fs-related anyway
            return;
        }

        for (int i = 0; i < size(); i++) {
            Item item = get(i);
            if (fieldMapPath == item || choiceProvider == item || choiceMisc == item || choiceTimezone == item)
                continue;
            if (soft && choiceTracklog == item)
                continue;

            delete(i);
            i = 0;
        }

        int insertAt = 0;
        for (int N = size(), i = 0; i < N; i++) {
            Item item = get(i);
            if (choiceMisc == item) {
                insertAt = i;
                break;
            }
        }

        String provider = choiceProvider.getString(choiceProvider.getSelectedIndex());
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(provider)) {
            insert(insertAt, fieldSimulatorDelay);
            if (choiceTracklog.isSelected(0)) {
                insert(insertAt, fieldTracklogsDir);
            }
            if (!soft) {
                insert(insertAt, choiceTracklog);
            }
        } else if (Config.LOCATION_PROVIDER_JSR82.equals(provider)) {
            if (choiceTracklog.isSelected(0)) {
                insert(insertAt, fieldTracklogsDir);
                insert(insertAt, choiceTracklogsFormat);
            }
            if (!soft) {
                insert(insertAt, choiceTracklog);
            }
        } else if (Config.LOCATION_PROVIDER_JSR179.equals(provider)) {
            if (choiceTracklog.isSelected(0)) {
                insert(insertAt, fieldTracklogsDir);
            }
            if (!soft) {
                insert(insertAt, choiceTracklog);
            }
        }
    }
}
