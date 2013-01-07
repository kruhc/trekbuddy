// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.util.NakedVector;
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

//#ifdef __HECL__
import cz.kruch.track.hecl.PluginManager;
//#endif

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
//#ifdef __HECL__
    private final String menuPlugins;
//#endif
    private final String menuMisc;

    private final Desktop.Event event;

    private ChoiceGroup choice1, choice2, choice3, choice4, choice5, choice6, choice7;
    private ChoiceGroup choiceProvider;
    private ChoiceGroup choiceStream;
    private ChoiceGroup choiceInternal;
    private ChoiceGroup choicePower;
    private ChoiceGroup choiceTracklog;
    private ChoiceGroup choiceTracklogFormat;
    private ChoiceGroup choiceGpx;
    private ChoiceGroup choiceSnapshotFormat;
    private TextField field1, field2, field3;
    private TextField fieldCaptureLocator;
    private TextField fieldSnapshotFormat;
    private TextField fieldSimulatorDelay;
    private TextField fieldLocationTimings;
    private TextField fieldCommUrl;
    private TextField fieldBtKeepalive;
    private TextField fieldO2Depth;
    private TextField fieldGpxDt;
    private TextField fieldGpxDs;
    private TextField fieldAltCorrection;
    private Gauge gauge1, gauge2;
    private Item itemLineCfg;

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
//#ifdef __HECL__
        this.menuPlugins = Resources.getString(Resources.CFG_ITEM_PLUGINS);
//#endif
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
//#ifdef __HECL__
        if (PluginManager.getInstance().size() > 0) {
            pane.append(menuPlugins, null);
        }
