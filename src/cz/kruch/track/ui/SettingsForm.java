// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.event.Callback;
import cz.kruch.track.TrackingMIDlet;
import cz.kruch.track.util.Datum;

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
    private ChoiceGroup choiceDatum;
    private ChoiceGroup choiceTimezone;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceTracklog;
    private ChoiceGroup choiceTracklogsFormat;
    private TextField fieldTracklogsDir;
    private TextField fieldCaptureLocator;
    private TextField fieldCaptureFormat;
    private TextField fieldSimulatorDelay;
    private TextField fieldLocationInterval;
    private ChoiceGroup choiceFriends;
    private ChoiceGroup choiceMisc;
/*
    private TextField dX, dY, dZ;
*/

    public SettingsForm(Callback callback) {
        super("Settings");
        this.callback = callback;
    }

    public void show() {
        Config config = Config.getSafeInstance();

        // default map path field
        if (TrackingMIDlet.isFs()) {
            fieldMapPath = new TextField("Default Map", config.getMapPath(), MAX_URL_LENGTH, TextField.URL);
            append(fieldMapPath);
        }

        // geodetic datum
        choiceDatum = new ChoiceGroup("Map Datum", ChoiceGroup.POPUP);
        for (int N = Datum.DATUMS.length, i = 0; i < N; i++) {
            String id = Datum.DATUMS[i].toString();
            choiceDatum.setSelectedIndex(choiceDatum.append(id, null), config.getGeoDatum().equals(id));
        }
        append(choiceDatum);
/*
        dX = new TextField("dX", null, 5, TextField.DECIMAL);
        dX.setString(Integer.toString(config.getdX()));
        dX.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_2);
        append(dX);
        dY = new TextField("dY", null, 5, TextField.DECIMAL);
        dY.setString(Integer.toString(config.getdY()));
        dY.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_2);
        append(dY);
        dZ = new TextField("dZ", null, 5, TextField.DECIMAL);
        dZ.setString(Integer.toString(config.getdZ()));
        dZ.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_2);
        append(dZ);
*/

        // location provider choice
        String[] providers = config.getLocationProviders();
        choiceProvider = new ChoiceGroup("Location Provider", ChoiceGroup.EXCLUSIVE);
        for (int N = providers.length, i = 0; i < N; i++) {
            String provider = providers[i];
            int idx = choiceProvider.append(provider, null);
            if (provider.equals(config.getLocationProvider())) {
                choiceProvider.setSelectedIndex(idx, true);
            }
        }
        if (providers.length > 0) {
            append(choiceProvider);
        }

        // timezone
        choiceTimezone = new ChoiceGroup("Timezone", ChoiceGroup.POPUP);
        for (int i = -12; i <= 13; i++) {
            String tz;
            if (i == 0) {
                tz = "GMT";
            } else if (i > 0) {
                tz = "GMT+" + (i < 10 ? "0" : "") + Integer.toString(i) + ":00";
            } else {
                tz = "GMT-" + (i > -10 ? "0" : "") + Integer.toString(Math.abs(i)) + ":00";
            }

            choiceTimezone.setSelectedIndex(choiceTimezone.append(tz, null),
                                            tz.equals(config.getTimeZone()));

            // extra zones
            if (i == 3) {
                tz = "GMT+03:30";
            } else if (i == 4) {
                tz = "GMT+04:30";
            } else if (i == 5) {
                tz = "GMT+05:30";
                choiceTimezone.setSelectedIndex(choiceTimezone.append(tz, null),
                                                tz.equals(config.getTimeZone()));
                tz = "GMT+05:45";
            } else if (i == 6) {
                tz = "GMT+06:30";
            } else if (i == 9) {
                tz = "GMT+09:30";
            } else if (i == -3) {
                tz = "GMT-03:30";
            } else {
                tz = null;
            }

            if (tz != null) {
                choiceTimezone.setSelectedIndex(choiceTimezone.append(tz, null),
                                                tz.equals(config.getTimeZone()));
            }
        }
        append(choiceTimezone);

        // tracklogs
        if (TrackingMIDlet.isFs()) {
            choiceTracklog = new ChoiceGroup("Tracklog", ChoiceGroup.MULTIPLE);
            choiceTracklog.setSelectedIndex(choiceTracklog.append("enabled", null), config.isTracklogsOn());
            choiceTracklogsFormat = new ChoiceGroup("Tracklog Format", ChoiceGroup.EXCLUSIVE);
            for (int N = Config.TRACKLOGS_FORMAT.length, i = 0; i < N; i++) {
                String format = Config.TRACKLOGS_FORMAT[i];
                int idx = choiceTracklogsFormat.append(format, null);
                if (format.equals(config.getTracklogsFormat())) {
                    choiceTracklogsFormat.setSelectedIndex(idx, true);
                }
            }
            fieldTracklogsDir = new TextField("Tracklogs Dir", config.getTracklogsDir(), MAX_URL_LENGTH, TextField.URL);
            if (TrackingMIDlet.isJsr135()) {
                fieldCaptureLocator = new TextField("Capture Locator", config.getCaptureLocator(), 16, TextField.URL);
                fieldCaptureFormat = new TextField("Capture Format", config.getCaptureFormat(), 64, TextField.ANY);
            }
        }

        // simulator
        if (TrackingMIDlet.isFs()) {
            fieldSimulatorDelay = new TextField("Simulator Delay", Integer.toString(config.getSimulatorDelay()), 8, TextField.NUMERIC);
        }

        // internal
        if (TrackingMIDlet.isJsr179()) {
            fieldLocationInterval = new TextField("Location Interval", Integer.toString(config.getLocationInterval()), 4, TextField.NUMERIC);
        }

        // 'Friends'
        choiceFriends = new ChoiceGroup("Location Sharing", ChoiceGroup.MULTIPLE);
        choiceFriends.setSelectedIndex(choiceFriends.append("receive", null), config.isLocationSharing());

        // desktop settings
        choiceMisc = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceMisc.setSelectedIndex(choiceMisc.append("fullscreen", null), config.isFullscreen());
        choiceMisc.setSelectedIndex(choiceMisc.append("no sounds", null), config.isNoSounds());
        choiceMisc.setSelectedIndex(choiceMisc.append("UTM coordinates", null), config.isUseUTM());
        choiceMisc.setSelectedIndex(choiceMisc.append("OSD extended", null), config.isOsdExtended());
        choiceMisc.setSelectedIndex(choiceMisc.append("OSD no background", null), config.isOsdNoBackground());
        choiceMisc.setSelectedIndex(choiceMisc.append("OSD larger font", null), config.isOsdMediumFont());
        choiceMisc.setSelectedIndex(choiceMisc.append("OSD bold font", null), config.isOsdBoldFont());
        choiceMisc.setSelectedIndex(choiceMisc.append("OSD black color", null), config.isOsdBlackColor());
        append(choiceMisc);

        // show current provider and tracklog specific options
        showProviderOptions(false);
/*
        showDatumOptions();
*/

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
        }/* else if (choiceDatum == item) {
            showDatumOptions();
        }*/
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
                if (TrackingMIDlet.isJsr135()) {
                    config.setCaptureLocator(fieldCaptureLocator.getString());
                    config.setCaptureFormat(fieldCaptureFormat.getString());
                }
            }
            // provider-specific
            if (TrackingMIDlet.isFs()) {
                config.setSimulatorDelay(Integer.parseInt(fieldSimulatorDelay.getString()));
            }
            if (TrackingMIDlet.isJsr179()) {
                config.setLocationInterval(Integer.parseInt(fieldLocationInterval.getString()));
            }
            // 'Friends'
            boolean[] misc = new boolean[choiceFriends.size()];
            choiceFriends.getSelectedFlags(misc);
            config.setLocationSharing(misc[0]);
            // desktop
            changed = true;
            misc = new boolean[choiceMisc.size()];
            choiceMisc.getSelectedFlags(misc);
            config.setFullscreen(misc[0]);
            config.setNoSounds(misc[1]);
            config.setUseUTM(misc[2]);
            config.setOsdExtended(misc[3]);
            config.setOsdNoBackground(misc[4]);
            config.setOsdMediumFont(misc[5]);
            config.setOsdBoldFont(misc[6]);
            config.setOsdBlackColor(misc[7]);
            Desktop.resetFont();
            // timezone
            config.setTimeZone(choiceTimezone.getString(choiceTimezone.getSelectedIndex()));
            // datum
            config.setGeoDatum(Datum.use(choiceDatum.getString(choiceDatum.getSelectedIndex())));
