// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;
import cz.kruch.track.fun.Camera;

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
import javax.microedition.lcdui.CustomItem;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.ItemCommandListener;

import api.file.File;
import api.location.Datum;

import java.util.Vector;

/**
 * Settings forms.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SettingsForm implements CommandListener, ItemStateListener {
    private static final int MAX_URL_LENGTH = 256;

    private final String menuBasic;
    private final String menuDesktop;
    private final String menuLocation;
    private final String menuNavigation;
    private final String menuMisc;

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
    private TextField fieldBtKeepalive;
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
    private Gauge gaugeAlpha;
    private TextField fieldCmsCycle;
    private ChoiceGroup choiceWaypoints;
    private TrailItem itemTrailView;
    private ChoiceGroup choiceSort;
    private TextField fieldListFont;
    private TextField fieldAltCorrection;

    private List pane;
    private Form submenu;
    private String section;
    private Vector providers;
    private boolean changed;
    private int gaugeAlphaScale;

    SettingsForm(Callback callback) {
        this.callback = callback;
        this.menuBasic = Resources.getString(Resources.CFG_ITEM_BASIC);
        this.menuDesktop = Resources.getString(Resources.CFG_ITEM_DESKTOP);
        this.menuLocation = Resources.getString(Resources.CFG_ITEM_LOCATION);
        this.menuNavigation = Resources.getString(Resources.CFG_ITEM_NAVIGATION);
        this.menuMisc = Resources.getString(Resources.CFG_ITEM_MISC);
    }

    public void show() {
        // create main pane
        pane = new List(Resources.prefixed(Resources.getString(Resources.DESKTOP_CMD_SETTINGS)), List.IMPLICIT);
        // top-level menu
        pane.append(menuBasic, null);
        pane.append(menuDesktop, null);
        pane.append(menuLocation, null);
        pane.append(menuNavigation, null);
        pane.append(menuMisc, null);

        // add command and handling
        pane.addCommand(new Command(Resources.getString(Resources.CFG_CMD_SAVE), Command.SCREEN, 1));
        pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
        /* default SELECT command is of SCREEN type */
        pane.setCommandListener(this);

        // show
        Desktop.display.setCurrent(pane);
    }

    private void show(String section) {
        // submenu form
        submenu = new Form(Resources.prefixed(section));

        if (menuBasic.equals(section)) {

            // default map path field
            if (File.isFs()) {
                submenu.append(fieldMapPath = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_START_MAP), Config.mapPath, MAX_URL_LENGTH, TextField.URL));
            }

            // map datum
            choiceMapDatum = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_DEFAULT_DATUM), Desktop.CHOICE_POPUP_TYPE);
            choiceMapDatum.setFitPolicy(Choice.TEXT_WRAP_ON);
            for (int N = Config.datums.size(), i = 0; i < N; i++) {
                String id = ((Datum) Config.datums.elementAt(i)).name;
                choiceMapDatum.setSelectedIndex(choiceMapDatum.append(id, null), Config.geoDatum.equals(id));
            }
            submenu.append(choiceMapDatum);

            // coordinates format
            choiceCoordinates = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_COORDS_FMT), Desktop.CHOICE_POPUP_TYPE);
            choiceCoordinates.setFitPolicy(Choice.TEXT_WRAP_ON);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPLL), null);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPGRID), null);
            choiceCoordinates.append("UTM", null);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_GCLL), null);
