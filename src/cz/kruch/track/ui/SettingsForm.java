// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.fun.Camera;

import api.file.File;
import api.location.Datum;
import api.location.ProjectionSetup;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.StringItem;
import java.util.Vector;

/**
 * Settings forms.
 *
 * @author kruhc@seznam.cz
 */
final class SettingsForm implements CommandListener, ItemStateListener, ItemCommandListener {
    private static final int MAX_URL_LENGTH = 256;

    private final String menuBasic;
    private final String menuDesktop;
    private final String menuLocation;
    private final String menuNavigation;
    private final String menuMisc;

    private final Desktop.Event event;

    private TextField fieldMapPath;
    private ChoiceGroup choiceMapDatum;
    private ChoiceGroup choiceCoordinates;
    private ChoiceGroup choiceUnits;
    private ChoiceGroup choiceScreen;
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
    private Gauge gaugeDesktopFont;
    private Gauge gaugeOsdAlpha;
    private TextField fieldCmsCycle;
    private ChoiceGroup choiceWaypoints;
    private Item itemLineCfg;
    private ChoiceGroup choiceSort;
    private TextField fieldListFont;
    private TextField fieldAltCorrection;
    private ChoiceGroup choiceStream;
    private ChoiceGroup choiceInternal;
    private ChoiceGroup choicePower;
    private ChoiceGroup choiceEasyzoom;
    private ChoiceGroup choiceZoomSpots;
    private ChoiceGroup choiceGuideSpots;

    private List pane;
    private Form submenu;
    private String section;
    private Vector providers;
    private int gaugeAlphaScale;

    private boolean changed;

    private final int NUMERIC;

