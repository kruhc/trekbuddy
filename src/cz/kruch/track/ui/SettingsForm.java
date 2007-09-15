/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

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
import javax.microedition.lcdui.Choice;

/**
 * Settings form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SettingsForm extends Form implements CommandListener, ItemStateListener {
    private static final int MAX_URL_LENGTH = 256;

    private Callback callback;

    private TextField fieldMapPath;
/*
    private ChoiceGroup choiceLanguage;
*/
    private ChoiceGroup choiceMapDatum;
    private ChoiceGroup choiceCoordinates;
    private ChoiceGroup choiceUnits;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceTracklog;
    private ChoiceGroup choiceTracklogFormat;
    private TextField fieldDataDir;
    private TextField fieldCaptureLocator;
    private TextField fieldCaptureFormat;
    private TextField fieldSimulatorDelay;
    private TextField fieldLocationTimings;
    private TextField fieldCommUrl;
    private ChoiceGroup choiceFriends;
    private ChoiceGroup choiceMisc;
    private ChoiceGroup choicePerformance;
    private TextField fieldWptProximity;
    private TextField fieldPoiProximity;
    private ChoiceGroup choiceRouteLine;
/*
    private Gauge gaugeScrollDelay;
*/
    private TextField fieldGpxDt;
    private TextField fieldGpxDs;

    private int menu;

    public SettingsForm(Callback callback) {
        super(cz.kruch.track.TrackingMIDlet.wm ? "Settings (TrekBuddy)" : "Settings");
        this.callback = callback;
    }

    public void show() {
        // default map path field
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            fieldMapPath = new TextField("Startup Map", Config.mapPath, MAX_URL_LENGTH, TextField.URL);
            append(fieldMapPath);
        }

        // language
/*
        choiceLanguage = new ChoiceGroup("Language", ChoiceGroup.POPUP);
        choiceLanguage.setFitPolicy(Choice.TEXT_WRAP_ON);
        for (int N = I18n.LANGUAGES.length, i = 0; i < N; i++) {
            choiceLanguage.setSelectedIndex(choiceLanguage.append(I18n.LANGUAGES[i], null), I18n.LANGUAGES[i].equals(Config.language));
        }
*/
/*
        append(choiceLanguage);
*/

        // map datum
        choiceMapDatum = new ChoiceGroup("Default Datum", ChoiceGroup.POPUP);
        choiceMapDatum.setFitPolicy(Choice.TEXT_WRAP_ON);
        for (int N = Config.DATUMS.length, i = 0; i < N; i++) {
            String id = Config.DATUMS[i].getName();
            choiceMapDatum.setSelectedIndex(choiceMapDatum.append(id, null), Config.geoDatum.equals(id));
        }
        append(choiceMapDatum);

        // coordinates format
        choiceCoordinates = new ChoiceGroup("Coordinates", ChoiceGroup.POPUP);
        choiceCoordinates.setFitPolicy(Choice.TEXT_WRAP_ON);
        choiceCoordinates.append(Config.COORDS_MAP_LATLON, null);
        choiceCoordinates.append(Config.COORDS_MAP_GRID, null);
        choiceCoordinates.append(Config.COORDS_UTM, null);
        choiceCoordinates.append(Config.COORDS_GC_LATLON, null);
        choiceCoordinates.setSelectedFlags(new boolean[]{
            false,
            Config.useGridFormat,
            Config.useUTM,
            Config.useGeocachingFormat
        });
        append(choiceCoordinates);

        // units format
        choiceUnits = new ChoiceGroup("Units", ChoiceGroup.POPUP);
        choiceUnits.setFitPolicy(Choice.TEXT_WRAP_ON);
        choiceUnits.append(Config.UNITS_METRIC, null);
        choiceUnits.append(Config.UNITS_IMPERIAL, null);
        choiceUnits.append(Config.UNITS_NAUTICAL, null);
        choiceUnits.setSelectedFlags(new boolean[]{
            false,
            Config.unitsImperial,
            Config.unitsNautical
        });
        append(choiceUnits);

        // desktop settings
        choiceMisc = new ChoiceGroup("Desktop", ChoiceGroup.MULTIPLE);
        choiceMisc.append("fullscreen", null);
        choiceMisc.append("no sounds", null);
        choiceMisc.append("decimal precision", null);
        choiceMisc.append("HPS wpt true azimuth", null);
        choiceMisc.append("OSD basic", null);
        choiceMisc.append("OSD extended", null);
        choiceMisc.append("OSD scale", null);
        choiceMisc.append("OSD no background", null);
        choiceMisc.append("OSD medium font", null);
        choiceMisc.append("OSD bold font", null);
        choiceMisc.append("OSD black color", null);
        choiceMisc.setSelectedFlags(new boolean[] {
            Config.fullscreen,
            Config.noSounds,
            Config.decimalPrecision,
            Config.hpsWptTrueAzimuth,
            Config.osdBasic,
            Config.osdExtended,
            Config.osdScale,
            Config.osdNoBackground,
            Config.osdMediumFont,
            Config.osdBoldFont,
            Config.osdBlackColor
        });