/*
            choiceCoordinates.setSelectedFlags(new boolean[]{
                false,
                Config.useGridFormat,
                Config.useUTM,
                Config.useGeocachingFormat
            });
*/
            choiceCoordinates.setSelectedIndex(Config.cfmt, true);
            submenu.append(choiceCoordinates);

            // units format
            choiceUnits = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_UNITS), Desktop.CHOICE_POPUP_TYPE);
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

        } else if (menuDesktop.equals(section)) {

            // trail line setup
            if (cz.kruch.track.TrackingMIDlet.sonyEricsson) {
                submenu.append(itemTrailView = new TrailItem(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAIL_PREVIEW),
                                                             Config.trailColor, Config.trailThick));
            }

            // desktop settings
            choiceMisc = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP), ChoiceGroup.MULTIPLE);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_FULLSCREEN), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_SAFE_COLORS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_SOUNDS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAJECTORY), null);
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
                Config.safeColors,
                Config.noSounds,
                Config.trailOn,
                Config.decimalPrecision,
                Config.hpsWptTrueAzimuth,
                Config.osdBasic,
                Config.osdExtended,
                Config.osdScale,
                Config.osdNoBackground,
                Config.osdMediumFont,
                Config.osdBoldFont,
                Config.osdBlackColor,
            });
            submenu.append(choiceMisc);

            // OSD transparency
            int alphaSteps = Desktop.display.numAlphaLevels();
            if (alphaSteps > 16) {
                alphaSteps = 16;
            }
            gaugeAlphaScale = 0x100 / alphaSteps;
            final int value = Config.osdAlpha / gaugeAlphaScale;
            submenu.append(gaugeAlpha = new Gauge(Resources.getString(Resources.CFG_DESKTOP_TRANSPARENCY), true, alphaSteps, value));

            // trail line setup