    SettingsForm(Desktop.Event event) {
        this.event = event;
        this.menuBasic = Resources.getString(Resources.CFG_ITEM_BASIC);
        this.menuDesktop = Resources.getString(Resources.CFG_ITEM_DESKTOP);
        this.menuLocation = Resources.getString(Resources.CFG_ITEM_LOCATION);
        this.menuNavigation = Resources.getString(Resources.CFG_ITEM_NAVIGATION);
        this.menuMisc = Resources.getString(Resources.CFG_ITEM_MISC);
        if (Config.numericInputHack) {
            NUMERIC = TextField.ANY;
        } else {
            NUMERIC = TextField.NUMERIC;
        }
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
                submenu.append(fieldMapPath = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_START_MAP),
                                                            Config.mapURL, MAX_URL_LENGTH, TextField.URL));
            }

            // map datum
            choiceMapDatum = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_DEFAULT_DATUM), Desktop.CHOICE_POPUP_TYPE);
            choiceMapDatum.setFitPolicy(Choice.TEXT_WRAP_ON);
            for (int N = Config.datums.size(), i = 0; i < N; i++) {
                final String id = ((Datum) Config.datums.elementAt(i)).name;
                choiceMapDatum.setSelectedIndex(choiceMapDatum.append(id, null), Config.geoDatum.equals(id));
            }
            submenu.append(choiceMapDatum);

            // coordinates format
            choiceCoordinates = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_COORDS_FMT), Desktop.CHOICE_POPUP_TYPE);
            choiceCoordinates.setFitPolicy(Choice.TEXT_WRAP_ON);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPLL), null);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPGRID), null);
            choiceCoordinates.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_GCLL), null);
            choiceCoordinates.append(ProjectionSetup.PROJ_UTM, null);
            // TODO: add BNG, IG, SG, SUI
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

            // startup screen
            choiceScreen = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_START_SCREEN), Desktop.CHOICE_POPUP_TYPE);
            choiceScreen.setFitPolicy(Choice.TEXT_WRAP_ON);
            choiceScreen.append(Resources.getString(Resources.CFG_BASIC_FLD_SCREEN_MAP), null);
            choiceScreen.append(Resources.getString(Resources.CFG_BASIC_FLD_SCREEN_HPS), null);
            choiceScreen.append(Resources.getString(Resources.CFG_BASIC_FLD_SCREEN_CMS), null);
            choiceScreen.setSelectedIndex(Config.startupScreen, true);
            submenu.append(choiceScreen);

            // datadir
            if (File.isFs()) {
                submenu.append(fieldDataDir = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_DATA_DIR),
                               Config.getDataDir(), MAX_URL_LENGTH, TextField.URL));
            }

        } else if (menuDesktop.equals(section)) {

            // desktop settings
            choiceMisc = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP), ChoiceGroup.MULTIPLE);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_FULLSCREEN), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_SAFE_COLORS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_SOUNDS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_QUESTIONS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_COMMANDS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_ITEM_COMMANDS), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAJECTORY), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_DEC_PRECISION), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HPS_WPT_TRUE_AZI), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_VOLUME), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BASIC), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_EXT), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_SCALE), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_NO_BG), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BOLD_FONT), null);
            choiceMisc.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BLACK), null);
            choiceMisc.setSelectedFlags(new boolean[] {
                Config.fullscreen,
                Config.safeColors,
                Config.noSounds,
                Config.noQuestions,
                Config.uiNoCommands,
                Config.uiNoItemCommands,
                Config.trailOn,
                Config.decimalPrecision,
                Config.hpsWptTrueAzimuth,
                Config.easyZoomVolumeKeys,
                Config.osdBasic,
                Config.osdExtended,
                Config.osdScale,
                Config.osdNoBackground,
                Config.osdBoldFont,
                Config.osdBlackColor,
            });
            submenu.append(choiceMisc);

            // icons appearance
            if (Desktop.screen.hasPointerEvents()) {
                choiceZoomSpots = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_ZOOM_SPOTS), ChoiceGroup.POPUP);
                choiceZoomSpots.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null);
                choiceZoomSpots.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null);
                choiceZoomSpots.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HIDE_AFTER) + " 3s", null);
                choiceZoomSpots.setSelectedIndex(Config.zoomSpotsMode, true);
                submenu.append(choiceZoomSpots);
                choiceGuideSpots = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_GUIDE_SPOTS), ChoiceGroup.POPUP);
                choiceGuideSpots.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null);
                choiceGuideSpots.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null);
                choiceGuideSpots.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HIDE_AFTER) + " 3s", null);
                choiceGuideSpots.setSelectedIndex(Config.guideSpotsMode, true);
                submenu.append(choiceGuideSpots);
            }

            // easyzoom
            choiceEasyzoom = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_EASYZOOM), ChoiceGroup.POPUP);
            choiceEasyzoom.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_OFF), null);
            choiceEasyzoom.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_LAYERS), null);
//            choiceEasyzoom.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_MAPS), null);
            choiceEasyzoom.setSelectedIndex(Config.easyZoomMode, true);
            submenu.append(choiceEasyzoom);
            
            // font
            submenu.append(gaugeDesktopFont = new Gauge(Resources.getString(Resources.CFG_DESKTOP_FLD_FONT_SIZE), true, 2, Config.desktopFontSize));

            // OSD transparency
            int alphaSteps = Desktop.display.numAlphaLevels();
            if (alphaSteps > 16) {
                alphaSteps = 16;
            }
            gaugeAlphaScale = 0x100 / alphaSteps;
            final int value = Config.osdAlpha / gaugeAlphaScale;
            submenu.append(gaugeOsdAlpha = new Gauge(Resources.getString(Resources.CFG_DESKTOP_TRANSPARENCY), true, alphaSteps, value));

            // smartlist font
            StringBuffer hexstr = new StringBuffer(Integer.toHexString(Config.listFont));
            while (hexstr.length() < 6) {
                hexstr.insert(0, '0');
            }
            submenu.append(fieldListFont = new TextField(Resources.getString(Resources.CFG_DESKTOP_FLD_LIST_FONT), hexstr.toString(), 10, TextField.ANY));
            
            // CMS cycling
            submenu.append(fieldCmsCycle = new TextField(Resources.getString(Resources.CFG_DESKTOP_FLD_CMS_CYCLE), Integer.toString(Config.cmsCycle), 4, /*TextField.*/NUMERIC));

            // trail line
            submenu.append(itemLineCfg = createLineCfgItem(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAIL_PREVIEW),
                                                           Config.trailColor, Config.trailThick));
            submenu.append(new Spacer(submenu.getWidth(), 1));

        } else if (menuNavigation.equals(section)) {

            // proximity
            submenu.append(fieldWptProximity = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_WPT_PROXIMITY), Integer.toString(Config.wptProximity), 5, /*TextField.*/NUMERIC));
            /*append(*/fieldPoiProximity = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_PROXIMITY), Integer.toString(Config.poiProximity), 5, /*TextField.*/NUMERIC)/*)*/;

            // route line
            choiceRouteLine = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_ROUTE_LINE), ChoiceGroup.MULTIPLE);
            choiceRouteLine.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_DOTTED), null);