//#endif
        pane.append(menuMisc, null);

        // add command and handling
        pane.addCommand(new Command(Resources.getString(Resources.CFG_CMD_SAVE), Command.SCREEN, 1));
        pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
        /* default SELECT command is of SCREEN type */
        pane.setCommandListener(this);

        // show
        Desktop.display.setCurrent(pane);
    }

    private void show(final String section) {
        // submenu form
        submenu = new Form(Resources.prefixed(section));

        if (menuBasic.equals(section)) {

            // default map path field
            if (File.isFs()) {
                submenu.append(field1 = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_START_MAP),
                                                      Config.mapURL, MAX_URL_LENGTH, TextField.URL));
            }

            // map datum
            choice1 = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_DEFAULT_DATUM),
                                      Desktop.CHOICE_POPUP_TYPE);
            choice1.setFitPolicy(Choice.TEXT_WRAP_ON);
            for (int N = Config.datums.size(), i = 0; i < N; i++) {
                final String id = ((Datum) Config.datums.elementAt(i)).name;
                choice1.setSelectedIndex(choice1.append(id, null), Config.geoDatum.equals(id));
            }
            submenu.append(choice1);

            // coordinates format
            choice2 = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_COORDS_FMT),
                                      Desktop.CHOICE_POPUP_TYPE);
            choice2.setFitPolicy(Choice.TEXT_WRAP_ON);
            choice2.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPLL), null);
            choice2.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_MAPGRID), null);
            choice2.append(Resources.getString(Resources.CFG_BASIC_FLD_COORDS_GCLL), null);
            choice2.append(ProjectionSetup.PROJ_UTM, null);
            // TODO: add BNG, IG, SG, SUI
            choice2.setSelectedIndex(Config.cfmt, true);
            submenu.append(choice2);

            // units format
            choice3 = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_UNITS),
                                      Desktop.CHOICE_POPUP_TYPE);
            choice3.setFitPolicy(Choice.TEXT_WRAP_ON);
            choice3.append(Resources.getString(Resources.CFG_BASIC_FLD_UNITS_METRIC), null);
            choice3.append(Resources.getString(Resources.CFG_BASIC_FLD_UNITS_IMPERIAL), null);
            choice3.append(Resources.getString(Resources.CFG_BASIC_FLD_UNITS_NAUTICAL), null);
            choice3.setSelectedIndex(Config.units, true);
            submenu.append(choice3);

            // startup screen
            choice4 = new ChoiceGroup(Resources.getString(Resources.CFG_BASIC_GROUP_START_SCREEN),
                                      Desktop.CHOICE_POPUP_TYPE);
            choice4.setFitPolicy(Choice.TEXT_WRAP_ON);
            choice4.append(Resources.getString(Resources.CFG_BASIC_FLD_SCREEN_MAP), null);
            choice4.append(Resources.getString(Resources.CFG_BASIC_FLD_SCREEN_HPS), null);
            choice4.append(Resources.getString(Resources.CFG_BASIC_FLD_SCREEN_CMS), null);
            choice4.setSelectedIndex(Config.startupScreen, true);
            submenu.append(choice4);

            // datadir
            if (File.isFs()) {
                submenu.append(field2 = new TextField(Resources.getString(Resources.CFG_BASIC_FLD_DATA_DIR),
                               Config.getDataDir(), MAX_URL_LENGTH, TextField.URL));
            }

        } else if (menuDesktop.equals(section)) {

            // desktop settings
            choice1 = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP), ChoiceGroup.MULTIPLE);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_FULLSCREEN), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_SAFE_COLORS), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_SOUNDS), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_QUESTIONS), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_COMMANDS), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_NO_ITEM_COMMANDS), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAJECTORY), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_DEC_PRECISION), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HPS_WPT_TRUE_AZI), null);
            choice1.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_VOLUME), null);
            choice1.setSelectedFlags(new boolean[] {
                Config.fullscreen,
                Config.safeColors,
                Config.noSounds,
                Config.noQuestions,
                Config.uiNoCommands,
                Config.uiNoItemCommands,
                Config.trailOn,
                Config.decimalPrecision,
                Config.hpsWptTrueAzimuth,
                Config.easyZoomVolumeKeys
            });
            submenu.append(choice1);

            // OSD settings
            choice2 = new ChoiceGroup("OSD", ChoiceGroup.MULTIPLE);
            choice2.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BASIC), null);
            choice2.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_EXT), null);
            choice2.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_SCALE), null);
            choice2.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_NO_BG), null);
            choice2.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BOLD_FONT), null);
            choice2.append(Resources.getString(Resources.CFG_DESKTOP_FLD_OSD_BLACK), null);
            choice2.setSelectedFlags(new boolean[] {
                Config.osdBasic,
                Config.osdExtended,
                Config.osdScale,
                Config.osdNoBackground,
                Config.osdBoldFont,
                Config.osdBlackColor
            });
            submenu.append(choice2);

            // OSD transparency
            int alphaSteps = Desktop.display.numAlphaLevels();
            if (alphaSteps > 16) {
                alphaSteps = 16;
            }
            gaugeAlphaScale = 0x100 / alphaSteps;
            final int value = Config.osdAlpha / gaugeAlphaScale;
            submenu.append(gauge2 = new Gauge(Resources.getString(Resources.CFG_DESKTOP_TRANSPARENCY),
                                              true, alphaSteps, value));

            // map prescale
            submenu.append(field3 = new TextField(Resources.getString(Resources.CFG_DESKTOP_FLD_MAP_PRESCALE) + " (100% - 270%)",
                                                  Integer.toString(Config.prescale),
                                                  3, /*TextField.*/NUMERIC));

            // icons appearance
            if (Desktop.screen.hasPointerEvents()) {
                choice3 = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_ZOOM_SPOTS),
                                          Desktop.CHOICE_POPUP_TYPE);
                choice3.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null);
                choice3.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null);
                choice3.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HIDE_AFTER) + " 3s", null);
                choice3.setSelectedIndex(Config.zoomSpotsMode, true);
                submenu.append(choice3);
                choice4 = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_GUIDE_SPOTS),
                                          Desktop.CHOICE_POPUP_TYPE);
                choice4.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null);
                choice4.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null);
                choice4.append(Resources.getString(Resources.CFG_DESKTOP_FLD_HIDE_AFTER) + " 3s", null);
                choice4.setSelectedIndex(Config.guideSpotsMode, true);
                submenu.append(choice4);
            }

            // easyzoom
            choice5 = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_EASYZOOM),
                                      Desktop.CHOICE_POPUP_TYPE);
            choice5.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_OFF), null);
            choice5.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_LAYERS), null);
