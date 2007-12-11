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
import cz.kruch.track.Resources;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Gauge;

import api.file.File;

/**
 * Settings form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SettingsForm extends List implements CommandListener, ItemStateListener {
    private static final int MAX_URL_LENGTH = 256;

    private static final String MENU_BASIC;
    private static final String MENU_DESKTOP;
    private static final String MENU_LOCATION;
    private static final String MENU_NAVIGATION;
    private static final String MENU_MISC;

    private final Callback callback;

    private TextField fieldMapPath;
    private ChoiceGroup choiceMapDatum;
    private ChoiceGroup choiceCoordinates;
    private ChoiceGroup choiceUnits;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceTracklog;
    private ChoiceGroup choiceTracklogFormat;
    private TextField fieldDataDir;
    private TextField fieldCaptureLocator;
    private TextField fieldSnapshotFormat;
    private ChoiceGroup choiceSnapshotFormat;
    private TextField fieldSimulatorDelay;
    private TextField fieldLocationTimings;
    private TextField fieldCommUrl;
    private TextField fieldO2Depth;
    private ChoiceGroup choiceFriends;
    private ChoiceGroup choiceMisc;
    private ChoiceGroup choicePerformance;
    private TextField fieldWptProximity;
    private TextField fieldPoiProximity;
    private ChoiceGroup choiceRouteLine;
    private ChoiceGroup choiceGpx;
    private TextField fieldGpxDt;
    private TextField fieldGpxDs;

    private Form submenu;

    private boolean changed;

    static {
        MENU_BASIC = Resources.getString(Resources.CFG_ITEM_BASIC);
        MENU_DESKTOP = Resources.getString(Resources.CFG_ITEM_DESKTOP);
        MENU_LOCATION = Resources.getString(Resources.CFG_ITEM_LOCATION);
        MENU_NAVIGATION = Resources.getString(Resources.CFG_ITEM_NAVIGATION);
        MENU_MISC = Resources.getString(Resources.CFG_ITEM_MISC);
    }

    public SettingsForm(Callback callback) {
        super(Resources.prefixed(Resources.getString(Resources.DESKTOP_CMD_SETTINGS)), List.IMPLICIT);
        this.callback = callback;
    }

    public void show() {
        // top-level menu
        append(MENU_BASIC, null);
        append(MENU_DESKTOP, null);
        append(MENU_LOCATION, null);
        append(MENU_NAVIGATION, null);
        append(MENU_MISC, null);

        // add command and handling
        addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        addCommand(new Command(Resources.getString(Resources.CFG_CMD_APPLY), Command.ITEM, 1));
        addCommand(new Command(Resources.getString(Resources.CFG_CMD_SAVE), Command.ITEM, 2));
        /* default SELECT command is of SCREEN type */
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    private void show(String section) {
        // submenu form
        submenu = null; // gc hint
        submenu = new Form(Resources.prefixed(section));

        if (MENU_BASIC.equals(section)) {

            // default map path field
            if (File.isFs()) {
                submenu.append(fieldMapPath = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_START_MAP), Config.mapPath, MAX_URL_LENGTH, TextField.URL));
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
            choiceMapDatum = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_DEFAULT_DATUM), ChoiceGroup.POPUP);
            choiceMapDatum.setFitPolicy(Choice.TEXT_WRAP_ON);
            for (int N = Config.DATUMS.length, i = 0; i < N; i++) {
                String id = Config.DATUMS[i].name;
                choiceMapDatum.setSelectedIndex(choiceMapDatum.append(id, null), Config.geoDatum.equals(id));
            }
            submenu.append(choiceMapDatum);

            // coordinates format
            choiceCoordinates = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_COORDS_FMT), ChoiceGroup.POPUP);
            choiceCoordinates.setFitPolicy(Choice.TEXT_WRAP_ON);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPLL), null);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPGRID), null);
            choiceCoordinates.append("UTM", null);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_GCLL), null);
            choiceCoordinates.setSelectedFlags(new boolean[]{
                false,
                Config.useGridFormat,
                Config.useUTM,
                Config.useGeocachingFormat
            });
            submenu.append(choiceCoordinates);

            // units format
            choiceUnits = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_UNITS), ChoiceGroup.POPUP);
            choiceUnits.setFitPolicy(Choice.TEXT_WRAP_ON);
            choiceUnits.append(Resources.getString(Resources.CFG_BASIC_FLD_UNITS_METRIC), null);
            choiceUnits.append(Resources.getString(Resources.CFG_BASIC_FLD_UNITS_IMPERIAL), null);
            choiceUnits.append(Resources.getString(Resources.CFG_BASIC_FLD_UNITS_NAUTICAL), null);
            choiceUnits.setSelectedIndex(Config.units, true);
            submenu.append(choiceUnits);

            // datadir
            if (File.isFs()) {
                submenu.append(fieldDataDir = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_DATA_DIR), Config.getDataDir(), MAX_URL_LENGTH, TextField.URL));
            }

        } else if (MENU_DESKTOP.equals(section)) {

            // desktop settings
            choiceMisc = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP), ChoiceGroup.MULTIPLE);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_FULLSCREEN), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_SOUNDS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_DEC_PRECISION), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HPS_WPT_TRUE_AZI), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BASIC), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_EXT), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_SCALE), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_NO_BG), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_MED_FONT), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BOLD_FONT), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BLACK), null);
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
//            if (choiceProvider.size() == 0) { // ignore for dumb phones
                submenu.append(choiceMisc);