//        if (choiceProvider.size() == 0) { // ignore for dumb phones
            append(choiceMisc);
//        }

/*
        // scrolling speed
        gaugeScrollDelay = new Gauge("Scroll Delay", true, 10, Config.scrollingDelay);
        append(gaugeScrollDelay);
*/

        // navigation
        append(fieldWptProximity = new TextField("Wpt Proximity", Integer.toString(Config.wptProximity), 5, TextField.NUMERIC));
        /*append(*/fieldPoiProximity = new TextField("Poi Proximity", Integer.toString(Config.poiProximity), 5, TextField.NUMERIC)/*)*/;
        choiceRouteLine = new ChoiceGroup("Route Line", ChoiceGroup.MULTIPLE);
        choiceRouteLine.append("dotted", null);
        choiceRouteLine.append("red color", null);
        choiceRouteLine.append("POI icons", null);
        choiceRouteLine.setSelectedFlags(new boolean[] {
            Config.routeLineStyle,
            Config.routeLineColor != 0,
            Config.routePoiMarks
        });
        append(choiceRouteLine);

        // tweaks
        choicePerformance = new ChoiceGroup("Tweaks", ChoiceGroup.MULTIPLE);
        choicePerformance.append("optimistic I/O", null);
        choicePerformance.append("S60 renderer", null);
        choicePerformance.append("forced GC", null);
        choicePerformance.append("1-tile scroll", null);
        choicePerformance.setSelectedFlags(new boolean[] {
            Config.optimisticIo,
            Config.S60renderer,
            Config.forcedGc,
            Config.oneTileScroll
        });
        append(choicePerformance);

        // datadir
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            fieldDataDir = new TextField("Data Dir", Config.getDataDir(), MAX_URL_LENGTH, TextField.URL);
            append(fieldDataDir);
        }

        // 'Friends'
        if (cz.kruch.track.TrackingMIDlet.jsr120) {
            choiceFriends = new ChoiceGroup("Location Sharing", ChoiceGroup.MULTIPLE);
            choiceFriends.append("receive", null);
            choiceFriends.setSelectedFlags(new boolean[] {
                Config.locationSharing
            });
            append(choiceFriends);
        }

        // location provider choice
        String[] providers = Config.getLocationProviders();
        choiceProvider = new ChoiceGroup("Location Provider", ChoiceGroup.EXCLUSIVE);
        for (int N = providers.length, i = 0; i < N; i++) {
            String provider = providers[i];
            int idx = choiceProvider.append(provider, null);
            if (provider.equals(Config.locationProvider)) {
                choiceProvider.setSelectedIndex(idx, true);
            }
        }
        if (providers.length > 0) {
            append(choiceProvider);
        }

        // tracklogs, waypoints
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            choiceTracklog = new ChoiceGroup("Tracklog", ChoiceGroup.EXCLUSIVE);
            String tracklog = Config.tracklog;
            choiceTracklog.setSelectedIndex(choiceTracklog.append(Config.TRACKLOG_NEVER, null), Config.TRACKLOG_NEVER.equals(tracklog));
            choiceTracklog.setSelectedIndex(choiceTracklog.append(Config.TRACKLOG_ASK, null), Config.TRACKLOG_ASK.equals(tracklog));
            choiceTracklog.setSelectedIndex(choiceTracklog.append(Config.TRACKLOG_ALWAYS, null), Config.TRACKLOG_ALWAYS.equals(tracklog));

            choiceTracklogFormat = new ChoiceGroup("Tracklog Format", ChoiceGroup.EXCLUSIVE);
            String tracklogFormat = Config.tracklogFormat;
            choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_GPX, null), Config.TRACKLOG_FORMAT_GPX.equals(tracklogFormat));
            choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_NMEA, null), Config.TRACKLOG_FORMAT_NMEA.equals(tracklogFormat));

            fieldGpxDt = new TextField("GPX dt", Integer.toString(Config.gpxDt), 5, TextField.NUMERIC);
            fieldGpxDs = new TextField("GPX ds", Integer.toString(Config.gpxDs), 5, TextField.NUMERIC);

            if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                fieldCaptureLocator = new TextField("Capture Locator", Config.captureLocator, 16, TextField.URL);
                fieldCaptureFormat = new TextField("Capture Format", Config.captureFormat, 64, TextField.ANY);
            }
        }

        // serial
        if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
            fieldCommUrl = new TextField("Connection URL", Config.commUrl, 64, TextField.ANY);
        }
        
        // simulator
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            fieldSimulatorDelay = new TextField("Simulator Delay", Integer.toString(Config.simulatorDelay), 8, TextField.NUMERIC);
        }

        // internal
        if (cz.kruch.track.TrackingMIDlet.jsr179) {
            fieldLocationTimings = new TextField("Location Timings", Config.getLocationTimings(), 12, TextField.ANY);
        }

        // show current provider and tracklog specific options
        itemStateChanged(choiceProvider);

        // add command and handling
        addCommand(new Command("Cancel", Command.BACK, 1));
        addCommand(new Command("Apply", cz.kruch.track.TrackingMIDlet.wm ? Command.ITEM : Command.SCREEN, 1));
        addCommand(new Command("Save", cz.kruch.track.TrackingMIDlet.wm ? Command.ITEM : Command.SCREEN, 2));
        setCommandListener(this);
        setItemStateListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void itemStateChanged(Item affected) {
        if (choiceProvider.size() == 0) { // dumb phone
            return;
        }

        if (affected != choiceProvider && affected != choiceTracklog && affected != choiceTracklogFormat) {
            return;
        }

        for (int i = size(); --i >= 0; ) {
            Item item = get(i);

            if (fieldMapPath == item /*|| choiceLanguage == item*/ || choiceProvider == item || choiceMisc == item || choicePerformance == item || choiceCoordinates == item || choiceMapDatum == item || fieldDataDir == item || choiceFriends == item || choiceUnits == item || fieldWptProximity == item || fieldPoiProximity == item || choiceRouteLine == item /*|| gaugeScrollDelay == item*/)
                continue;

            if (choiceTracklogFormat == affected) {
                if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || choiceTracklog == item || choiceTracklogFormat == item)
                    continue;
            }

            if (choiceTracklog == affected) {
                if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || choiceTracklog == item)
                    continue;
            }

            delete(i);

            i = size();
        }

        String provider = choiceProvider.getString(choiceProvider.getSelectedIndex());
        boolean isFs = cz.kruch.track.TrackingMIDlet.isFs();
        boolean isTracklog = isFs && !Config.TRACKLOG_NEVER.equals(choiceTracklog.getString(choiceTracklog.getSelectedIndex()));
        boolean isTracklogGpx = isTracklog && Config.TRACKLOG_FORMAT_GPX.equals(choiceTracklogFormat.getString(choiceTracklogFormat.getSelectedIndex()));

        if (choiceProvider == affected) {
            if (Config.LOCATION_PROVIDER_JSR179.equals(provider) || Config.LOCATION_PROVIDER_MOTOROLA.equals(provider)) {
                append(fieldLocationTimings);
            } else if (Config.LOCATION_PROVIDER_SIMULATOR.equals(provider)) {
                append(fieldSimulatorDelay);
            } else if (Config.LOCATION_PROVIDER_SERIAL.equals(provider)) {
                append(fieldCommUrl);
            }
            if (isFs) {
                append(choiceTracklog);
                if (isTracklog) {
                    if (Config.LOCATION_PROVIDER_JSR82.equals(provider) || Config.LOCATION_PROVIDER_SERIAL.equals(provider) || Config.LOCATION_PROVIDER_JSR179.equals(provider)) {
                        append(choiceTracklogFormat);
                    }
                    if (isTracklogGpx) {
                        appendWithNewlineAfter(fieldGpxDt);
                        appendWithNewlineAfter(fieldGpxDs);
                        if (fieldCaptureLocator != null && fieldCaptureFormat != null) {
                            append(fieldCaptureLocator);
                            append(fieldCaptureFormat);
                        }
                    }
                }
            }
        }

        if (choiceTracklog == affected) {
            if (isTracklog) {
                if (Config.LOCATION_PROVIDER_JSR82.equals(provider) || Config.LOCATION_PROVIDER_SERIAL.equals(provider) || Config.LOCATION_PROVIDER_JSR179.equals(provider)) {
                    append(choiceTracklogFormat);
                }
                if (isTracklogGpx) {
                    appendWithNewlineAfter(fieldGpxDt);
                    appendWithNewlineAfter(fieldGpxDs);
                    if (fieldCaptureLocator != null && fieldCaptureFormat != null) {
                        append(fieldCaptureLocator);
                        append(fieldCaptureFormat);
                    }
                }
            }
        }

        if (choiceTracklogFormat == affected) {
            if (isTracklogGpx) {
                appendWithNewlineAfter(fieldGpxDt);
                appendWithNewlineAfter(fieldGpxDs);
                if (fieldCaptureLocator != null && fieldCaptureFormat != null) {
                    append(fieldCaptureLocator);
                    append(fieldCaptureFormat);
                }
            }
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        boolean changed = false;

        // restore desktop
        Desktop.display.setCurrent(Desktop.screen);

        if (command.getCommandType() != Command.BACK) { // "Apply", "Save"

            // map path
            if (cz.kruch.track.TrackingMIDlet.isFs()) {
                Config.mapPath = fieldMapPath.getString();
            }

/*
            // language
            Config.language = choiceLanguage.getString(choiceLanguage.getSelectedIndex());
*/

            // provider
            if (choiceProvider.size() > 0) {
                Config.locationProvider = choiceProvider.getString(choiceProvider.getSelectedIndex());
            }

            // tracklogs, waypoints
            if (cz.kruch.track.TrackingMIDlet.isFs()) {
                Config.tracklog = choiceTracklog.getString(choiceTracklog.getSelectedIndex());
                Config.tracklogFormat = choiceTracklogFormat.getString(choiceTracklogFormat.getSelectedIndex());
                Config.gpxDt = Integer.parseInt(fieldGpxDt.getString());
                Config.gpxDs = Integer.parseInt(fieldGpxDs.getString());
                Config.setDataDir(fieldDataDir.getString());
                if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                    Config.captureLocator = fieldCaptureLocator.getString();
                    Config.captureFormat = fieldCaptureFormat.getString();
                }
            }
            
            // navigation
            Config.wptProximity = Integer.parseInt(fieldWptProximity.getString());
            Config.poiProximity = Integer.parseInt(fieldPoiProximity.getString());
            boolean[] rl = new boolean[choiceRouteLine.size()];
            choiceRouteLine.getSelectedFlags(rl);
            Config.routeLineStyle = rl[0];
            Config.routeLineColor = rl[1] ? 0x00FF0000 : 0x0;
            Config.routePoiMarks = rl[2];

            // provider-specific
            if (cz.kruch.track.TrackingMIDlet.isFs()) {
                Config.simulatorDelay = Integer.parseInt(fieldSimulatorDelay.getString());
            }
            if (cz.kruch.track.TrackingMIDlet.jsr179) {
                Config.setLocationTimings(fieldLocationTimings.getString());
            }
            if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                Config.commUrl = fieldCommUrl.getString();
            }

            // location sharing
            if (cz.kruch.track.TrackingMIDlet.jsr120) {
                boolean[] friends = new boolean[choiceFriends.size()];
                choiceFriends.getSelectedFlags(friends);
                Config.locationSharing = friends[0];
            }

            // desktop
            changed = true;
            boolean[] misc = new boolean[choiceMisc.size()];
            choiceMisc.getSelectedFlags(misc);
            Config.fullscreen = misc[0];
            Config.noSounds = misc[1];
            Config.decimalPrecision = misc[2];
            Config.hpsWptTrueAzimuth = misc[3];
            Config.osdBasic = misc[4];
            Config.osdExtended = misc[5];
            Config.osdScale = misc[6];
            Config.osdNoBackground = misc[7];
            Config.osdMediumFont = misc[8];
            Config.osdBoldFont = misc[9];
            Config.osdBlackColor = misc[10];
            Desktop.resetFont();

            // datum
            Config.geoDatum = Config.useDatum(choiceMapDatum.getString(choiceMapDatum.getSelectedIndex()));

            // coordinates format
            String fmt = choiceCoordinates.getString(choiceCoordinates.getSelectedIndex());
            Config.useGridFormat = Config.COORDS_MAP_GRID.equals(fmt);
            Config.useUTM = Config.COORDS_UTM.equals(fmt);
            Config.useGeocachingFormat = Config.COORDS_GC_LATLON.equals(fmt);
             
            // units format
            fmt = choiceUnits.getString(choiceUnits.getSelectedIndex());
            Config.unitsImperial = Config.UNITS_IMPERIAL.equals(fmt);
            Config.unitsNautical = Config.UNITS_NAUTICAL.equals(fmt);

            // performance
            boolean[] perf = new boolean[choicePerformance.size()];
            choicePerformance.getSelectedFlags(perf);
            Config.optimisticIo = perf[0];
            Config.S60renderer = perf[1];
            Config.forcedGc = perf[2];
            Config.oneTileScroll = perf[3];

/*
            // scrolling
            Config.scrollingDelay = gaugeScrollDelay.getValue();
*/

            // save
            if ("Save".equals(command.getLabel())) {
                try {
                    // update config
                    Config.update(Config.CONFIG_090);

                    // show confirmation
                    Desktop.showConfirmation("Configuration saved.", Desktop.screen);

                } catch (ConfigurationException e) {
                    // show error
                    Desktop.showError("Failed to save configuration.", e, Desktop.screen);
                }
            }
        }

        // notify that we are done
        callback.invoke(changed ? Boolean.TRUE : Boolean.FALSE, null, this);
    }

    private int appendWithNewlineAfter(Item item) {
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_SHRINK | Item.LAYOUT_VSHRINK);
        return append(item);
    }
}