//            choice5.append(Resources.getString(Resources.CFG_DESKTOP_FLD_EASYZOOM_MAPS), null);
            choice5.setSelectedIndex(Config.easyZoomMode, true);
            submenu.append(choice5);

            // desktop font
            submenu.append(gauge1 = new Gauge(Resources.getString(Resources.CFG_DESKTOP_FLD_FONT_SIZE), true,
                                              2, Config.desktopFontSize));

            // list font
            final StringBuffer hexstr = new StringBuffer(Integer.toHexString(Config.listFont));
            while (hexstr.length() < 6) {
                hexstr.insert(0, '0');
            }
            submenu.append(field1 = new TextField(Resources.getString(Resources.CFG_DESKTOP_FLD_LIST_FONT),
                                                  hexstr.toString(), 10, TextField.ANY));

            // list mode
            if (Desktop.screen.hasPointerEvents()) {
                choice6 = new ChoiceGroup(Resources.getString(Resources.CFG_DESKTOP_GROUP_LIST_MODE),
                                          Desktop.CHOICE_POPUP_TYPE);
                choice6.append(Resources.getString(Resources.CFG_DESKTOP_FLD_MODE_DEFAULT), null);
                choice6.append(Resources.getString(Resources.CFG_DESKTOP_FLD_MODE_CUSTOM), null);
                choice6.setSelectedIndex(Config.extListMode, true);
                submenu.append(choice6);
            }

            // CMS cycling
            submenu.append(field2 = new TextField(Resources.getString(Resources.CFG_DESKTOP_FLD_CMS_CYCLE),
                                                  Integer.toString(Config.cmsCycle), 4, /*TextField.*/NUMERIC));

            // trail line
            submenu.append(itemLineCfg = createLineCfgItem(Resources.getString(Resources.CFG_DESKTOP_FLD_TRAIL_PREVIEW),
                                                           Config.trailColor, Config.trailThick));
            submenu.append(new Spacer(submenu.getWidth(), 1));

        } else if (menuNavigation.equals(section)) {

            // proximity
            submenu.append(field1 = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_WPT_PROXIMITY), Integer.toString(Config.wptProximity), 5, /*TextField.*/NUMERIC));
            /*append(*/field2 = new TextField(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_PROXIMITY), Integer.toString(Config.poiProximity), 5, /*TextField.*/NUMERIC)/*)*/;

            // route line
            choice1 = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_ROUTE_LINE), ChoiceGroup.MULTIPLE);
            choice1.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_DOTTED), null);
//            choice1.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_RED), null);
            choice1.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_POI_ICONS), null);
            choice1.setSelectedFlags(new boolean[] {
                Config.routeLineStyle,
//                Config.routeLineColor != 0,
                Config.routePoiMarks
            });
            submenu.append(choice1);

            // route line
            submenu.append(itemLineCfg = createLineCfgItem(Resources.getString(Resources.CFG_NAVIGATION_GROUP_ROUTE_LINE),
                                                           Config.routeColor, Config.routeThick));
            submenu.append(new Spacer(submenu.getWidth(), 1));

            // waypoints
            choice2 = new ChoiceGroup(Resources.getString(Resources.NAV_ITEM_WAYPOINTS), ChoiceGroup.MULTIPLE);
            choice2.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_REVISIONS), null);
            choice2.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_PREFER_GSNAME), null);
            choice2.setSelectedFlags(new boolean[] {
                Config.makeRevisions,
                Config.preferGsName
            });
            submenu.append(choice2);

            // wpts sorting
            choice3 = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_SORT), Desktop.CHOICE_POPUP_TYPE);
            choice3.setFitPolicy(Choice.TEXT_WRAP_ON);
            choice3.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_SORT_BYPOS), null);
            choice3.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_SORT_BYNAME), null);
            choice3.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_SORT_BYDIST), null);
            choice3.setSelectedIndex(Config.sort, true);
            submenu.append(choice3);

            // 'Friends'
            if (cz.kruch.track.TrackingMIDlet.jsr120) {
                choice4 = new ChoiceGroup(Resources.getString(Resources.CFG_NAVIGATION_GROUP_SMS), ChoiceGroup.MULTIPLE);
                choice4.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_RECEIVE), null);
                choice4.append(Resources.getString(Resources.CFG_NAVIGATION_FLD_AUTOHIDE), null);
                choice4.setSelectedFlags(new boolean[] {
                    Config.locationSharing,
                    Config.autohideNotification
                });
                submenu.append(choice4);
            }
            
        } else if (menuMisc.equals(section)) {

            // tweaks
            choice1 = new ChoiceGroup(Resources.getString(Resources.CFG_TWEAKS_GROUP), ChoiceGroup.MULTIPLE);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SIEMENS_IO), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LOWMEM_IO), null);