/*
            config.setdX(Integer.parseInt(dX.getString()));
            config.setdY(Integer.parseInt(dY.getString()));
            config.setdZ(Integer.parseInt(dZ.getString()));
*/

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

        for (int N = size(), i = 0; i < N; i++) {
            Item item = get(i);
            if (fieldMapPath == item || choiceProvider == item || /*choiceMisc == item || */choiceTimezone == item || choiceDatum == item/*|| (dX == item || dY == item || dZ == item)*/)
                continue;
            if (soft) {
                if (fieldSimulatorDelay == item || fieldLocationInterval == item || choiceFriends == item || choiceTracklog == item)
                    continue;
            }

            delete(i);

            // restart cycle
            i = -1;
            N = size();
        }

/*
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
            if (TrackingMIDlet.isFs()) {
                if (choiceTracklog.isSelected(0)) {
                    insert(insertAt, fieldTracklogsDir);
                }
                insert(insertAt, choiceTracklog);
                if (!soft) {
                    insert(insertAt, fieldSimulatorDelay);
                }
            }
        } else if (Config.LOCATION_PROVIDER_JSR82.equals(provider)) {
            if (TrackingMIDlet.isFs()) {
                if (choiceTracklog.isSelected(0)) {
                    if (fieldCaptureLocator != null && fieldCaptureFormat != null) {
                        insert(insertAt, fieldCaptureFormat);
                        insert(insertAt, fieldCaptureLocator);
                    }
                    insert(insertAt, fieldTracklogsDir);
                    insert(insertAt, choiceTracklogsFormat);
                }
                insert(insertAt, choiceTracklog);
            }
            if (!soft) {
                insert(insertAt, choiceFriends);
            }
        } else if (Config.LOCATION_PROVIDER_JSR179.equals(provider)) {
            if (TrackingMIDlet.isFs()) {
                if (choiceTracklog.isSelected(0)) {
                    if (fieldCaptureLocator != null && fieldCaptureFormat != null) {
                        insert(insertAt, fieldCaptureFormat);
                        insert(insertAt, fieldCaptureLocator);
                    }
                    insert(insertAt, fieldTracklogsDir);
                }
                insert(insertAt, choiceTracklog);
            }
            if (!soft) {
                insert(insertAt, fieldLocationInterval);
                insert(insertAt, choiceFriends);
            }
        }
*/
        String provider = choiceProvider.getString(choiceProvider.getSelectedIndex());
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(provider)) {
            if (TrackingMIDlet.isFs()) {
                if (!soft) {
                    append(fieldSimulatorDelay);
                    append(choiceTracklog);
                }
                if (choiceTracklog.isSelected(0)) {
                    append(fieldTracklogsDir);
                }
            }
        } else {
            if (!soft) {
                if (Config.LOCATION_PROVIDER_JSR179.equals(provider)) {
                    append(fieldLocationInterval);
                }
                append(choiceFriends);
            }
            if (TrackingMIDlet.isFs()) {
                if (!soft) {
                    append(choiceTracklog);
                }
                if (choiceTracklog.isSelected(0)) {
                    append(choiceTracklogsFormat);
                    append(fieldTracklogsDir);
                    if (fieldCaptureLocator != null && fieldCaptureFormat != null) {
                        append(fieldCaptureLocator);
                        append(fieldCaptureFormat);
                    }
                }
            }
        }
        append(choiceMisc);
    }

/*
    private void showDatumOptions() {
        String id = choiceDatum.getString(choiceDatum.getSelectedIndex());
        if (id.startsWith("--")) {
            // fake
        } else if (id.indexOf('-') > -1) {
            dX.setConstraints(dX.getConstraints() | TextField.UNEDITABLE);
            dY.setConstraints(dY.getConstraints() | TextField.UNEDITABLE);
            dZ.setConstraints(dZ.getConstraints() | TextField.UNEDITABLE);
        } else {
            dX.setConstraints(dX.getConstraints() & ~TextField.UNEDITABLE);
            dY.setConstraints(dY.getConstraints() & ~TextField.UNEDITABLE);
            dZ.setConstraints(dZ.getConstraints() & ~TextField.UNEDITABLE);
        }
    }
*/
}