/*
            submenu.append(gaugeTrailColor = new Gauge(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAIL_COLOR), true, 15, Config.trailColor));
            submenu.append(gaugeTrailThick = new Gauge(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAIL_THICK), true, 2, Config.trailThick));
            submenu.setItemStateListener(this);
*/

            // smartlist font
            StringBuffer hexstr = new StringBuffer(Integer.toHexString(Config.listFont));
            while (hexstr.length() < 6) {
                hexstr.insert(0, '0');
            }
            submenu.append(fieldListFont = new TextField("Lists font", hexstr.toString(), 10, TextField.ANY));
            
            // CMS cycling
            submenu.append(fieldCmsCycle = new TextField(Resources.getString(Resources.CFG_DESKTOP_FLD_CMS_CYCLE), Integer.toString(Config.cmsCycle), 4, TextField.NUMERIC));

            // trail line setup
            if (!cz.kruch.track.TrackingMIDlet.sonyEricsson) {
                submenu.append(itemTrailView = new TrailItem(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAIL_PREVIEW),
                                                             Config.trailColor, Config.trailThick));
            }

        } else if (menuNavigation.equals(section)) {

            // proximity
            submenu.append(fieldWptProximity = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_WPT_PROXIMITY), Integer.toString(Config.wptProximity), 5, TextField.NUMERIC));
            /*append(*/fieldPoiProximity = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_PROXIMITY), Integer.toString(Config.poiProximity), 5, TextField.NUMERIC)/*)*/;

            // route line
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

            // waypoints
            choiceWaypoints = new ChoiceGroup(Resources.getString(Resources.NAV_ITEM_WAYPOINTS), ChoiceGroup.MULTIPLE);
            choiceWaypoints.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_REVISIONS), null);
            choiceWaypoints.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_PREFER_GSNAME), null);
            choiceWaypoints.setSelectedFlags(new boolean[] {
                Config.makeRevisions,
                Config.preferGsName
            });
            submenu.append(choiceWaypoints);

            // wpts sorting
            choiceSort = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_SORT), Desktop.CHOICE_POPUP_TYPE);
            choiceSort.setFitPolicy(Choice.TEXT_WRAP_ON);
            choiceSort.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_SORT_BYPOS), null);
            choiceSort.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_SORT_BYNAME), null);
            choiceSort.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_SORT_BYDIST), null);
            choiceSort.setSelectedIndex(Config.sort, true);
            submenu.append(choiceSort);

            // 'Friends'
            if (cz.kruch.track.TrackingMIDlet.jsr120) {
                choiceFriends = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_SMS), ChoiceGroup.MULTIPLE);
                choiceFriends.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_RECEIVE), null);
                choiceFriends.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_AUTOHIDE), null);
                choiceFriends.setSelectedFlags(new boolean[] {
                    Config.locationSharing,
                    Config.autohideNotification
                });
                submenu.append(choiceFriends);
            }
            
        } else if (menuMisc.equals(section)) {

            // tweaks
            choicePerformance = new ChoiceGroup(Resources.getString(Resources.CFG_TWEAKS_GROUP), ChoiceGroup.MULTIPLE);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SIEMENS_IO), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SAFE_RENDERER), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_FORCED_GC), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_POWER_SAVE), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_1TILE_SCROLL), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LARGE_ATLASES), null);
            choicePerformance.setSelectedFlags(new boolean[] {
                Config.siemensIo,
                Config.S60renderer,
                Config.forcedGc,
                Config.powerSave,
                Config.oneTileScroll,
                Config.largeAtlases
            });
            submenu.append(choicePerformance);

            if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                submenu.append(fieldCaptureLocator = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CAPTURE_LOCATOR), Config.captureLocator, 16, TextField.URL));
                submenu.append(choiceSnapshotFormat = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_FLD_CAPTURE_FMT), Desktop.CHOICE_POPUP_TYPE));
                final String[] formats = Camera.getStillResolutions();
                for (int N = formats.length, i = 0; i < N; i++) {
                    choiceSnapshotFormat.setSelectedIndex(choiceSnapshotFormat.append(formats[i], null), Config.snapshotFormat.equals(formats[i]));
                }
                submenu.append(fieldSnapshotFormat = new TextField(null, Config.snapshotFormat, 64, TextField.ANY));
                if (Config.snapshotFormat == null || Config.snapshotFormat.length() == 0) {
                    if (choiceSnapshotFormat.size() > 0) {
                        fieldSnapshotFormat.setString(choiceSnapshotFormat.getString(choiceSnapshotFormat.getSelectedIndex()));
                    }
                }
                submenu.setItemStateListener(this);
            }

        } else if (menuLocation.equals(section)) {

            // location provider choice
            choiceProvider = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_PROVIDER), ChoiceGroup.EXCLUSIVE);
            final Vector providers = getLocationProviders();
            for (int N = providers.size(), i = 0; i < N; i++) {
                final int provider = ((Integer) providers.elementAt(i)).intValue();
                short resourceId = -1;
                switch (provider) {
                    case Config.LOCATION_PROVIDER_JSR82:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_BLUETOOTH;
                    break;
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
                    case Config.LOCATION_PROVIDER_HGE100:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_HGE100;
                    break;
                }
                choiceProvider.setSelectedIndex(choiceProvider.append(Resources.getString(resourceId), null),
                                                Config.locationProvider == provider);
            }
            if (choiceProvider.size() > 0) {
                submenu.append(choiceProvider);
            }

            // tracklogs, waypoints
            if (File.isFs()) {
                choiceTracklog = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_TRACKLOG), ChoiceGroup.EXCLUSIVE);
                choiceTracklog.setSelectedIndex(choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null), Config.TRACKLOG_NEVER == Config.tracklog);
                if (Config.dataDirExists) {
                    choiceTracklog.setSelectedIndex(choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ASK), null), Config.TRACKLOG_ASK == Config.tracklog);
                    choiceTracklog.setSelectedIndex(choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null), Config.TRACKLOG_ALWAYS == Config.tracklog);
                }

                choiceTracklogFormat = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_TRACKLOG_FMT), ChoiceGroup.EXCLUSIVE);
                choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_GPX, null), Config.TRACKLOG_FORMAT_GPX.equals(Config.tracklogFormat));
                choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_NMEA, null), Config.TRACKLOG_FORMAT_NMEA.equals(Config.tracklogFormat));

                choiceGpx = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_GPX_OPTS), ChoiceGroup.MULTIPLE);
                choiceGpx.append(Resources.getString(Resources.CFG_LOCATION_FLD_GPX_LOG_VALID), null);
                choiceGpx.append(Resources.getString(Resources.CFG_LOCATION_FLD_GPX_LOG_GSM), null);
                choiceGpx.append(Resources.getString(Resources.CFG_LOCATION_FLD_GPX_LOG_TIME_MS), null);
                choiceGpx.setSelectedFlags(new boolean[] {
                    Config.gpxOnlyValid,
                    Config.gpxGsmInfo,
                    Config.gpxSecsDecimal
                });

                fieldGpxDt = new TextField("GPX dt (s)", Integer.toString(Config.gpxDt), 5, TextField.NUMERIC);
                fieldGpxDs = new TextField("GPX ds (m)", Integer.toString(Config.gpxDs), 5, TextField.NUMERIC);
            }

            // provider specific
            if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                fieldCommUrl = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CONN_URL), Config.commUrl, 64, TextField.ANY);
            }
            if (File.isFs()) {
                fieldSimulatorDelay = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_SIMULATOR_DELAY), Integer.toString(Config.simulatorDelay), 8, TextField.NUMERIC);
            }
            if (cz.kruch.track.TrackingMIDlet.jsr82) {
                fieldBtKeepalive = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_BT_KEEP_ALIVE), Integer.toString(Config.btKeepAlive), 6, TextField.NUMERIC);
            }
            if (cz.kruch.track.TrackingMIDlet.jsr179 || cz.kruch.track.TrackingMIDlet.motorola179) {
                fieldLocationTimings = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_LOCATION_TIMINGS), "", 12, TextField.ANY);
                fieldAltCorrection = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_ALT_CORRECTION), "", 4, TextField.NUMERIC);
            }
            if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                fieldO2Depth = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_FILTER_DEPTH), Integer.toString(Config.o2Depth), 2, TextField.NUMERIC);
            }

            // show current provider and tracklog specific options
            itemStateChanged(choiceProvider);
            submenu.setItemStateListener(this);
        }

        // add command and handling
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_OK), Desktop.POSITIVE_CMD_TYPE, 0));
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        submenu.setCommandListener(this);

        // show
        Desktop.display.setCurrent(submenu);
    }

    public void itemStateChanged(Item affected) {
        if (affected == choiceProvider || affected == choiceTracklog || affected == choiceTracklogFormat) {

            final Form submenu = this.submenu;
            for (int i = submenu.size(); --i >= 0; ) {
                final Item item = submenu.get(i);

                if (choiceProvider == item)
                    continue;

                if (choiceTracklogFormat == affected) {
                    if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || fieldBtKeepalive == item || fieldO2Depth == item || fieldAltCorrection == item || choiceTracklog == item || choiceTracklogFormat == item)
                        continue;
                }

                if (choiceTracklog == affected) {
                    if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || fieldBtKeepalive == item || fieldO2Depth == item || fieldAltCorrection == item || choiceTracklog == item)
                        continue;
                }

                submenu.delete(i);
                i = submenu.size();
            }

            final int provider = ((Integer) providers.elementAt(choiceProvider.getSelectedIndex())).intValue();
            final boolean isFs = File.isFs();
            final boolean isTracklog = isFs && choiceTracklog.getSelectedIndex() > Config.TRACKLOG_NEVER;
            final boolean isTracklogGpx = isTracklog && Config.TRACKLOG_FORMAT_GPX.equals(choiceTracklogFormat.getString(choiceTracklogFormat.getSelectedIndex()));

            if (choiceProvider == affected) {
                switch (provider) {
                    case Config.LOCATION_PROVIDER_JSR82:
                        appendWithNewlineAfter(submenu, fieldBtKeepalive);
                    break;
                    case Config.LOCATION_PROVIDER_JSR179:
                    case Config.LOCATION_PROVIDER_MOTOROLA:
//#ifndef __ANDROID__
                        fieldLocationTimings.setString(Config.getLocationTimings(provider));
                        appendWithNewlineAfter(submenu, fieldLocationTimings);
                        fieldAltCorrection.setString(Integer.toString(Config.altCorrection));
                        appendWithNewlineAfter(submenu, fieldAltCorrection);
//#endif
                    break;
                    case Config.LOCATION_PROVIDER_SERIAL:
                        appendWithNewlineAfter(submenu, fieldCommUrl);
                    break;
                    case Config.LOCATION_PROVIDER_SIMULATOR:
                        appendWithNewlineAfter(submenu, fieldSimulatorDelay);
                    break;
                    case Config.LOCATION_PROVIDER_O2GERMANY:
                        appendWithNewlineAfter(submenu, fieldO2Depth);
                    break;
                }
                if (isFs) {
                    appendWithNewlineAfter(submenu, choiceTracklog);
                    if (isTracklog) {
                        switch (provider) {
                            case Config.LOCATION_PROVIDER_JSR82:
                            case Config.LOCATION_PROVIDER_JSR179:
                            case Config.LOCATION_PROVIDER_SERIAL:
                            case Config.LOCATION_PROVIDER_HGE100:
                                appendWithNewlineAfter(submenu, choiceTracklogFormat);
                                break;
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
                    switch (provider) {
                        case Config.LOCATION_PROVIDER_JSR82:
                        case Config.LOCATION_PROVIDER_JSR179:
                        case Config.LOCATION_PROVIDER_SERIAL:
                        case Config.LOCATION_PROVIDER_HGE100:
                            appendWithNewlineAfter(submenu, choiceTracklogFormat);
                            break;
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

        } else if (affected == choiceSnapshotFormat) {
            final int i0 = choiceSnapshotFormat.getSelectedIndex();
            final String s0 = choiceSnapshotFormat.getString(i0);
            fieldSnapshotFormat.setString(s0);
        } /*else if (affected == gaugeTrailColor || affected == gaugeTrailThick) {
            gaugeTrailView.update();
        }*/
    }

    public void commandAction(Command command, Displayable displayable) {
        // top-level menu action?
        if (displayable == pane) {
            // open submenu?
            if (List.SELECT_COMMAND == command) {
                // submenu
                show(section = pane.getString(pane.getSelectedIndex()));
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

        // grab values on OK
        if (command.getCommandType() != Desktop.CANCEL_CMD_TYPE) {

            if (menuBasic.equals(section)) {

                // map path
                if (File.isFs()) {
                    Config.mapPath = fieldMapPath.getString();
                }

                // datum
                Config.geoDatum = choiceMapDatum.getString(choiceMapDatum.getSelectedIndex());

                // coordinates format
                Config.cfmt = choiceCoordinates.getSelectedIndex();
/*
                Config.useGridFormat = Config.useUTM = Config.useGeocachingFormat = false;
                switch (choiceCoordinates.getSelectedIndex()) { // getSelectedFlags does not work on JP-7
                    case 1:
                        Config.useGridFormat = true;
                        break;
                    case 2:
                        Config.useUTM = true;
                        break;
                    case 3:
                        Config.useGeocachingFormat = true;
                        break;
                }
*/

                // units format
                Config.units = choiceUnits.getSelectedIndex();

                // datadir
                if (File.isFs()) {
                    Config.setDataDir(fieldDataDir.getString());
                }

            } else if (menuLocation.equals(section)) {

                // provider
                if (choiceProvider.size() > 0) {
                    Config.locationProvider = ((Integer) providers.elementAt(choiceProvider.getSelectedIndex())).intValue();
                }

                // provider-specific
                if (File.isFs()) {
                    Config.simulatorDelay = Integer.parseInt(fieldSimulatorDelay.getString());
                }
                if (cz.kruch.track.TrackingMIDlet.jsr179 || cz.kruch.track.TrackingMIDlet.motorola179) {
                    Config.setLocationTimings(fieldLocationTimings.getString());
                    Config.altCorrection = Integer.parseInt(fieldAltCorrection.getString());
                }
                if (cz.kruch.track.TrackingMIDlet.jsr82) {
                    Config.btKeepAlive = Integer.parseInt(fieldBtKeepalive.getString());
                    if (Config.btKeepAlive > 0 && Config.btKeepAlive < 250) {
                        Config.btKeepAlive = 250;
                    }
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
                    Config.gpxGsmInfo = opts[1];
                    Config.gpxSecsDecimal = opts[2];
                    Config.gpxDt = Integer.parseInt(fieldGpxDt.getString());
                    Config.gpxDs = Integer.parseInt(fieldGpxDs.getString());
                }

            } else if (menuNavigation.equals(section)) {

                // proximity
                Config.wptProximity = Integer.parseInt(fieldWptProximity.getString());
                Config.poiProximity = Integer.parseInt(fieldPoiProximity.getString());

                // route line
                final boolean[] rl = new boolean[choiceRouteLine.size()];
                choiceRouteLine.getSelectedFlags(rl);
                Config.routeLineStyle = rl[0];
                Config.routeLineColor = rl[1] ? 0x00FF0000 : 0x0;
                Config.routePoiMarks = rl[2];

                // waypoints
                final boolean[] wpts = new boolean[choiceWaypoints.size()];
                choiceWaypoints.getSelectedFlags(wpts);
                Config.makeRevisions = wpts[0];
                Config.preferGsName = wpts[1];

                // wpts sorting
                Config.sort = choiceSort.getSelectedIndex();

                // location sharing
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    final boolean[] friends = new boolean[choiceFriends.size()];
                    choiceFriends.getSelectedFlags(friends);
                    Config.locationSharing = friends[0];
                    Config.autohideNotification = friends[1];
                }

            } else if (menuDesktop.equals(section)) {

                // desktop
                final boolean[] misc = new boolean[choiceMisc.size()];
                choiceMisc.getSelectedFlags(misc);
                Config.fullscreen = misc[0];
                Config.safeColors = misc[1];
                Config.noSounds = misc[2];
                Config.trailOn = misc[3];
                Config.decimalPrecision = misc[4];
                Config.hpsWptTrueAzimuth = misc[5];
                Config.osdBasic = misc[6];
                Config.osdExtended = misc[7];
                Config.osdScale = misc[8];
                Config.osdNoBackground = misc[9];
                Config.osdMediumFont = misc[10];
                Config.osdBoldFont = misc[11];
                Config.osdBlackColor = misc[12];
                Config.osdAlpha = gaugeAlpha.getValue() * gaugeAlphaScale;
                Config.cmsCycle = Integer.parseInt(fieldCmsCycle.getString());
                Config.listFont = Integer.parseInt(fieldListFont.getString(), 16);
                Config.trailColor = itemTrailView.color/*gaugeTrailColor.getValue()*/;
                Config.trailThick = itemTrailView.thick/*gaugeTrailThick.getValue()*/;
                changed = true;

            } else if (menuMisc.equals(section)) {

                // performance
                final boolean[] perf = new boolean[choicePerformance.size()];
                choicePerformance.getSelectedFlags(perf);
                Config.siemensIo = perf[0];
                Config.S60renderer = perf[1];
                Config.forcedGc = perf[2];
                Config.powerSave = perf[3];
                Config.oneTileScroll = perf[4];
                Config.largeAtlases = perf[5];

                // multimedia
                if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                    Config.captureLocator = fieldCaptureLocator.getString();
                    Config.snapshotFormat = fieldSnapshotFormat.getString();
                    Config.snapshotFormatIdx = choiceSnapshotFormat.getSelectedIndex();
                }
            }
        }

        // gc hint
        submenu.setCommandListener(null);
        submenu.deleteAll();
        submenu = null;

        // restore top-level menu
        Desktop.display.setCurrent(pane);
    }

    private void mainMenuCommandAction(Command command) {

        // restore desktop
        Desktop.display.setCurrent(Desktop.screen);

        // save?
        if (command.getCommandType() != Desktop.BACK_CMD_TYPE) {
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

        // notify that we are done
        callback.invoke(new Boolean(changed), null, this);
    }

    private static int appendWithNewlineAfter(Form form, Item item) {
//        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_SHRINK | Item.LAYOUT_VSHRINK);
        return form.append(item);
    }

    private static int appendShrinked(Form form, Item item) {
//        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_SHRINK | Item.LAYOUT_VSHRINK);
        return form.append(item);
    }

    private Vector getLocationProviders() {
        if (providers == null) {
            providers = new Vector(8);
            if (cz.kruch.track.TrackingMIDlet.jsr82) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_JSR82));
            }
            if (cz.kruch.track.TrackingMIDlet.jsr179 || cz.kruch.track.TrackingMIDlet.android) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_JSR179));
            }