//            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_SAFE_RENDERER), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_FORCED_GC), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_POWER_SAVE), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_1TILE_SCROLL), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LARGE_ATLASES), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_LAZY_GPX), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_NUMERIC_INPUT_HACK), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_EXTERNAL_CFG_BACKUP), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_FILTERED_SCALING), null);
            choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_VERBOSE_LOADING), null);
            choice1.setSelectedFlags(new boolean[] {
                Config.siemensIo,
                Config.lowmemIo,
//                Config.S60renderer,
                Config.forcedGc,
                Config.powerSave,
                Config.oneTileScroll,
                Config.largeAtlases,
                Config.lazyGpxParsing,
                Config.numericInputHack,
                Config.externalConfigBackup,
                Config.tilesScaleFiltered,
                Config.verboseLoading
            });
//#ifndef __ANDROID__
            choice1.setSelectedIndex(choice1.append("S40 ticker hack", null),
                                     Config.s40ticker);
//#endif
//#ifdef __SYMBIAN__
            choice1.setSelectedIndex(choice1.append(Resources.getString(Resources.CFG_TWEAKS_FLD_USE_TBSVC), null),
                                     Config.useNativeService);
//#endif
            submenu.append(choice1);

            // HECL
            choice2 = new ChoiceGroup(Resources.getString(Resources.CFG_TWEAKS_HECL_GROUP),
                                      Desktop.CHOICE_POPUP_TYPE);
            choice2.append("0", null);
            choice2.append("1", null);
            choice2.append("2", null);
            choice2.setSelectedIndex(Config.heclOpt, true);
            submenu.append(choice2);

            // capture device and format
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
                choiceTracklog = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_TRACKLOG),
                                                 ChoiceGroup.EXCLUSIVE);
                choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_NEVER), null);
                if (Config.dataDirExists) {
                    choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ASK), null);
                    choiceTracklog.append(Resources.getString(Resources.CFG_LOCATION_FLD_TRACKLOG_ALWAYS), null);
                }
                choiceTracklog.setSelectedIndex(Config.tracklog, true);

                choiceTracklogFormat = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_TRACKLOG_FMT),
                                                       ChoiceGroup.EXCLUSIVE);
                choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_GPX, null),
                                                      Config.TRACKLOG_FORMAT_GPX.equals(Config.tracklogFormat));
                choiceTracklogFormat.setSelectedIndex(choiceTracklogFormat.append(Config.TRACKLOG_FORMAT_NMEA, null),
                                                      Config.TRACKLOG_FORMAT_NMEA.equals(Config.tracklogFormat));

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
                fieldCommUrl = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_CONN_URL),
                                             Config.commUrl, 64, TextField.ANY);
            }
            if (File.isFs()) {
                fieldSimulatorDelay = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_SIMULATOR_DELAY),
                                                    Integer.toString(Config.simulatorDelay), 8, /*TextField.*/NUMERIC);
            }
            if (cz.kruch.track.TrackingMIDlet.jsr82) {
                fieldBtKeepalive = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_BT_KEEP_ALIVE),
                                                 Integer.toString(Config.btKeepAlive), 6, /*TextField.*/NUMERIC);
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
                choiceInternal = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_FLD_PROV_INTERNAL),
                                                 ChoiceGroup.MULTIPLE);
                choiceInternal.append(Resources.getString(Resources.CFG_LOCATION_FLD_ASSISTED_GPS), null);
                choiceInternal.append(Resources.getString(Resources.CFG_LOCATION_FLD_TIME_FIX), null);
                choiceInternal.setSelectedFlags(new boolean[] {
                    Config.assistedGps,
                    Config.timeFix
                });