//            choiceRouteLine.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_RED), null);
            choiceRouteLine.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_ICONS), null);
            choiceRouteLine.setSelectedFlags(new boolean[] {
                Config.routeLineStyle,
//                Config.routeLineColor != 0,
                Config.routePoiMarks
            });
            submenu.append(choiceRouteLine);

            // route line
            submenu.append(itemLineCfg = createLineCfgItem(Resources.getString(Resources.CFG_NAVIGATION_GROUP_ROUTE_LINE),
                                                           Config.routeColor, Config.routeThick));
            submenu.append(new Spacer(submenu.getWidth(), 1));

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
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LOWMEM_IO), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SAFE_RENDERER), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_FORCED_GC), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_POWER_SAVE), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_1TILE_SCROLL), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LARGE_ATLASES), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LAZY_GPX), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_NUMERIC_INPUT_HACK), null);
            choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_EXTERNAL_CFG_BACKUP), null);
            choicePerformance.setSelectedFlags(new boolean[] {
                Config.siemensIo,
                Config.lowmemIo,
                Config.S60renderer,
                Config.forcedGc,
                Config.powerSave,
                Config.oneTileScroll,
                Config.largeAtlases,
                Config.lazyGpxParsing,
                Config.numericInputHack,
                Config.externalConfigBackup
            });
            if (cz.kruch.track.TrackingMIDlet.symbian) {
                choicePerformance.setSelectedIndex(choicePerformance.append(Resources.getString(Resources.CFG_TWEAKS_FLD_USE_TBSVC), null),
                                                   Config.useNativeService);
            }
            submenu.append(choicePerformance);

            if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                submenu.append(fieldCaptureLocator = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CAPTURE_LOCATOR), Config.captureLocator, 18, TextField.URL));
                submenu.append(choiceSnapshotFormat = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_FLD_CAPTURE_FMT), Desktop.CHOICE_POPUP_TYPE));
                final String[] formats = Camera.getStillResolutions();
                for (int N = formats.length, i = 0; i < N; i++) {
                    final int idx = choiceSnapshotFormat.append(formats[i], null);
                    if ((Camera.type == Camera.TYPE_JSR135 && Config.snapshotFormat.equals(formats[i]))
                        || (Camera.type == Camera.TYPE_JSR234 && Config.snapshotFormatIdx == i)) {
                        choiceSnapshotFormat.setSelectedIndex(idx, true);
                    }
                }
                submenu.append(fieldSnapshotFormat = new TextField(null, Config.snapshotFormat, 64, TextField.ANY));
                if (Camera.type == Camera.TYPE_JSR135 && (Config.snapshotFormat == null || Config.snapshotFormat.length() == 0)) {
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
//#ifdef __ALL__
                    case Config.LOCATION_PROVIDER_MOTOROLA:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_MOTOROLA;
                    break;
                    case Config.LOCATION_PROVIDER_O2GERMANY:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_O2GERMANY;
                    break;
                    case Config.LOCATION_PROVIDER_HGE100:
                        resourceId = Resources.CFG_LOCATION_FLD_PROV_HGE100;
                    break;
//#endif                    
                }
                final int idx = choiceProvider.append(Resources.getString(resourceId), null);
                if (Config.locationProvider == provider) {
                    choiceProvider.setSelectedIndex(idx, true);
                }
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

                fieldGpxDt = new TextField("GPX dt (s)", Integer.toString(Config.gpxDt), 5, /*TextField.*/NUMERIC);
                fieldGpxDs = new TextField("GPX ds (m)", Integer.toString(Config.gpxDs), 5, /*TextField.*/NUMERIC);
            }

            // provider specific
            if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                fieldCommUrl = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CONN_URL), Config.commUrl, 64, TextField.ANY);
            }
            if (File.isFs()) {
                fieldSimulatorDelay = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_SIMULATOR_DELAY), Integer.toString(Config.simulatorDelay), 8, /*TextField.*/NUMERIC);
            }
            if (cz.kruch.track.TrackingMIDlet.jsr82) {
                fieldBtKeepalive = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_BT_KEEP_ALIVE), Integer.toString(Config.btKeepAlive), 6, /*TextField.*/NUMERIC);
            }
            if (cz.kruch.track.TrackingMIDlet.jsr179 || cz.kruch.track.TrackingMIDlet.motorola179) {
//#ifdef __RIM__
                choiceInternal = new ChoiceGroup("Blackberry", ChoiceGroup.MULTIPLE);
                choiceInternal.append(Resources.getString(Resources.CFG_LOCATION_FLD_ASSISTED_GPS), null);
                choiceInternal.append(Resources.getString(Resources.CFG_LOCATION_FLD_NEGATIVE_ALT_FIX), null);
                choiceInternal.setSelectedFlags(new boolean[] {
                    Config.assistedGps,
                    Config.negativeAltFix
                });
//#elifdef __ANDROID__
                // nothing for Android
//#else
                choiceInternal = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_FLD_PROV_INTERNAL), ChoiceGroup.MULTIPLE);
                choiceInternal.append(Resources.getString(Resources.CFG_LOCATION_FLD_ASSISTED_GPS), null);
                choiceInternal.append(Resources.getString(Resources.CFG_LOCATION_FLD_TIME_FIX), null);
                choiceInternal.setSelectedFlags(new boolean[] {
                    Config.assistedGps,
                    Config.timeFix
                });