//            }

        } else if (MENU_NAVIGATION.equals(section)) {

            // navigation
            submenu.append(fieldWptProximity = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_WPT_PROXIMITY), Integer.toString(Config.wptProximity), 5, TextField.NUMERIC));
            /*append(*/fieldPoiProximity = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_PROXIMITY), Integer.toString(Config.poiProximity), 5, TextField.NUMERIC)/*)*/;
            choiceRouteLine = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_ROUTE_LINE), ChoiceGroup.MULTIPLE);
            choiceRouteLine.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_DOTTED), null);
            choiceRouteLine.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_RED), null);
            choiceRouteLine.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_ICONS), null);
            choiceRouteLine.setSelectedFlags(new boolean[] {
                Config.routeLineStyle,
                Config.routeLineColor != 0,
                Config.routePoiMarks
            });
            submenu.append(choiceRouteLine);

            // 'Friends'
            if (cz.kruch.track.TrackingMIDlet.jsr120) {
                choiceFriends = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_SMS), ChoiceGroup.MULTIPLE);
                choiceFriends.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_RECEIVE), null);
                choiceFriends.setSelectedFlags(new boolean[] {
                    Config.locationSharing
                });
                submenu.append(choiceFriends);
            }
            
        } else if (MENU_MISC.equals(section)) {

            // tweaks
            choicePerformance = new ChoiceGroup(Resources.getString(Resources.CFG_TWEAKS_GROUP), ChoiceGroup.MULTIPLE);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SIEMENS_IO), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SAFE_RENDERER), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_FORCED_GC), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_1TILE_SCROLL), null);
            choicePerformance.setSelectedFlags(new boolean[] {
                Config.siemensIo,
                Config.S60renderer,
                Config.forcedGc,
                Config.oneTileScroll
            });
            submenu.append(choicePerformance);

        } else if (MENU_LOCATION.equals(section)) {

            // location provider choice
            short[] providers = Config.getLocationProviders();
            choiceProvider = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_PROVIDER), ChoiceGroup.EXCLUSIVE);
            for (int N = providers.length, i = 0; i < N; i++) {
                short resourceId = Resources.CFG_LOCATION_FLD_PROV_BLUETOOTH;
                switch (providers[i]) {
                    case Config.LOCATION_PROVIDER_JSR179:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_INTERNAL;
                    break;
                    case Config.LOCATION_PROVIDER_SERIAL:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_SERIAL;
                    break;
                    case Config.LOCATION_PROVIDER_SIMULATOR:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_SIMULATOR;
                    break;
                    case Config.LOCATION_PROVIDER_MOTOROLA:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_MOTOROLA;
                    break;
                    case Config.LOCATION_PROVIDER_O2GERMANY:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_O2GERMANY;
                    break;
                }
                choiceProvider.setSelectedIndex(choiceProvider.append(Resources.getString(resourceId), null), Config.locationProvider == providers[i]);
            }
            if (providers.length > 0) {
                submenu.append(choiceProvider);
            }

            // tracklogs, waypoints
            if (File.isFs()) {
                choiceTracklog = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_TRACKLOG), ChoiceGroup.EXCLUSIVE);
                choiceTracklog.setSelectedIndex(choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null), Config.TRACKLOG_NEVER == Config.tracklog);
                choiceTracklog.setSelectedIndex(choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ASK), null), Config.TRACKLOG_ASK == Config.tracklog);
                choiceTracklog.setSelectedIndex(choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null), Config.TRACKLOG_ALWAYS == Config.tracklog);

                choiceTracklogFormat = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_TRACKLOG_FMT), ChoiceGroup.EXCLUSIVE);
                String tracklogFormat = Config.tracklogFormat;
                choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_GPX, null), Config.TRACKLOG_FORMAT_GPX.equals(tracklogFormat));
                choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_NMEA, null), Config.TRACKLOG_FORMAT_NMEA.equals(tracklogFormat));

                choiceGpx = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_GPX_OPTS), ChoiceGroup.MULTIPLE);
                choiceGpx.append(Resources.getString(Resources.CFG_LOCATION_FLD_GPX_LOG_VALID), null);
                choiceGpx.setSelectedFlags(new boolean[] {
                    Config.gpxOnlyValid
                });

                fieldGpxDt = new TextField("GPX dt", Integer.toString(Config.gpxDt), 5, TextField.NUMERIC);
                fieldGpxDs = new TextField("GPX ds", Integer.toString(Config.gpxDs), 5, TextField.NUMERIC);

                if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                    fieldCaptureLocator = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CAPTURE_LOCATOR), Config.captureLocator, 16, TextField.URL);
                    choiceSnapshotFormat = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_FLD_CAPTURE_FMT), ChoiceGroup.POPUP);
                    String encodings = System.getProperty("video.snapshot.encodings");
                    int start = encodings.indexOf("encoding=");
                    while (start > -1) {
                        int end = encodings.indexOf("encoding=", start + 9);
                        String item;
                        if (end > -1) {
                            item = encodings.substring(start, end).trim();
                        } else {
                            item = encodings.substring(start).trim();
                        }
                        choiceSnapshotFormat.setSelectedIndex(choiceSnapshotFormat.append(item, null), Config.snapshotFormat.equals(item));
                        start = end;
                    }
                    fieldSnapshotFormat = new TextField(null, Config.snapshotFormat, 64, TextField.ANY);
                    fieldSnapshotFormat.setString(Config.snapshotFormat);
                }
            }

            // serial
            if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                fieldCommUrl = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CONN_URL), Config.commUrl, 64, TextField.ANY);
            }

            // simulator
            if (File.isFs()) {
                fieldSimulatorDelay = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_SIMULATOR_DELAY), Integer.toString(Config.simulatorDelay), 8, TextField.NUMERIC);
            }

            // internal
            if (cz.kruch.track.TrackingMIDlet.jsr179) {
                fieldLocationTimings = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_LOCATION_TIMINGS), Config.getLocationTimings(), 12, TextField.ANY);
            }

            // O2 Germany
            if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                fieldO2Depth = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_FILTER_DEPTH), Integer.toString(Config.o2Depth), 2, TextField.NUMERIC);
            }

            // show current provider and tracklog specific options
            itemStateChanged(choiceProvider);
            submenu.setItemStateListener(this);
        }

        // add command and handling
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_OK), Desktop.POSITIVE_CMD_TYPE, 1));
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        submenu.setCommandListener(this);

        // show
        Desktop.display.setCurrent(submenu);
    }

    public void itemStateChanged(Item affected) {
        if (choiceProvider.size() == 0) { // dumb phone
            return;
        }

        if (affected == choiceProvider || affected == choiceTracklog || affected == choiceTracklogFormat) {

            for (int i = submenu.size(); --i >= 0; ) {
                Item item = submenu.get(i);

                if (choiceProvider == item)
                    continue;

                if (choiceTracklogFormat == affected) {
                    if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || fieldO2Depth == item || choiceTracklog == item || choiceTracklogFormat == item)
                        continue;
                }

                if (choiceTracklog == affected) {
                    if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || fieldO2Depth == item || choiceTracklog == item)
                        continue;
                }

                submenu.delete(i);
                i = submenu.size();
            }

            final int provider = Config.getLocationProviders()[choiceProvider.getSelectedIndex()];
            final boolean isFs = File.isFs();
            final boolean isTracklog = isFs && choiceTracklog.getSelectedIndex() > Config.TRACKLOG_NEVER;
            final boolean isTracklogGpx = isTracklog && Config.TRACKLOG_FORMAT_GPX.equals(choiceTracklogFormat.getString(choiceTracklogFormat.getSelectedIndex()));

            if (choiceProvider == affected) {
                switch (provider) {
                    case Config.LOCATION_PROVIDER_JSR179:
                    case Config.LOCATION_PROVIDER_MOTOROLA:
                        appendWithNewlineAfter(submenu, fieldLocationTimings);
                    break;
                    case Config.LOCATION_PROVIDER_SIMULATOR:
                        appendWithNewlineAfter(submenu, fieldSimulatorDelay);
                    break;
                    case Config.LOCATION_PROVIDER_SERIAL:
                        appendWithNewlineAfter(submenu, fieldCommUrl);
                    break;
                    case Config.LOCATION_PROVIDER_O2GERMANY:
                        appendWithNewlineAfter(submenu, fieldO2Depth);
                    break;
                }
                if (isFs) {
                    appendWithNewlineAfter(submenu, choiceTracklog);
                    if (isTracklog) {
                        if (Config.LOCATION_PROVIDER_JSR82 == provider || Config.LOCATION_PROVIDER_SERIAL == provider || Config.LOCATION_PROVIDER_JSR179 == provider) {
                            appendWithNewlineAfter(submenu, choiceTracklogFormat);
                        }
                        if (isTracklogGpx) {
                            appendWithNewlineAfter(submenu, choiceGpx);
                            appendShrinked(submenu, fieldGpxDt);
                            appendWithNewlineAfter(submenu, fieldGpxDs);
                        }
                    }
                }
            }

            if (choiceTracklog == affected) {
                if (isTracklog) {
                    if (Config.LOCATION_PROVIDER_JSR82 == provider || Config.LOCATION_PROVIDER_SERIAL == provider || Config.LOCATION_PROVIDER_JSR179 == provider) {
                        appendWithNewlineAfter(submenu, choiceTracklogFormat);
                    }
                    if (isTracklogGpx) {
                        appendWithNewlineAfter(submenu, choiceGpx);
                        appendShrinked(submenu, fieldGpxDt);
                        appendWithNewlineAfter(submenu, fieldGpxDs);
                    }
                }
            }

            if (choiceTracklogFormat == affected) {
                if (isTracklogGpx) {
                    appendWithNewlineAfter(submenu, choiceGpx);
                    appendShrinked(submenu, fieldGpxDt);
                    appendWithNewlineAfter(submenu, fieldGpxDs);
                }
            }

            if (fieldCaptureLocator != null) {
                appendWithNewlineAfter(submenu, fieldCaptureLocator);
                appendWithNewlineAfter(submenu, choiceSnapshotFormat);
                appendWithNewlineAfter(submenu, fieldSnapshotFormat);
            }

        } else if (affected == choiceSnapshotFormat) {
            int i0 = choiceSnapshotFormat.getSelectedIndex();
            String s0 = choiceSnapshotFormat.getString(i0);
            fieldSnapshotFormat.setString(s0);
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        // top-level menu action?
        if (displayable == this) {
            // open submenu?
            if (Command.SCREEN == command.getCommandType()) {
                // submenu
                show(getString(getSelectedIndex()));
            } else {
                // main menu action
                mainMenuCommandAction(command);
            }
        } else {
            // grab changes
            subMenuCommandAction(command);
        }
    }

    private void subMenuCommandAction(Command command) {
        // restore top-level menu
        Desktop.display.setCurrent(this);

        if (command.getCommandType() != Command.BACK) { // "Cancel"

            // submenu
            String section = submenu.getTitle();

            if (section.startsWith(MENU_BASIC)) {

                // map path
                if (File.isFs()) {
                    Config.mapPath = fieldMapPath.getString();
                }

                // datum
                Config.geoDatum = choiceMapDatum.getString(choiceMapDatum.getSelectedIndex());

                // coordinates format
                final boolean[] fmt = new boolean[choiceCoordinates.size()];
                choiceCoordinates.getSelectedFlags(fmt);
                Config.useGridFormat = fmt[1];
                Config.useUTM = fmt[2];
                Config.useGeocachingFormat = fmt[3];

                // units format
                Config.units = choiceUnits.getSelectedIndex();

                // datadir
                if (File.isFs()) {
                    Config.setDataDir(fieldDataDir.getString());
                }

            } else if (section.startsWith(MENU_LOCATION)) {

                // provider
                if (choiceProvider.size() > 0) {
                    Config.locationProvider = Config.getLocationProviders()[choiceProvider.getSelectedIndex()];
                }

                // provider-specific
                if (File.isFs()) {
                    Config.simulatorDelay = Integer.parseInt(fieldSimulatorDelay.getString());
                }
                if (cz.kruch.track.TrackingMIDlet.jsr179) {
                    Config.setLocationTimings(fieldLocationTimings.getString());
                }
                if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                    Config.commUrl = fieldCommUrl.getString();
                }
                if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                    Config.o2Depth = Integer.parseInt(fieldO2Depth.getString());
                }

                // tracklogs, waypoints
                if (File.isFs()) {
                    Config.tracklog = choiceTracklog.getSelectedIndex();
                    Config.tracklogFormat = choiceTracklogFormat.getString(choiceTracklogFormat.getSelectedIndex());
                    final boolean[] opts = new boolean[choiceGpx.size()];
                    choiceGpx.getSelectedFlags(opts);
                    Config.gpxOnlyValid = opts[0];
                    Config.gpxDt = Integer.parseInt(fieldGpxDt.getString());
                    Config.gpxDs = Integer.parseInt(fieldGpxDs.getString());
                    if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                        Config.captureLocator = fieldCaptureLocator.getString();
                        Config.snapshotFormat = fieldSnapshotFormat.getString();
                    }
                }

            } else if (section.startsWith(MENU_NAVIGATION)) {

                // navigation
                Config.wptProximity = Integer.parseInt(fieldWptProximity.getString());
                Config.poiProximity = Integer.parseInt(fieldPoiProximity.getString());
                final boolean[] rl = new boolean[choiceRouteLine.size()];
                choiceRouteLine.getSelectedFlags(rl);
                Config.routeLineStyle = rl[0];
                Config.routeLineColor = rl[1] ? 0x00FF0000 : 0x0;
                Config.routePoiMarks = rl[2];

                // location sharing
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    final boolean[] friends = new boolean[choiceFriends.size()];
                    choiceFriends.getSelectedFlags(friends);
                    Config.locationSharing = friends[0];
                }

            } else if (section.startsWith(MENU_DESKTOP)) {

                // desktop
                final boolean[] misc = new boolean[choiceMisc.size()];
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
                changed = true;

            } else if (section.startsWith(MENU_MISC)) {

                // performance
                final boolean[] perf = new boolean[choicePerformance.size()];
                choicePerformance.getSelectedFlags(perf);
                Config.siemensIo = perf[0];
                Config.S60renderer = perf[1];
                Config.forcedGc = perf[2];
                Config.oneTileScroll = perf[3];

            }
        }

        // gc hint
        submenu = null;
    }

    private void mainMenuCommandAction(Command command) {

        // restore desktop
        Desktop.display.setCurrent(Desktop.screen);

        if (command.getCommandType() != Command.BACK) { // "Apply", "Save"

            // save?
            if (/* Save */2 == command.getPriority()) {
                try {
                    // update config
                    Config.update(Config.CONFIG_090);

                    // show confirmation
                    Desktop.showConfirmation(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATED), Desktop.screen);

                } catch (ConfigurationException e) {
                    // show error
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATE_FAILED), e, Desktop.screen);
                }
            }
        }

        // notify that we are done
        callback.invoke(changed ? Boolean.TRUE : Boolean.FALSE, null, this);
    }

    private static int appendWithNewlineAfter(Form form, Item item) {
//        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_SHRINK | Item.LAYOUT_VSHRINK);
        return form.append(item);
    }

    private static int appendShrinked(Form form, Item item) {
//        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_SHRINK | Item.LAYOUT_VSHRINK);
        return form.append(item);
    }
}