//#endif
//#ifndef __ANDROID__
                choicePower = new ChoiceGroup(Resources.getString(Resources.CFG_LOCATION_GROUP_POWER),
                                              Desktop.CHOICE_POPUP_TYPE);
                choicePower.append("NO_REQUIREMENT", null);
                choicePower.append("LOW", null);
                choicePower.append("MEDIUM", null);
                choicePower.append("HIGH", null);
                choicePower.setSelectedIndex(Config.powerUsage, true);
                fieldLocationTimings = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_LOCATION_TIMINGS),
                                                     Config.getLocationTimings(Config.locationProvider), 12, TextField.ANY);
//#else
                fieldLocationTimings = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_LOCATION_TIMINGS),
                                                     Config.getLocationTimings(Config.locationProvider), 4, TextField.NUMERIC);
//#endif                
                fieldAltCorrection = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_ALT_CORRECTION),
                                                   Float.toString(Config.altCorrection), 5, TextField.ANY/*DECIMAL*/);
            }
//#ifdef __ALL__
            if (cz.kruch.track.TrackingMIDlet.hasFlag("provider_o2_germany")) {
                fieldO2Depth = new TextField(Resources.getString(Resources.CFG_LOCATION_FLD_FILTER_DEPTH),
                                             Integer.toString(Config.o2Depth), 2, /*TextField.*/NUMERIC);
            }
//#endif            

            // show current provider and tracklog specific options
            itemStateChanged(choiceProvider);
            submenu.setItemStateListener(this);

//#ifdef __HECL__

        } else if (menuPlugins.equals(section)) {

            // same for all plugins // TODO move to PluginManager
            final NakedVector plugins = PluginManager.getInstance().getPlugins();
            final Object[] ps = plugins.getData();
            for (int i = 0, N = plugins.size(); i < N; i++) {
                final PluginManager.Plugin plugin = (PluginManager.Plugin) ps[i];
                final StringItem cfgBtn;
                if (plugin.hasOptions()) {
                    cfgBtn = new StringItem(null, Resources.getString(Resources.CFG_PLUGINS_CONFIGURE), Item.BUTTON);
                    cfgBtn.setDefaultCommand(new Command(Resources.getString(Resources.CFG_PLUGINS_CONFIGURE), Command.ITEM, i));
                    cfgBtn.setItemCommandListener(this);
                } else {
                    cfgBtn = new StringItem(null, Resources.getString(Resources.CFG_PLUGINS_NO_CONFIG), Item.PLAIN);
                }
                final ChoiceGroup cg = new ChoiceGroup(plugin.getFullName(), ChoiceGroup.MULTIPLE);
                cg.append(Resources.getString(Resources.CFG_PLUGINS_ENABLE), null);
                cg.setSelectedFlags(new boolean[]{ plugin.isEnabled() });
                submenu.append(cg);
                submenu.append(cfgBtn);
                submenu.append(new Spacer(submenu.getWidth(), 8));
            }
//#endif
        }

        // add command and handling
//#ifndef __ANDROID__
        submenu.addCommand(new Command(Resources.getString(Resources.CMD_OK), Desktop.POSITIVE_CMD_TYPE, 0));
//#endif
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
//#endif
                        /* different timings for Motorola and others, hence update... */
                        fieldLocationTimings.setString(Config.getLocationTimings(provider));
                        appendWithNewlineAfter(submenu, fieldLocationTimings);
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
//#ifdef __HECL__
        if (menuPlugins.equals(section))
            (new PluginForm(PluginManager.getInstance().getPlugin(command.getPriority()))).show();
        else