//#ifdef __ALL__
            if (cz.kruch.track.TrackingMIDlet.sonyEricssonEx) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_HGE100));
            }
//#endif
            if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_SERIAL));
            }
            if (api.file.File.isFs()) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_SIMULATOR));
            }
//#ifdef __ALL__
            if (cz.kruch.track.TrackingMIDlet.motorola179) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_MOTOROLA));
            }
//#endif
            if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_O2GERMANY));
            }
        }

        return providers;
    }

    private final class TrailItem extends CustomItem implements ItemCommandListener {

        private int thick;
        private int color;
        
        private boolean isIn;
        private int mode;

        public TrailItem(String label, int color, int thickness) {
            super(label);
            this.color = color;
            this.thick = thickness;
            this.setDefaultCommand(new Command(Resources.getString(Resources.CFG_DESKTOP_CMD_TRAIL_MODE), Command.ITEM, 1));
            this.setItemCommandListener(this);
        }

        public void commandAction(Command command, Item item) {
            mode++;
        }

        protected boolean traverse(int dir, int viewportWidth, int viewportHeight, int[] visRect_inout) {
            // ??? mobile-utopia textfield
            super.traverse(dir, viewportWidth, viewportHeight, visRect_inout);

            boolean notify = false;
            boolean repaint = false;

            if (isIn) {
                switch (dir) {
                    case javax.microedition.lcdui.Canvas.LEFT: {
                        if (mode % 2 == 0) {
                            if (--color < 0) {
                                ++color;
                            } else {
                                notify = repaint = true;
                            }
                        } else {
                            if (--thick < 0) {
                                ++thick;
                            } else {
                                notify = repaint = true;
                            }
                        }
                    } break;
                    case javax.microedition.lcdui.Canvas.RIGHT: {
                        if (mode % 2 == 0) {
                            if (++color == Config.COLORS_16.length) {
                                --color;
                            } else {
                                notify = repaint = true;
                            }
                        } else {
                            if (++thick == 4) {
                                --thick;
                            } else {
                                notify = repaint = true;
                            }
                        }
                    } break;
                    case javax.microedition.lcdui.Canvas.UP:
                    case javax.microedition.lcdui.Canvas.DOWN:
                        return false;
                }
            } else {
                repaint = true;
                isIn = true;
            }

            if (repaint) {
                repaint();
            }
            if (notify) {
                notifyStateChanged();
            }

            return true;
        }

        protected void traverseOut() {
            super.traverseOut(); // ??? both mobile-utopia and google code
            isIn = false;
        }

        protected int getMinContentWidth() {
            return (int) ((float)submenu.getWidth() * 0.85F);
        }

        protected int getMinContentHeight() {
            return 3 + 7 + 3; // space + max trail thickness + space
        }

        protected int getPrefContentWidth(int i) {
            return getMinContentWidth();
        }

        protected int getPrefContentHeight(int i) {
            return getMinContentHeight();
        }

        protected void keyPressed(int i) {
            int a;
            try {
                a = getGameAction(i);
            } catch (Exception e) {
                a = NONE;
            }
            switch (a) {
                case javax.microedition.lcdui.Canvas.UP:
                case javax.microedition.lcdui.Canvas.LEFT:
                case javax.microedition.lcdui.Canvas.RIGHT:
                case javax.microedition.lcdui.Canvas.DOWN:
                    // ignore
                    break;
                default:
                    mode++;
            }
        }

        protected void pointerPressed(int x, int y) {
            boolean notify = false;
            boolean repaint = false;

            if (x < getMinContentWidth() * 0.33D) {
                if (mode % 2 == 0) {
                    if (--color < 0) {
                        ++color;
                    } else {
                        notify = repaint = true;
                    }
                } else {
                    if (--thick < 0) {
                        ++thick;
                    } else {
                        notify = repaint = true;
                    }
                }
            } else if (x > getMinContentWidth() * 0.66D) {
                if (mode % 2 == 0) {
                    if (++color == Config.COLORS_16.length) {
                        --color;
                    } else {
                        notify = repaint = true;
                    }
                } else {
                    if (++thick == 4) {
                        --thick;
                    } else {
                        notify = repaint = true;
                    }
                }
            } else {
                mode++;
            }

            if (repaint) {
                repaint();
            }
            if (notify) {
                notifyStateChanged();
            }
        }

        protected void paint(Graphics graphics, int w, int h) {
            final int c = graphics.getColor();
            graphics.setColor(0x40ffffff);
            graphics.fillRect(0, 0, w, h);
            graphics.setColor(Config.COLORS_16[color]);
            graphics.fillRect(3, /*3 + 3*/h / 2 - thick, w - 3 - 3, thick * 2 + 1);
            graphics.setColor(c);
        }
    }
}
