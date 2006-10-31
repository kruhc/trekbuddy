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
/*
    private ChoiceGroup choiceTimezone;
*/
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

/*
        // timezone
        choiceTimezone = new ChoiceGroup("Timezone", ChoiceGroup.POPUP);
        for (int N = Config.TZ.length, i = 0; i < N; i++) {
            String tz = (String) ((Object[]) Config.TZ[i])[0];
            int index = choiceTimezone.append(tz, null);
            if (tz.equals(config.getTimeZone())) {
                choiceTimezone.setSelectedIndex(index, true);
            }
        }
        append(choiceTimezone);
*/

        // tracklogs
        if (TrackingMIDlet.isFs()) {
            choiceTracklog = new ChoiceGroup("Tracklog", ChoiceGroup.EXCLUSIVE);
            String tracklogsOn = config.getTracklogsOn();
            choiceTracklog.setSelectedIndex(choiceTracklog.append(Config.TRACKLOG_NEVER, null), Config.TRACKLOG_NEVER.equals(tracklogsOn));
            choiceTracklog.setSelectedIndex(choiceTracklog.append(Config.TRACKLOG_ASK, null), Config.TRACKLOG_ASK.equals(tracklogsOn));
            choiceTracklog.setSelectedIndex(choiceTracklog.append(Config.TRACKLOG_ALWAYS, null), Config.TRACKLOG_ALWAYS.equals(tracklogsOn));
            choiceTracklogsFormat = new ChoiceGroup("Tracklog Format", ChoiceGroup.EXCLUSIVE);
            String tracklogsFormat = config.getTracklogsFormat();
            choiceTracklogsFormat.setSelectedIndex(choiceTracklogsFormat.append(Config.TRACKLOG_FORMAT_GPX, null), Config.TRACKLOG_FORMAT_GPX.equals(tracklogsFormat));
            choiceTracklogsFormat.setSelectedIndex(choiceTracklogsFormat.append(Config.TRACKLOG_FORMAT_NMEA, null), Config.TRACKLOG_FORMAT_NMEA.equals(tracklogsFormat));
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
        choiceFriends.append("receive", null);
        choiceFriends.setSelectedFlags(new boolean[] {
            config.isLocationSharing()
        });

        // desktop settings
        choiceMisc = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceMisc.append("fullscreen", null);
        choiceMisc.append("no sounds", null);
        choiceMisc.append("geocaching format", null);
        choiceMisc.append("UTM coordinates", null);
        choiceMisc.append("OSD extended", null);
        choiceMisc.append("OSD no background", null);
        choiceMisc.append("OSD larger font", null);
        choiceMisc.append("OSD bold font", null);
        choiceMisc.append("OSD black color", null);
        choiceMisc.setSelectedFlags(new boolean[] {
            config.isFullscreen(),
            config.isNoSounds(),
            config.isUseGeocachingFormat(),
            config.isUseUTM(),
            config.isOsdExtended(),
            config.isOsdNoBackground(),
            config.isOsdMediumFont(),
            config.isOsdBoldFont(),
            config.isOsdBlackColor()
        });
        if (choiceProvider.size() == 0) { // dumb phone
            append(choiceMisc);
        }

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
                config.setTracklogsOn(choiceTracklog.getString(choiceTracklog.getSelectedIndex()));
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
            // location sharing
            boolean[] friends = new boolean[choiceFriends.size()];
            choiceFriends.getSelectedFlags(friends);
            config.setLocationSharing(friends[0]);
            // desktop
            changed = true;
            boolean[] misc = new boolean[choiceMisc.size()];
            choiceMisc.getSelectedFlags(misc);
            config.setFullscreen(misc[0]);
            config.setNoSounds(misc[1]);
            config.setUseGeocachingFormat(misc[2]);
            config.setUseUTM(misc[3]);
            config.setOsdExtended(misc[4]);
            config.setOsdNoBackground(misc[5]);
            config.setOsdMediumFont(misc[6]);
            config.setOsdBoldFont(misc[7]);
            config.setOsdBlackColor(misc[8]);
            Desktop.resetFont();
/*
            // timezone
            config.setTimeZone(choiceTimezone.getString(choiceTimezone.getSelectedIndex()));
*/
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

        for (int i = size(); --i >= 0; ) {
            Item item = get(i);
            if (fieldMapPath == item || choiceProvider == item || /*choiceMisc == item || *//*choiceTimezone == item || */choiceDatum == item/*|| (dX == item || dY == item || dZ == item)*/)
                continue;
            if (soft) {
                if (fieldSimulatorDelay == item || fieldLocationInterval == item || choiceFriends == item || choiceTracklog == item)
                    continue;
            }

            System.out.println("Deleting " + item);
            delete(i);

            // restart cycle
            i = size();
        }

        String provider = choiceProvider.getString(choiceProvider.getSelectedIndex());
        boolean tracklogsOn = !Config.TRACKLOG_NEVER.equals(choiceTracklog.getString(choiceTracklog.getSelectedIndex()));
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(provider)) {
            if (TrackingMIDlet.isFs()) {
                if (!soft) {
                    append(fieldSimulatorDelay);
                    append(choiceTracklog);
                }
                if (tracklogsOn) {
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
                if (tracklogsOn) {
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