//#endif
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
//#ifndef __ANDROID__
        // grab values on OK
        if (command.getCommandType() != Desktop.BACK_CMD_TYPE) {
//#endif
            if (menuBasic.equals(section)) {

                // map path
                if (File.isFs()) {
                    Config.mapURL = getString(field1);
                }

                // datum
                Config.geoDatum = choice1.getString(choice1.getSelectedIndex());

                // coordinates format
                Config.cfmt = choice2.getSelectedIndex();

                // units format
                Config.units = choice3.getSelectedIndex();

                // startup screen
                Config.startupScreen = choice4.getSelectedIndex();

                // datadir
                if (File.isFs()) {
                    Config.setDataDir(getString(field2));
                }

            } else if (menuLocation.equals(section)) {

                // provider
                if (choiceProvider.size() > 0) {
                    Config.locationProvider = ((Integer) providers.elementAt(choiceProvider.getSelectedIndex())).intValue();
                }

                // provider-specific
                if (fieldSimulatorDelay != null) {
                    Config.simulatorDelay = getInt(fieldSimulatorDelay);
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
//#endif
                    Config.setLocationTimings(getString(fieldLocationTimings));
                    Config.altCorrection = Float.parseFloat(getString(fieldAltCorrection));
                }
                if (fieldBtKeepalive != null) {
                    Config.btKeepAlive = getInt(fieldBtKeepalive);
                    if (Config.btKeepAlive > 0 && Config.btKeepAlive < 250) {
                        Config.btKeepAlive = 250;
                    }
                }
                if (fieldCommUrl != null) {
                    Config.commUrl = getString(fieldCommUrl);
                }
//#ifdef __ALL__
                if (fieldO2Depth != null) {
                    Config.o2Depth = getInt(fieldO2Depth);
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
                    Config.gpxDt = getInt(fieldGpxDt);
                    Config.gpxDs = getInt(fieldGpxDs);
                }

            } else if (menuNavigation.equals(section)) {

                // proximity
                Config.wptProximity = getInt(field1);
                Config.poiProximity = getInt(field2);

                // route line
                final boolean[] rl = new boolean[choice1.size()];
                choice1.getSelectedFlags(rl);
                Config.routeLineStyle = rl[0];
//                Config.routeLineColor = rl[1] ? 0x00FF0000 : 0x0;
//                Config.routePoiMarks = rl[2];
                Config.routePoiMarks = rl[1];
                Config.routeColor = itemLineColor;
                Config.routeThick = itemLineThick;

                // waypoints
                final boolean[] wpts = new boolean[choice2.size()];
                choice2.getSelectedFlags(wpts);
                Config.makeRevisions = wpts[0];
                Config.preferGsName = wpts[1];

                // wpts sorting
                Config.sort = choice3.getSelectedIndex();

                // location sharing
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    final boolean[] friends = new boolean[choice4.size()];
                    choice4.getSelectedFlags(friends);
                    Config.locationSharing = friends[0];
                    Config.autohideNotification = friends[1];
                }

            } else if (menuDesktop.equals(section)) {

                // desktop
                final boolean[] desktop = new boolean[choice1.size()];
                choice1.getSelectedFlags(desktop);
                Config.fullscreen = desktop[0];
                Config.safeColors = desktop[1];
                Config.noSounds = desktop[2];
                Config.noQuestions = desktop[3];
                Config.uiNoCommands = desktop[4];
                Config.uiNoItemCommands = desktop[5];
                Config.trailOn = desktop[6];
                Config.decimalPrecision = desktop[7];
                Config.hpsWptTrueAzimuth = desktop[8];
                Config.easyZoomVolumeKeys = desktop[9];
                final boolean[] osd = new boolean[choice2.size()];
                choice2.getSelectedFlags(osd);
                Config.osdBasic = osd[0];
                Config.osdExtended = osd[1];
                Config.osdScale = osd[2];
                Config.osdNoBackground = osd[3];
                Config.osdBoldFont = osd[4];
                Config.osdBlackColor = osd[5];
                if (Desktop.screen.hasPointerEvents()) {
                    Config.zoomSpotsMode = choice3.getSelectedIndex();
                    Config.guideSpotsMode = choice4.getSelectedIndex();
                    Config.extListMode = choice6.getSelectedIndex();
                }
                Config.easyZoomMode = choice5.getSelectedIndex();
                Config.desktopFontSize = gauge1.getValue();
                Config.osdAlpha = gauge2.getValue() * gaugeAlphaScale;
                Config.listFont = Integer.parseInt(getString(field1), 16);
                Config.cmsCycle = getInt(field2);
                Config.prescale = getInt(field3);
                if (Config.prescale < 100 || Config.prescale > 270) {
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_INVALID_INPUT) + ": " + getString(field3), null, null);
                    return;
                }
                Config.trailColor = itemLineColor;
                Config.trailThick = itemLineThick;
                changed = true;

