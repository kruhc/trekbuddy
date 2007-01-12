// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.event.Callback;

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
    private ChoiceGroup choiceMapDatum;
    private ChoiceGroup choiceCoordinates;
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
    private ChoiceGroup choicePerformance;

    public SettingsForm(Callback callback) {
        super("Settings");
        this.callback = callback;
    }

    public void show() {
        Config config = Config.getSafeInstance();

        // default map path field
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            fieldMapPath = new TextField("Startup Map", config.getMapPath(), MAX_URL_LENGTH, TextField.URL);
            append(fieldMapPath);
        }

        // map datum
        choiceMapDatum = new ChoiceGroup("Default Datum", ChoiceGroup.POPUP);
        for (int N = Config.DATUMS.length, i = 0; i < N; i++) {
            String id = Config.DATUMS[i].getName();
            choiceMapDatum.setSelectedIndex(choiceMapDatum.append(id, null), config.getGeoDatum().equals(id));
        }
        append(choiceMapDatum);

        // coordinates format
        choiceCoordinates = new ChoiceGroup("Coordinates", ChoiceGroup.POPUP);
        choiceCoordinates.append(Config.COORDS_MAP_LATLON, null);
        choiceCoordinates.append(Config.COORDS_MAP_GRID, null);
        choiceCoordinates.append(Config.COORDS_UTM, null);
        choiceCoordinates.append(Config.COORDS_GC_LATLON, null);
        choiceCoordinates.setSelectedFlags(new boolean[]{
            false,
            config.isUseGridFormat(),
            config.isUseUTM(),
            config.isUseGeocachingFormat()
        });
        append(choiceCoordinates);

        // desktop settings
        choiceMisc = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceMisc.append("fullscreen", null);
        choiceMisc.append("no sounds", null);
        choiceMisc.append("decimal precision", null);
        choiceMisc.append("HPS wpt true azimuth", null);
        choiceMisc.append("OSD basic", null);
        choiceMisc.append("OSD extended", null);
        choiceMisc.append("OSD no background", null);
        choiceMisc.append("OSD medium font", null);
        choiceMisc.append("OSD bold font", null);
        choiceMisc.append("OSD black color", null);
        choiceMisc.setSelectedFlags(new boolean[] {
            config.isFullscreen(),
            config.isNoSounds(),
            config.isDecimalPrecision(),
            config.isHpsWptTrueAzimuth(),
            config.isOsdBasic(),
            config.isOsdExtended(),
            config.isOsdNoBackground(),
            config.isOsdMediumFont(),
            config.isOsdBoldFont(),
            config.isOsdBlackColor()
        });
//        if (choiceProvider.size() == 0) { // dumb phone
            append(choiceMisc);
//        }

        // tweaks
        choicePerformance = new ChoiceGroup("Tweaks", ChoiceGroup.MULTIPLE);
        choicePerformance.append("optimistic I/O", null);
        choicePerformance.append("S60 renderer", null);
        choicePerformance.append("cache offline maps", null);
        choicePerformance.setSelectedFlags(new boolean[] {
            config.isOptimisticIo(),
            config.isS60renderer(),
            config.isCacheOffline()
        });
        append(choicePerformance);

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

        // tracklogs
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
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
            if (cz.kruch.track.TrackingMIDlet.isJsr135()) {
                fieldCaptureLocator = new TextField("Capture Locator", config.getCaptureLocator(), 16, TextField.URL);
                fieldCaptureFormat = new TextField("Capture Format", config.getCaptureFormat(), 64, TextField.ANY);
            }
        }

        // simulator
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            fieldSimulatorDelay = new TextField("Simulator Delay", Integer.toString(config.getSimulatorDelay()), 8, TextField.NUMERIC);
        }

        // internal
        if (cz.kruch.track.TrackingMIDlet.isJsr179()) {
            fieldLocationInterval = new TextField("Location Interval", Integer.toString(config.getLocationInterval()), 4, TextField.NUMERIC);
        }

        // 'Friends'
        choiceFriends = new ChoiceGroup("Location Sharing", ChoiceGroup.MULTIPLE);
        choiceFriends.append("receive", null);
        choiceFriends.setSelectedFlags(new boolean[] {
            config.isLocationSharing()
        });

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
            if (cz.kruch.track.TrackingMIDlet.isFs()) {
                config.setMapPath(fieldMapPath.getString());
            }
            // provider
            if (choiceProvider.size() > 0) {
                config.setLocationProvider(choiceProvider.getString(choiceProvider.getSelectedIndex()));
            }
            // tracklog
            if (cz.kruch.track.TrackingMIDlet.isFs()) {
                config.setTracklogsOn(choiceTracklog.getString(choiceTracklog.getSelectedIndex()));
                config.setTracklogsFormat(choiceTracklogsFormat.getString(choiceTracklogsFormat.getSelectedIndex()));
                config.setTracklogsDir(fieldTracklogsDir.getString());
                if (cz.kruch.track.TrackingMIDlet.isJsr135()) {
                    config.setCaptureLocator(fieldCaptureLocator.getString());
                    config.setCaptureFormat(fieldCaptureFormat.getString());
                }
            }
            // provider-specific
            if (cz.kruch.track.TrackingMIDlet.isFs()) {
                config.setSimulatorDelay(Integer.parseInt(fieldSimulatorDelay.getString()));
            }
            if (cz.kruch.track.TrackingMIDlet.isJsr179()) {
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
            config.setDecimalPrecision(misc[2]);
            config.setHpsWptTrueAzimuth(misc[3]);
            config.setOsdBasic(misc[4]);
            config.setOsdExtended(misc[5]);
            config.setOsdNoBackground(misc[6]);
            config.setOsdMediumFont(misc[7]);
            config.setOsdBoldFont(misc[8]);
            config.setOsdBlackColor(misc[9]);
            Desktop.resetFont();
            // datum
            config.setGeoDatum(Config.useDatum(choiceMapDatum.getString(choiceMapDatum.getSelectedIndex())));
            // coordinates format ('==' is ok for comparison)
            String fmt = choiceCoordinates.getString(choiceCoordinates.getSelectedIndex());
            config.setUseGridFormat(Config.COORDS_MAP_GRID == fmt);
            config.setUseUTM(Config.COORDS_UTM == fmt);
            config.setUseGeocachingFormat(Config.COORDS_GC_LATLON == fmt);
            // desktop
            boolean[] perf = new boolean[choicePerformance.size()];
            choicePerformance.getSelectedFlags(perf);
            config.setOptimisticIo(perf[0]);
            config.setS60renderer(perf[1]);
            config.setCacheOffline(perf[2]);

            // save
            if ("Save".equals(command.getLabel())) {
                try {
                    // update config
                    config.update(0);

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
            if (fieldMapPath == item || choiceProvider == item || choiceMisc == item || choicePerformance == item || choiceCoordinates == item || choiceMapDatum == item)
                continue;
            if (soft) {
                if (fieldSimulatorDelay == item || fieldLocationInterval == item || choiceFriends == item || choiceTracklog == item)
                    continue;
            }

            delete(i);

            // restart cycle
            i = size();
        }

        String provider = choiceProvider.getString(choiceProvider.getSelectedIndex());
        boolean isFs = cz.kruch.track.TrackingMIDlet.isFs();
        boolean tracklogsOn = isFs && !Config.TRACKLOG_NEVER.equals(choiceTracklog.getString(choiceTracklog.getSelectedIndex()));

        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(provider)) {
            if (isFs) {
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
            if (isFs) {
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
    }
}