//#endif
//#ifndef __ANDROID__
                choicePower = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_POWER), Desktop.CHOICE_POPUP_TYPE);
                choicePower.append("NO_REQUIREMENT", null);
                choicePower.append("LOW", null);
                choicePower.append("MEDIUM", null);
                choicePower.append("HIGH", null);
                choicePower.setSelectedIndex(Config.powerUsage, true);
                fieldLocationTimings = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_LOCATION_TIMINGS), Config.getLocationTimings(Config.locationProvider), 12, TextField.ANY);
//#endif
                fieldAltCorrection = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_ALT_CORRECTION), Float.toString(Config.altCorrection), 5, TextField.ANY/*TextField.NUMERIC*/);
            }
//#ifdef __ALL__
            if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                fieldO2Depth = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_FILTER_DEPTH), Integer.toString(Config.o2Depth), 2, /*TextField.*/NUMERIC);
            }
//#endif            

            // show current provider and tracklog specific options
            itemStateChanged(choiceProvider);
            submenu.setItemStateListener(this);
        }

        // add command and handling
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_OK), Desktop.POSITIVE_CMD_TYPE, 0));
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_BACK), Desktop.BACK_CMD_TYPE, 1));
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
                    if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || fieldBtKeepalive == item || fieldO2Depth == item || fieldAltCorrection == item || choiceInternal == item || choiceStream == item || choicePower == item || choiceTracklog == item || choiceTracklogFormat == item)
                        continue;
                }

                if (choiceTracklog == affected) {
                    if (fieldSimulatorDelay == item || fieldLocationTimings == item || fieldCommUrl == item || fieldBtKeepalive == item || fieldO2Depth == item || fieldAltCorrection == item || choiceInternal == item || choiceStream == item || choicePower == item || choiceTracklog == item)
                        continue;
                }

                submenu.delete(i);
                i = submenu.size();
            }

            final int provider = ((Integer) providers.elementAt(choiceProvider.getSelectedIndex())).intValue();
            final boolean isFs = File.isFs();
            final boolean isTracklog = isFs && choiceTracklog.getSelectedIndex() > Config.TRACKLOG_NEVER;
            boolean isTracklogGpx = isTracklog && Config.TRACKLOG_FORMAT_GPX.equals(choiceTracklogFormat.getString(choiceTracklogFormat.getSelectedIndex()));

            if (choiceProvider == affected) {
                final String providerName = choiceProvider.getString(choiceProvider.getSelectedIndex());
                switch (provider) {
                    case Config.LOCATION_PROVIDER_JSR82:
                        appendWithNewlineAfter(submenu, createStreamChoice(provider, providerName));
                        appendWithNewlineAfter(submenu, fieldBtKeepalive);
                    break;
                    case Config.LOCATION_PROVIDER_JSR179:
//#ifdef __ALL__
                    case Config.LOCATION_PROVIDER_MOTOROLA:
//#endif
//#ifndef __ANDROID__
                        appendWithNewlineAfter(submenu, choiceInternal);
                        appendWithNewlineAfter(submenu, choicePower);
                        /* different timings for Motorola and others, hence update... */
                        fieldLocationTimings.setString(Config.getLocationTimings(provider));
                        appendWithNewlineAfter(submenu, fieldLocationTimings);
//#endif
                        appendWithNewlineAfter(submenu, fieldAltCorrection);
                    break;
                    case Config.LOCATION_PROVIDER_SERIAL:
                        appendWithNewlineAfter(submenu, createStreamChoice(provider, providerName));
                        appendWithNewlineAfter(submenu, fieldCommUrl);
                    break;
                    case Config.LOCATION_PROVIDER_SIMULATOR:
                        appendWithNewlineAfter(submenu, createStreamChoice(provider, providerName));
                        appendWithNewlineAfter(submenu, fieldSimulatorDelay);
                    break;
//#ifdef __ALL__
                    case Config.LOCATION_PROVIDER_O2GERMANY:
                        appendWithNewlineAfter(submenu, fieldO2Depth);
                    break;
                    case Config.LOCATION_PROVIDER_HGE100:
                        appendWithNewlineAfter(submenu, createStreamChoice(provider, providerName));
                    break;
//#endif
                }
                if (isFs) {
                    appendWithNewlineAfter(submenu, choiceTracklog);
                    if (isTracklog) {
                        tracklogStateChanged(provider, isTracklogGpx);
                    }
                }
            }

            if (choiceTracklog == affected) {
                if (isTracklog) {
                    tracklogStateChanged(provider, isTracklogGpx);
                }
            }

            if (choiceTracklogFormat == affected) {
                if (isTracklogGpx) {
                    appendTracklogGpxOptions();
                }
            }

        } else if (affected == choiceSnapshotFormat) {
            if (Camera.type == Camera.TYPE_JSR135) {
                final int i0 = choiceSnapshotFormat.getSelectedIndex();
                final String s0 = choiceSnapshotFormat.getString(i0);
                fieldSnapshotFormat.setString(s0);
            }
        }
        
    }

    public void commandAction(Command command, Displayable displayable) {
        // top-level menu action?
        if (displayable == pane) {
            // open submenu?
            if (List.SELECT_COMMAND == command) {
                // submenu
                try {
                    show(section = pane.getString(pane.getSelectedIndex()));
//#ifdef __ANDROID__
                } catch (ArrayIndexOutOfBoundsException e) {
                    // no item selected - ignore
//#endif
                } catch (Throwable t) {
                    Desktop.showError("Show menu error", t, null);
                }
            } else {
                // restore desktop
                Desktop.restore(displayable);

                // main menu action
                mainMenuCommandAction(command);
            }
        } else {
            // grab changes
            try {
                subMenuCommandAction(command);
            } catch (Throwable t) {
                Desktop.showError("Submenu action error", t, pane);
            }
        }
    }

    public void commandAction(Command command, Item item) {
        (new LineCfgForm()).show();
    }

    private void tracklogStateChanged(final int provider, boolean isTracklogGpx) {
        switch (provider) {
            case Config.LOCATION_PROVIDER_JSR82:
            case Config.LOCATION_PROVIDER_JSR179:
            case Config.LOCATION_PROVIDER_SERIAL:
//#ifdef __ALL__
            case Config.LOCATION_PROVIDER_HGE100:
//#endif
                appendWithNewlineAfter(submenu, choiceTracklogFormat);
            break;
            default: // GPX always avaialable
                isTracklogGpx = true;
        }
        if (isTracklogGpx) {
            appendTracklogGpxOptions();
        }
    }

    private void appendTracklogGpxOptions() {
        appendWithNewlineAfter(submenu, choiceGpx);
        appendShrinked(submenu, fieldGpxDt);
        appendWithNewlineAfter(submenu, fieldGpxDs);
    }

    private void subMenuCommandAction(Command command) {

        // grab values on OK
        if (command.getCommandType() != Desktop.BACK_CMD_TYPE) {

            if (menuBasic.equals(section)) {

                // map path
                if (File.isFs()) {
                    Config.mapURL = fieldMapPath.getString();
                }

                // datum
                Config.geoDatum = choiceMapDatum.getString(choiceMapDatum.getSelectedIndex());

                // coordinates format
                Config.cfmt = choiceCoordinates.getSelectedIndex();

                // units format
                Config.units = choiceUnits.getSelectedIndex();

                // startup screen
                Config.startupScreen = choiceScreen.getSelectedIndex();

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
                if (fieldSimulatorDelay != null) {
                    Config.simulatorDelay = Integer.parseInt(fieldSimulatorDelay.getString());
                }
                if (cz.kruch.track.TrackingMIDlet.jsr179 || cz.kruch.track.TrackingMIDlet.motorola179) {
//#ifdef __RIM__
                    final boolean[] bbopts = new boolean[choiceInternal.size()];
                    choiceInternal.getSelectedFlags(bbopts);
                    Config.assistedGps = bbopts[0];
                    Config.negativeAltFix = bbopts[1];
//#elifdef __ANDROID__
                    // nothing for Android
//#else
                    final boolean[] iopts = new boolean[choiceInternal.size()];
                    choiceInternal.getSelectedFlags(iopts);
                    Config.assistedGps = iopts[0];
                    Config.timeFix = iopts[1];
//#endif
//#ifndef __ANDROID__
                    Config.powerUsage = choicePower.getSelectedIndex();
                    Config.setLocationTimings(fieldLocationTimings.getString());
//#endif
                    Config.altCorrection = Float.parseFloat(fieldAltCorrection.getString());
                }
                if (fieldBtKeepalive != null) {
                    Config.btKeepAlive = Integer.parseInt(fieldBtKeepalive.getString());
                    if (Config.btKeepAlive > 0 && Config.btKeepAlive < 250) {
                        Config.btKeepAlive = 250;
                    }
                }
                if (fieldCommUrl != null) {
                    Config.commUrl = fieldCommUrl.getString();
                }
//#ifdef __ALL__
                if (fieldO2Depth != null) {
                    Config.o2Depth = Integer.parseInt(fieldO2Depth.getString());
                }
//#endif                

                // stream options
                if (choiceStream != null)  {
                    final boolean[] nopts = new boolean[choiceStream.size()];
                    choiceStream.getSelectedFlags(nopts);
                    Config.nmeaMsExact = nopts[0];
                    Config.reliableInput = nopts[1];
                    if (choiceStream.size() == 4) {
                        Config.btDoServiceSearch = nopts[2];
                        Config.btAddressWorkaround = nopts[3];
                    }
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
//                Config.routeLineColor = rl[1] ? 0x00FF0000 : 0x0;
//                Config.routePoiMarks = rl[2];
                Config.routePoiMarks = rl[1];
                Config.routeColor = itemLineColor;
                Config.routeThick = itemLineThick;

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
                Config.noQuestions = misc[3];
                Config.uiNoCommands = misc[4];
                Config.uiNoItemCommands = misc[5];
                Config.trailOn = misc[6];
                Config.decimalPrecision = misc[7];
                Config.hpsWptTrueAzimuth = misc[8];
                Config.easyZoomVolumeKeys = misc[9];
                Config.osdBasic = misc[10];
                Config.osdExtended = misc[11];
                Config.osdScale = misc[12];
                Config.osdNoBackground = misc[13];
                Config.osdBoldFont = misc[14];
                Config.osdBlackColor = misc[15];
                if (Desktop.screen.hasPointerEvents()) {
                    Config.zoomSpotsMode = choiceZoomSpots.getSelectedIndex();
                    Config.guideSpotsMode = choiceGuideSpots.getSelectedIndex();
                }
                Config.easyZoomMode = choiceEasyzoom.getSelectedIndex();
                Config.desktopFontSize = gaugeDesktopFont.getValue();
                Config.osdAlpha = gaugeOsdAlpha.getValue() * gaugeAlphaScale;
                Config.cmsCycle = Integer.parseInt(fieldCmsCycle.getString());
                Config.listFont = Integer.parseInt(fieldListFont.getString(), 16);
                Config.trailColor = itemLineColor;
                Config.trailThick = itemLineThick;
                changed = true;

            } else if (menuMisc.equals(section)) {

                // performance
                final boolean[] perf = new boolean[choicePerformance.size()];
                choicePerformance.getSelectedFlags(perf);
                Config.siemensIo = perf[0];
                Config.lowmemIo = perf[1];
                Config.S60renderer = perf[2];
                Config.forcedGc = perf[3];
                Config.powerSave = perf[4];
                Config.oneTileScroll = perf[5];
                Config.largeAtlases = perf[6];
                Config.lazyGpxParsing = perf[7];
                Config.numericInputHack = perf[8];
                Config.externalConfigBackup = perf[9];
                if (cz.kruch.track.TrackingMIDlet.symbian) {
                    Config.useNativeService = perf[10];
                }

                // multimedia
                if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                    Config.captureLocator = fieldCaptureLocator.getString();
                    Config.snapshotFormatIdx = choiceSnapshotFormat.getSelectedIndex();
                    Config.snapshotFormat = fieldSnapshotFormat.getString().trim();
                }
            }
        }

        // gc hint
/* causes crash on Symbian^3 phones
        submenu.setCommandListener(null);
        submenu.deleteAll();
*/
        submenu = null;

        // restore top-level menu
        Desktop.display.setCurrent(pane);
    }

    private void mainMenuCommandAction(Command command) {

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
        event.invoke(new Boolean(changed), null, this);
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
            if (cz.kruch.track.TrackingMIDlet.jsr179) {
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
            if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                providers.addElement(new Integer(Config.LOCATION_PROVIDER_O2GERMANY));
            }
//#endif
        }

        return providers;
    }

    private ChoiceGroup createStreamChoice(final int provider, final String providerName) {
        choiceStream = new ChoiceGroup(providerName, ChoiceGroup.MULTIPLE);
        choiceStream.setSelectedIndex(choiceStream.append(Resources.getString(Resources.CFG_LOCATION_FLD_NMEA_MS_ROUNDING), null), Config.nmeaMsExact);
        choiceStream.setSelectedIndex(choiceStream.append(Resources.getString(Resources.CFG_TWEAKS_FLD_RELIABLE_INPUT), null), Config.reliableInput);
        if (provider == Config.LOCATION_PROVIDER_JSR82) {
            choiceStream.setSelectedIndex(choiceStream.append(Resources.getString(Resources.CFG_LOCATION_FLD_DO_SERVICE_SEARCH), null), Config.btDoServiceSearch);
            choiceStream.setSelectedIndex(choiceStream.append(Resources.getString(Resources.CFG_LOCATION_FLD_ADDRESS_FIX), null), Config.btAddressWorkaround);
        }
        return choiceStream;
    }

    private int itemLineColor;
    private int itemLineThick;

    private String itemLineLabel;
    private Image itemLineImage;

    private Item createLineCfgItem(final String label, final int color, final int thickness) {
        itemLineColor = color;
        itemLineThick = thickness;
        itemLineLabel = label;
        itemLineImage = Image.createImage((int) ((float)submenu.getWidth() / 3),
                                          3 + 7 + 3); // space + max trail thickness + space
        itemLinePaint();
//#ifdef __RIM__
//        itemLineCfg = new TrailItem(label);
        itemLineCfg = new StringItem(label, "<no preview>", Item.BUTTON);
//#else
        itemLineCfg = new ImageItem(label, itemLineImage, Item.LAYOUT_DEFAULT, "<no preview>", Item.BUTTON);
//#endif
        itemLineCfg.setDefaultCommand(new Command("Edit", Command.ITEM, 1));
        itemLineCfg.setItemCommandListener(this);
        return itemLineCfg;
    }
    
    private void itemLinePaint() {
        final Graphics graphics = itemLineImage.getGraphics();
        final int w = itemLineImage.getWidth();
        final int h = itemLineImage.getHeight();
        graphics.setColor(0x40ffffff);
        graphics.fillRect(0, 0, w, h);
        graphics.setColor(Config.COLORS_16[itemLineColor]);
        graphics.fillRect(3, /*3 + 3*/h / 2 - itemLineThick, w - 3 - 3, itemLineThick * 2 + 1);
    }

    private final class LineCfgForm implements CommandListener, ItemStateListener {
        private Gauge gaugeColor, gaugeThickness;
        private int idx;

        public LineCfgForm() {
        }

        public void show() {
            final Form form = new Form(itemLineLabel);
            form.append(gaugeColor = new Gauge(Resources.getString(Resources.CFG_DESKTOP_FLD_COLOR), true, 15, itemLineColor));
            form.append(gaugeThickness = new Gauge(Resources.getString(Resources.CFG_DESKTOP_FLD_THICKNESS), true, 2, itemLineThick));
            form.append(new Spacer(form.getWidth(), 7));
            idx = form.append(itemLineImage);
            form.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
            form.setItemStateListener(this);
            form.setCommandListener(this);
            Desktop.display.setCurrent(form);
        }

        public void itemStateChanged(Item item) {
            if (item == gaugeColor) {
                itemLineColor = gaugeColor.getValue();
            } else if (item == gaugeThickness) {
                itemLineThick = gaugeThickness.getValue();
            }
            itemLinePaint();
            final Form form = (Form) Desktop.display.getCurrent();
            form.delete(idx);
            idx = form.append(itemLineImage);
        }

        public void commandAction(Command command, Displayable displayable) {
            if (itemLineCfg instanceof ImageItem) {
                ((ImageItem) itemLineCfg).setImage(itemLineImage);
            }
            Desktop.display.setCurrent(submenu);
        }
    }
}