//#ifdef __HECL__

            } else if (menuPlugins.equals(section)) {

                // iterate over all
                for (int i = 0, N = submenu.size() / 3; i < N; i++) {

                    // get enabled status
                    final ChoiceGroup cg = (ChoiceGroup) submenu.get(i * 3);
                    final boolean[] enabled = new boolean[1];
                    cg.getSelectedFlags(enabled);
                    PluginManager.getInstance().getPlugin(i).setEnabled(enabled[0]);
                }

//#endif

            } else if (menuMisc.equals(section)) {

                // performance
                final boolean[] perf = new boolean[choice1.size()];
                choice1.getSelectedFlags(perf);
                Config.siemensIo = perf[0];
                Config.lowmemIo = perf[1];
//                Config.S60renderer = perf[2];
                Config.forcedGc = perf[2];
                Config.powerSave = perf[3];
                Config.oneTileScroll = perf[4];
                Config.largeAtlases = perf[5];
                Config.lazyGpxParsing = perf[6];
                Config.numericInputHack = perf[7];
                Config.externalConfigBackup = perf[8];
                Config.tilesScaleFiltered = perf[9];
                Config.verboseLoading = perf[10];
//#ifndef __ANDROID__
                Config.s40ticker = perf[11];
//#endif
//#ifdef __SYMBIAN__
                Config.useNativeService = perf[12];
//#endif

                // HECL
                Config.heclOpt = choice2.getSelectedIndex();

                // multimedia
                if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
                    Config.captureLocator = getString(fieldCaptureLocator);
                    Config.snapshotFormatIdx = choiceSnapshotFormat.getSelectedIndex();
                    Config.snapshotFormat = getString(fieldSnapshotFormat);
                }
            }
//#ifndef __ANDROID__
        }
//#endif

        // gc hint
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

//#ifdef __HECL__
                // update plugins configurations
                PluginManager.getInstance().saveConfiguration();
//#endif

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

    private static String getString(final TextField field) {
        return field.getString().trim();
    }

    private static int getInt(final TextField field) {
        return Integer.parseInt(field.getString().trim());
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

//#ifdef __HECL__

    private final class PluginForm implements CommandListener {

        private PluginManager.Plugin plugin;

        public PluginForm(PluginManager.Plugin plugin) {
            this.plugin = plugin;
        }

        public void show() {
            try {
                final Form form = new Form(plugin.getFullName());
                plugin.appendOptions(form);
                form.addCommand(new Command(Resources.getString(Resources.CMD_OK), Desktop.POSITIVE_CMD_TYPE, 0));
                form.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
                form.setCommandListener(this);
                Desktop.display.setCurrent(form);
            } catch (Throwable t) {
                Desktop.showError("Error showing plugin configuration tab", t, submenu);
            }
        }

        public void commandAction(Command command, Displayable displayable) {
            if (command.getCommandType() != Desktop.BACK_CMD_TYPE) {
                plugin.grabOptions((Form) displayable);
            }
            Desktop.display.setCurrent(submenu);
        }
    }

//#endif

}
