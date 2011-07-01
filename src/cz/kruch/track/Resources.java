// @LICENSE@

package cz.kruch.track;

import api.io.BufferedInputStream;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.util.CharArrayTokenizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.EOFException;

/**
 * Resource helper (L10n).
 *
 * @author kruhc@seznam.cz
 */
public final class Resources {
    /* common commands */
    public static final short CMD_OK                            = 0;
    public static final short CMD_BACK                          = 1;
    public static final short CMD_CLOSE                         = 2;
    public static final short CMD_CANCEL                        = 3;
    public static final short CMD_YES                           = 4;
    public static final short CMD_NO                            = 5;
    /* vendor */
//#ifdef __B2B__
    public static final short VENDOR_INITIAL_SCREEN             = 100;
    public static final short VENDOR_INITIAL_MAP                = 101;
    public static final short VENDOR_INITIAL_DATADIR            = 102;
    public static final short VENDOR_INITIAL_CHECKSUM           = 103;
    public static final short VENDOR_INITIAL_NAVI_SOURCE        = 104;
    public static final short VENDOR_INITIAL_NAVI_CMD           = 105;
    public static final short VENDOR_CORRUPTED_MAP              = 106;
    public static final short VENDOR_CMD_LOAD_GUIDE             = 107;
    public static final short VENDOR_MSG_SELECT_GUIDE           = 108;
    public static final short VENDOR_CMD_OPEN_GUIDE             = 109;
    public static final short VENDOR_INITIAL_CMS                = 110;
    public static final short VENDOR_TRAIL_OPTS                 = 111;
    public static final short VENDOR_ROUTE_OPTS                 = 112;
//#endif
    /* boot - messages */
    public static final short BOOT_CACHING_IMAGES               = 300;
    public static final short BOOT_LOADING_CFG                  = 301;
    public static final short BOOT_CUSTOMIZING                  = 302;
    public static final short BOOT_CREATING_UI                  = 303;
    public static final short BOOT_LOADING_MAP                  = 304;
    public static final short BOOT_KEYMAP                       = 305;
    public static final short BOOT_L10N                         = 306;
    public static final short BOOT_LOCAL_COPY                   = 399;
    /* desktop - commands */
    public static final short DESKTOP_CMD_START                 = 1000;
    public static final short DESKTOP_CMD_STOP                  = 1001;
    public static final short DESKTOP_CMD_PAUSE                 = 1002;
    public static final short DESKTOP_CMD_CONTINUE              = 1003;
    public static final short DESKTOP_CMD_LOAD_MAP              = 1004;
    public static final short DESKTOP_CMD_LOAD_ATLAS            = 1005;
    public static final short DESKTOP_CMD_SETTINGS              = 1006;
    public static final short DESKTOP_CMD_NAVIGATION            = 1007;
    public static final short DESKTOP_CMD_INFO                  = 1008;
    public static final short DESKTOP_CMD_EXIT                  = 1009;
    public static final short DESKTOP_CMD_REFRESH               = 1010;
    public static final short DESKTOP_CMD_CONNECT               = 1011;
    public static final short DESKTOP_CMD_OPEN                  = 1012;
    public static final short DESKTOP_CMD_SELECT                = 1013;
    /* desktop - messages */
    public static final short DESKTOP_MSG_FRIENDS_FAILED        = 1300;
    public static final short DESKTOP_MSG_WANT_QUIT             = 1301;
    public static final short DESKTOP_MSG_WPT_OFF_LAYER         = 1302;
    public static final short DESKTOP_MSG_WPT_OFF_MAP           = 1303;
    public static final short DESKTOP_MSG_KEYS_LOCKED           = 1304;
    public static final short DESKTOP_MSG_KEYS_UNLOCKED         = 1305;
    public static final short DESKTOP_MSG_START_TRACKLOG        = 1306;
    public static final short DESKTOP_MSG_CREATE_PROV_FAILED    = 1307;
    public static final short DESKTOP_MSG_START_PROV_FAILED     = 1308;
    public static final short DESKTOP_MSG_STOP_PROV_FAILED      = 1309;
    public static final short DESKTOP_MSG_NAV_STOPPED           = 1310;
    public static final short DESKTOP_MSG_WPT_SET               = 1311;
    public static final short DESKTOP_MSG_NO_LAYERS             = 1312;
    public static final short DESKTOP_MSG_NO_MAPS               = 1313;
    public static final short DESKTOP_MSG_NO_MAP_FOR_POS        = 1314;
    public static final short DESKTOP_MSG_LOADING_MAP           = 1315;
    public static final short DESKTOP_MSG_INIT_MAP              = 1316;
    public static final short DESKTOP_MSG_LOADING_ATLAS         = 1317;
    public static final short DESKTOP_MSG_CFG_UPDATED           = 1318;
    public static final short DESKTOP_MSG_CFG_UPDATE_FAILED     = 1319;
    public static final short DESKTOP_MSG_TRACKLOG_ERROR        = 1320;
    public static final short DESKTOP_MSG_USE_MAP_FAILED        = 1321;
    public static final short DESKTOP_MSG_LOAD_MAP_FAILED       = 1322;
    public static final short DESKTOP_MSG_SLICES_LOADED         = 1323;
    public static final short DESKTOP_MSG_LOADING_STATUS        = 1324;
    public static final short DESKTOP_MSG_SELECT_MAP            = 1325;
    public static final short DESKTOP_MSG_SELECT_ATLAS          = 1326;
    public static final short DESKTOP_MSG_SELECT_LAYER          = 1327;
    public static final short DESKTOP_MSG_UNKNOWN_SMS           = 1328;
    public static final short DESKTOP_MSG_SMS_SENT              = 1329;
    public static final short DESKTOP_MSG_SMS_SEND_FAILED       = 1330;
    public static final short DESKTOP_MSG_SMS_RECEIVE_FAILED    = 1331;
    public static final short DESKTOP_MSG_SERVICE_NOT_FOUND     = 1332;
    public static final short DESKTOP_MSG_SERVICE_SEARCH_FAILED = 1333;
    public static final short DESKTOP_MSG_NO_SERVICES_FOUND     = 1334;
    public static final short DESKTOP_MSG_DISC_RESTART_FAILED   = 1335;
    public static final short DESKTOP_MSG_START_TRACKLOG_FAILED = 1336;
    public static final short DESKTOP_MSG_LOAD_PROFILE_FAILED   = 1337;
    public static final short DESKTOP_MSG_NO_PROVIDER_INSTANCE  = 1338;
    public static final short DESKTOP_MSG_NO_DEFAULT_MAP        = 1339;
    public static final short DESKTOP_MSG_PAUSED                = 1340;
    public static final short DESKTOP_MSG_NO_CMS_PROFILES       = 1341;
    public static final short DESKTOP_MSG_PROFILES              = 1342;
    public static final short DESKTOP_MSG_EVENT_CLENAUP         = 1343;
    public static final short DESKTOP_MSG_PARSE_PROJ_FAILED     = 1344;
    public static final short DESKTOP_MSG_PARSE_DATUM_FAILED    = 1345;
    public static final short DESKTOP_MSG_PARSE_CAL_FAILED      = 1346;
    public static final short DESKTOP_MSG_PARSE_SET_FAILED      = 1347;
    public static final short DESKTOP_MSG_INVALID_ATLAS_URL     = 1348;
    public static final short DESKTOP_MSG_INVALID_MAP_URL       = 1349;
    public static final short DESKTOP_MSG_INVALID_SLICE_NAME    = 1350;
    public static final short DESKTOP_MSG_INVALID_MAP_DIMENSION = 1351;
    public static final short DESKTOP_MSG_INVALID_POINT         = 1352;
    public static final short DESKTOP_MSG_INVALID_PROJECTION    = 1353;
    public static final short DESKTOP_MSG_INVALID_MMPXY         = 1354;
    public static final short DESKTOP_MSG_INVALID_MMPLL         = 1355;
    public static final short DESKTOP_MSG_INVALID_IWH           = 1356;
    public static final short DESKTOP_MSG_MM_SIZE_MISMATCH      = 1357;
    public static final short DESKTOP_MSG_MAP_TOO_LARGE         = 1358;
    public static final short DESKTOP_MSG_TOO_FEW_CALPOINTS     = 1359;
    public static final short DESKTOP_MSG_NO_CALIBRATION        = 1360;
    public static final short DESKTOP_MSG_NO_SLICES             = 1361;
    public static final short DESKTOP_MSG_SLICE_LOAD_FAILED     = 1362;
    public static final short DESKTOP_MSG_NO_SLICE_IMAGE        = 1363;
    public static final short DESKTOP_MSG_UNKNOWN_CAL_FILE      = 1364;
    public static final short DESKTOP_MSG_SLICES_DIR_NOT_FOUND  = 1365;
    public static final short DESKTOP_MSG_SLICES_LIST_FAILED    = 1366;
    public static final short DESKTOP_MSG_SLICE_TOO_BIG         = 1367;
    public static final short DESKTOP_MSG_FILE_NOT_FOUND        = 1368;
    public static final short DESKTOP_MSG_USE_AS_DEFAULT_ATLAS  = 1369;
    public static final short DESKTOP_MSG_USE_AS_DEFAULT_MAP    = 1370;
    public static final short DESKTOP_MSG_SELECT_DEVICE         = 1371;
    public static final short DESKTOP_MSG_SEARCHING_DEVICES     = 1372;
    public static final short DESKTOP_MSG_SERVICE_SEARCH        = 1373;
    public static final short DESKTOP_MSG_SEARCHING_SERVICE     = 1374;
    public static final short DESKTOP_MSG_RESOLVING_NAMES       = 1375;
    public static final short DESKTOP_MSG_NO_DEVICES_DISCOVERED = 1376;
    public static final short DESKTOP_MSG_BT_OFF                = 1377;
    public static final short DESKTOP_MSG_BACKLIGHT_ON          = 1379;
    public static final short DESKTOP_MSG_BACKLIGHT_OFF         = 1380;
    public static final short DESKTOP_MSG_NO_POSITION           = 1381;
    public static final short DESKTOP_MSG_NO_WPT                = 1382;
    public static final short DESKTOP_MSG_NMEA_PLAYBACK         = 1383;
    public static final short DESKTOP_MSG_SMS_RECEIVED          = 1384;
    public static final short DESKTOP_MSG_NO_SET_FILE           = 1385;
    public static final short DESKTOP_MSG_PROV_OUT_OF_SERVICE   = 1386;
    public static final short DESKTOP_MSG_PROV_STARTING         = 1387;
    public static final short DESKTOP_MSG_IN_PROGRESS           = 1388;
    public static final short DESKTOP_MSG_PUSH_SMS_FAILED       = 1389;
    /* navigation - commands */
    public static final short NAV_CMD_ROUTE_ALONG               = 2000;
    public static final short NAV_CMD_ROUTE_BACK                = 2001;
    public static final short NAV_CMD_NAVIGATE_TO               = 2002;
    public static final short NAV_CMD_SET_AS_ACTIVE             = 2003;
    public static final short NAV_CMD_GO_TO                     = 2004;
    public static final short NAV_CMD_ADD                       = 2005;
    public static final short NAV_CMD_SAVE                      = 2006;
    public static final short NAV_CMD_TAKE                      = 2007;
    public static final short NAV_CMD_SEND                      = 2008;
    public static final short NAV_CMD_SHOW_ALL                  = 2009;
    public static final short NAV_CMD_HIDE_ALL                  = 2010;
    public static final short NAV_CMD_EDIT                      = 2011;
    public static final short NAV_CMD_UPDATE                    = 2012;
    public static final short NAV_CMD_DELETE                    = 2013;
    public static final short NAV_CMD_SHOW                      = 2014;
    public static final short NAV_CMD_SORT_BYORDER              = 2015;
    public static final short NAV_CMD_SORT_BYNAME               = 2016;
    public static final short NAV_CMD_SORT_BYDIST               = 2017;
    public static final short NAV_CMD_NEW_NOTE                  = 2018;
    /* navigation - menu */
    public static final short NAV_ITEM_WAYPOINTS                = 2100;
    public static final short NAV_ITEM_RECORD                   = 2101;
    public static final short NAV_ITEM_ENTER                    = 2102;
    public static final short NAV_ITEM_SMS_IAH                  = 2103;
    public static final short NAV_ITEM_SMS_MYT                  = 2104;
    public static final short NAV_ITEM_STOP                     = 2105;
    public static final short NAV_ITEM_TRACKS                   = 2106;
    public static final short NAV_ITEM_FIELD_NOTES              = 2107;
    /* navigation - messages */
    public static final short NAV_MSG_LOAD_INJAR_FAILED         = 2300;
    public static final short NAV_MSG_LIST_STORES_FAILED        = 2301;
    public static final short NAV_MSG_NO_STORES                 = 2302;
    public static final short NAV_MSG_LIST_STORE_FAILED         = 2303;
    public static final short NAV_MSG_NO_WPTS_FOUND_IN          = 2304;
    public static final short NAV_MSG_WPTS_LOADED               = 2305;
    public static final short NAV_MSG_NO_POS_YET                = 2306;
    public static final short NAV_MSG_NOT_TRACKING              = 2307;
    public static final short NAV_MSG_PERSIST_WPT               = 2308;
    public static final short NAV_MSG_WPT_RECORDED              = 2309;
    public static final short NAV_MSG_WPT_RECORD_ERROR          = 2310;
    public static final short NAV_MSG_WPT_ENLISTED              = 2311;
    public static final short NAV_MSG_TICKER_LISTING            = 2312;
    public static final short NAV_MSG_TICKER_LOADING            = 2313;
    public static final short NAV_MSG_WPT_ADD_TO                = 2314;
    public static final short NAV_MSG_ENTER_STORE_FILENAME      = 2315;
    public static final short NAV_MSG_SELECT_STORE              = 2316;
    public static final short NAV_MSG_CAMERA_FAILED             = 2400;
    public static final short NAV_MSG_NO_PREVIEW                = 2401;
    public static final short NAV_MSG_DO_NOT_WORRY              = 2402;
    public static final short NAV_MSG_SNAPSHOT_FAILED           = 2403;
    public static final short NAV_MSG_NO_SNAPSHOT               = 2404;
    public static final short NAV_MSG_MALFORMED_COORD           = 2405;
    public static final short NAV_MSG_STORE_UPDATED             = 2406;
    public static final short NAV_MSG_STORE_UPDATE_FAILED       = 2407;
    public static final short NAV_MSG_NO_NOTES_YET              = 2408;
    public static final short NAV_MSG_NOTE_ADDED                = 2409;
    public static final short NAV_MSG_NEW_FILE_ITEM             = 2410;
    public static final short NAV_MSG_USER_FILE_PREFIX          = 2411;
    /* navigation - waypoint form */
    public static final short NAV_TITLE_WPT                     = 2200;
    public static final short NAV_FLD_WPT_NAME                  = 2201;
    public static final short NAV_FLD_WPT_CMT                   = 2202;
    public static final short NAV_FLD_WGS84LAT                  = 2203;
    public static final short NAV_FLD_WGS84LON                  = 2204;
    public static final short NAV_FLD_TIME                      = 2205;
    public static final short NAV_FLD_LOC                       = 2206;
    public static final short NAV_FLD_SNAPSHOT                  = 2207;
    public static final short NAV_FLD_RECIPIENT                 = 2208;
    public static final short NAV_FLD_MESSAGE                   = 2209;
    public static final short NAV_TITLE_CAMERA                  = 2210;
    public static final short NAV_FLD_ALT                       = 2211;
    public static final short NAV_FLD_ZONE                      = 2212;
    public static final short NAV_FLD_LAT                       = 2213;
    public static final short NAV_FLD_LON                       = 2214;
    public static final short NAV_FLD_UTM_EASTING               = 2215;
    public static final short NAV_FLD_UTM_NORTHING              = 2216;
    public static final short NAV_FLD_EASTING                   = 2217;
    public static final short NAV_FLD_NORTHING                  = 2218;
    public static final short NAV_FLD_GS_ID                     = 2219;
    public static final short NAV_FLD_GS_CLASS                  = 2220;
    public static final short NAV_FLD_GS_LISTING_SHORT          = 2221;
    public static final short NAV_FLD_GS_LISTING_LONG           = 2222;
    public static final short NAV_FLD_GS_HINT                   = 2223;
    public static final short NAV_FLD_DISTANCE                  = 2224;
    public static final short NAV_FLD_GS_LOGS                   = 2225;
    public static final short NAV_FLD_GS_LOG                    = 2226;
    public static final short NAV_FLD_DATE                      = 2227;
    public static final short NAV_FLD_TYPE                      = 2228;
    public static final short NAV_FLD_FINDER                    = 2229;
    public static final short NAV_FLD_TEXT                      = 2230;
    public static final short NAV_TITLE_FIELD_NOTE              = 2231;
    /* settings - commands */
    public static final short CFG_CMD_APPLY                     = 3000;
    public static final short CFG_CMD_SAVE                      = 3001;
    /* settings - menu */
    public static final short CFG_ITEM_BASIC                    = 3100;
    public static final short CFG_ITEM_DESKTOP                  = 3101;
    public static final short CFG_ITEM_LOCATION                 = 3102;
    public static final short CFG_ITEM_NAVIGATION               = 3103;
    public static final short CFG_ITEM_MISC                     = 3104;
    /* settings - Basic */
    public static final short CFG_BASIC_FLD_START_MAP           = 3400;
    public static final short CFG_BASIC_GROUP_DEFAULT_DATUM     = 3401;
    public static final short CFG_BASIC_GROUP_COORDS_FMT        = 3402;
    public static final short CFG_BASIC_GROUP_UNITS             = 3403;
    public static final short CFG_BASIC_FLD_DATA_DIR            = 3405;
    public static final short CFG_BASIC_FLD_UNITS_METRIC        = 3406;
    public static final short CFG_BASIC_FLD_UNITS_IMPERIAL      = 3407;
    public static final short CFG_BASIC_FLD_UNITS_NAUTICAL      = 3408;
    public static final short CFG_BASIC_FLD_COORDS_MAPLL        = 3409;
    public static final short CFG_BASIC_FLD_COORDS_MAPGRID      = 3410;
    public static final short CFG_BASIC_FLD_COORDS_GCLL         = 3411;
    public static final short CFG_BASIC_GROUP_START_SCREEN      = 3412;
    public static final short CFG_BASIC_FLD_SCREEN_MAP          = 3413;
    public static final short CFG_BASIC_FLD_SCREEN_HPS          = 3414;
    public static final short CFG_BASIC_FLD_SCREEN_CMS          = 3415;
    /* settings - Desktop */
    public static final short CFG_DESKTOP_GROUP                 = 3500;
    public static final short CFG_DESKTOP_FLD_FULLSCREEN        = 3501;
    public static final short CFG_DESKTOP_FLD_NO_SOUNDS         = 3502;
    public static final short CFG_DESKTOP_FLD_DEC_PRECISION     = 3503;
    public static final short CFG_DESKTOP_FLD_HPS_WPT_TRUE_AZI  = 3504;
    public static final short CFG_DESKTOP_FLD_OSD_BASIC         = 3505;
    public static final short CFG_DESKTOP_FLD_OSD_EXT           = 3506;
    public static final short CFG_DESKTOP_FLD_OSD_SCALE         = 3507;
    public static final short CFG_DESKTOP_FLD_OSD_NO_BG         = 3508;
    public static final short CFG_DESKTOP_FLD_OSD_MED_FONT      = 3509;
    public static final short CFG_DESKTOP_FLD_OSD_BOLD_FONT     = 3510;
    public static final short CFG_DESKTOP_FLD_OSD_BLACK         = 3511;
    public static final short CFG_DESKTOP_TRANSPARENCY          = 3512;
    public static final short CFG_DESKTOP_FLD_CMS_CYCLE         = 3513;
    public static final short CFG_DESKTOP_FLD_TRAJECTORY        = 3514;
    public static final short CFG_DESKTOP_FLD_TRAIL_PREVIEW     = 3515;
    public static final short CFG_DESKTOP_CMD_TRAIL_MODE        = 3516;
    public static final short CFG_DESKTOP_FLD_SAFE_COLORS       = 3517;
    public static final short CFG_DESKTOP_FLD_LIST_FONT         = 3518;
    public static final short CFG_DESKTOP_FLD_COLOR             = 3519;
    public static final short CFG_DESKTOP_FLD_THICKNESS         = 3520;
    public static final short CFG_DESKTOP_FLD_NO_QUESTIONS      = 3521;
    public static final short CFG_DESKTOP_FLD_FONT_SIZE         = 3522;
    public static final short CFG_DESKTOP_FLD_NO_COMMANDS       = 3523;
    public static final short CFG_DESKTOP_GROUP_EASYZOOM        = 3524;
    public static final short CFG_DESKTOP_FLD_EASYZOOM_LAYERS   = 3525;
    public static final short CFG_DESKTOP_FLD_EASYZOOM_MAPS     = 3526;
    public static final short CFG_DESKTOP_FLD_EASYZOOM_OFF      = 3527;
    public static final short CFG_DESKTOP_FLD_EASYZOOM_VOLUME   = 3528;
    public static final short CFG_DESKTOP_FLD_SHOW_VISUAL_SPOTS = 3529;
    public static final short CFG_DESKTOP_FLD_NO_ITEM_COMMANDS  = 3530;
    /* settings - Location */
    public static final short CFG_LOCATION_GROUP_PROVIDER       = 3600;
    public static final short CFG_LOCATION_GROUP_TRACKLOG       = 3601;
    public static final short CFG_LOCATION_FLD_TRACKLOG_ALWAYS  = 3602;
    public static final short CFG_LOCATION_FLD_TRACKLOG_ASK     = 3603;
    public static final short CFG_LOCATION_FLD_TRACKLOG_NEVER   = 3604;
    public static final short CFG_LOCATION_GROUP_TRACKLOG_FMT   = 3605;
    public static final short CFG_LOCATION_FLD_CAPTURE_LOCATOR  = 3606;
    public static final short CFG_LOCATION_FLD_CAPTURE_FMT      = 3607;
    public static final short CFG_LOCATION_FLD_CONN_URL         = 3608;
    public static final short CFG_LOCATION_FLD_SIMULATOR_DELAY  = 3609;
    public static final short CFG_LOCATION_FLD_LOCATION_TIMINGS = 3610;
    public static final short CFG_LOCATION_FLD_PROV_BLUETOOTH   = 3611;
    public static final short CFG_LOCATION_FLD_PROV_INTERNAL    = 3612;
    public static final short CFG_LOCATION_FLD_PROV_SERIAL      = 3613;
    public static final short CFG_LOCATION_FLD_PROV_SIMULATOR   = 3614;
    public static final short CFG_LOCATION_FLD_PROV_MOTOROLA    = 3615;
    public static final short CFG_LOCATION_GROUP_GPX_OPTS       = 3616;
    public static final short CFG_LOCATION_FLD_GPX_LOG_VALID    = 3617;
    public static final short CFG_LOCATION_FLD_PROV_O2GERMANY   = 3618;
    public static final short CFG_LOCATION_FLD_FILTER_DEPTH     = 3619;
    public static final short CFG_LOCATION_FLD_GPX_LOG_GSM      = 3620;
    public static final short CFG_LOCATION_FLD_PROV_HGE100      = 3621;
    public static final short CFG_LOCATION_FLD_BT_KEEP_ALIVE    = 3622;
    public static final short CFG_LOCATION_FLD_ALT_CORRECTION   = 3623;
    public static final short CFG_LOCATION_FLD_GPX_LOG_TIME_MS  = 3624;
    public static final short CFG_LOCATION_FLD_NEGATIVE_ALT_FIX = 3625;
    public static final short CFG_LOCATION_FLD_NMEA_MS_ROUNDING = 3626;
    public static final short CFG_LOCATION_FLD_ASSISTED_GPS     = 3627;
    public static final short CFG_LOCATION_FLD_DO_SERVICE_SEARCH = 3628;
    public static final short CFG_LOCATION_GROUP_POWER          = 3629;
    public static final short CFG_LOCATION_FLD_ADDRESS_FIX      = 3630;
    public static final short CFG_LOCATION_FLD_TIME_FIX         = 3631;
    /* settings - navigation */
    public static final short CFG_NAVIGATION_FLD_WPT_PROXIMITY  = 3700;
    public static final short CFG_NAVIGATION_FLD_POI_PROXIMITY  = 3701;
    public static final short CFG_NAVIGATION_GROUP_ROUTE_LINE   = 3702;
    public static final short CFG_NAVIGATION_FLD_DOTTED         = 3703;
    public static final short CFG_NAVIGATION_FLD_RED            = 3704;
    public static final short CFG_NAVIGATION_FLD_POI_ICONS      = 3705;
    public static final short CFG_NAVIGATION_GROUP_SMS          = 3706;
    public static final short CFG_NAVIGATION_FLD_RECEIVE        = 3707;
    public static final short CFG_NAVIGATION_FLD_AUTOHIDE       = 3708;
    public static final short CFG_NAVIGATION_FLD_REVISIONS      = 3709;
    public static final short CFG_NAVIGATION_FLD_PREFER_GSNAME  = 3710;
    public static final short CFG_NAVIGATION_GROUP_SORT         = 3711;
    public static final short CFG_NAVIGATION_FLD_SORT_BYPOS     = 3712;
    public static final short CFG_NAVIGATION_FLD_SORT_BYNAME    = 3713;
    public static final short CFG_NAVIGATION_FLD_SORT_BYDIST    = 3714;
    public static final short CFG_NAVIGATION_FLD_DYNAMIC_PROX   = 3715;
    /* settings - tweaks */
    public static final short CFG_TWEAKS_GROUP                  = 3800;
    public static final short CFG_TWEAKS_FLD_SIEMENS_IO         = 3801;
    public static final short CFG_TWEAKS_FLD_SAFE_RENDERER      = 3802;
    public static final short CFG_TWEAKS_FLD_FORCED_GC          = 3803;
    public static final short CFG_TWEAKS_FLD_1TILE_SCROLL       = 3804;
    public static final short CFG_TWEAKS_FLD_LARGE_ATLASES      = 3805;
    public static final short CFG_TWEAKS_FLD_POWER_SAVE         = 3806;
    public static final short CFG_TWEAKS_FLD_RELIABLE_INPUT     = 3807;
    public static final short CFG_TWEAKS_FLD_HIDEBAR_CMD        = 3808;
    public static final short CFG_TWEAKS_FLD_USE_TBSVC          = 3809;
    public static final short CFG_TWEAKS_FLD_LAZY_GPX           = 3810;
    public static final short CFG_TWEAKS_FLD_LOWMEM_IO          = 3811;
    public static final short CFG_TWEAKS_FLD_NUMERIC_INPUT_HACK = 3812;
    public static final short CFG_TWEAKS_FLD_DRAW_MAIN_MENU     = 3813;
    public static final short CFG_TWEAKS_FLD_EXTERNAL_CFG_BACKUP = 3814;
    /* info - commands */
    public static final short INFO_CMD_DETAILS                  = 4000;
    /* info - items */
    public static final short INFO_ITEM_VENDOR                  = 4200;
    public static final short INFO_ITEM_VENDOR_VALUE            = 4201;
    public static final short INFO_ITEM_VERSION                 = 4202;
    public static final short INFO_ITEM_KEYS                    = 4203;
    public static final short INFO_ITEM_KEYS_MS                 = 4204;

    private static String[] values, userValues;
    private static String value, userValue;
    private static int[] ids, userIds;
    private static short[] keymap0, keymap1;
    private static short keymapSize;

    private Resources() {
    }

    static int initialize() throws IOException {
        final Object[] holder = new Object[2];

        // read default resources
        InputStream is = Resources.class.getResourceAsStream("/resources/language." + System.getProperty("microedition.locale") + ".res");
        if (is == null) {
            is = Resources.class.getResourceAsStream("/resources/language.en_US.res");
        }
        loadRes(is, holder);
        ids = (int[]) holder[0];
        value = (String) holder[1];
        values = new String[ids.length];

        return 1;
    }

//#ifdef __USERL10N__

    public static int localize() throws IOException {
        int result = 0;
        final Object[] holder = new Object[2];

        // reload-safe
        userIds = null;
        userValue = null;
        userValues = null;

        // read user resources
        api.file.File file = null;
        try {
            file = api.file.File.open(Config.getFolderURL(Config.FOLDER_RESOURCES) + "language.res");
            if (file.exists()) {
                loadRes(file.openInputStream(), holder);
                userIds = (int[]) holder[0];
                userValue = (String) holder[1];
                userValues = new String[userIds.length];
                result++;
            }
        } catch (Throwable t) {
            // ignore
//#ifdef __LOG__
            t.printStackTrace();
//#endif
        } finally {
            try {
                file.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
//#ifndef __B2B__
            if (b2b_RejectRes(userIds)) {
                userIds = null;
                userValue = null;
                userValues = null;
            }
//#endif
        }

        return result;
    }

//#endif

    public static int keymap() throws IOException {
        int result = 0;

        // reload-safe
        keymap0 = keymap1 = null;
        keymapSize = 0;

        // do
        api.file.File file = null;
        try {
            file = api.file.File.open(Config.getFolderURL(Config.FOLDER_RESOURCES) + "keymap.txt");
            if (file.exists()) {
                LineReader reader = null;
                try {
                    reader = new LineReader(file.openInputStream());
                    keymap0 = new short[32];
                    keymap1 = new short[32];
                    final char[] delims = { '=' };
                    final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
                    CharArrayTokenizer.Token token = reader.readToken(false);
                    while (token != null && keymapSize < 32) {
                        tokenizer.init(token, delims, false);
                        keymap0[keymapSize] = (short) tokenizer.nextInt();
                        keymap1[keymapSize] = (short) tokenizer.nextInt();
                        keymapSize++;
                        result++;
                        token = null; // gc hint
                        token = reader.readToken(false);
                    }
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        } finally {
            try {
                file.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
        }

        return result;
    }

    public static String getString(final short id) {
        String result = null;
        if (userIds != null && userValues != null && userValue != null) {
            result = get(id, userIds, userValues, userValue);
        }
        if (result == null) {
            result = get(id, ids, values, value);
            if (result == null) {
                result = Integer.toString(id);
            }
        }

        return result;
    }

    public static String format(final short id, final String content) {
        String s = getString(id);
        final int wi = s.indexOf("%s");
        if (wi > -1) {
            final StringBuffer sb = new StringBuffer(s.length() + content.length());
            sb.append(s.substring(0, wi));
            sb.append(content);
            sb.append(s.substring(wi + 2));
            s = sb.toString();
        }
        return s;
    }

    public static int remap(final int keycode) {
        if (keymapSize > 0) {
            for (int i = keymapSize; --i >= 0; ) {
                if (keymap0[i] == keycode) {
                    return keymap1[i];
                }
            }
        }

        return keycode;
    }

    public static String prefixed(final String title) {
//#ifdef __J9__
        if (cz.kruch.track.TrackingMIDlet.j9) {
            return "TrekBuddy - " + title;
        }
//#endif
        return title;
    }

    private static void loadRes(final InputStream in, final Object[] retval) throws IOException {
        if (in != null) {
            final DataInputStream resin = new DataInputStream(new BufferedInputStream(in, 4096));
            try {
                final int signature = resin.readInt(); // signature: 0xEA4D4910; // JSR-238: 0xEE4D4910
                final int hl = resin.readInt();        // header length
                final int count = hl / 8;
                int[] IDs = new int[count];
                for (int i = 0; i < count; i++) {
                    IDs[i] = resin.readInt() - hl;
                }
                retval[0] = IDs;
                retval[1] = resin.readUTF();
            } catch (EOFException e) {
                // end of stream
            } finally {
                try {
                    resin.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static String get(final short id, final int[] ids,
                             final String[] values, final String value) {
        String result = null;
        for (int i = ids.length; --i >= 0; ) {
            if (id == ((ids[i] >> 16) & 0x0000ffff)) {
                result = values[i];
                if (result == null) {
                    final int start = ids[i] & 0x0000ffff;
                    if (i < ids.length - 1) {
                        final int end = ids[i + 1] & 0x0000ffff;
                        result = value.substring(start, end);
                    } else {
                        result = value.substring(start);
                    }
                    values[i] = result;
                }
                break;
            }
        }
        return result;
    }

//#ifndef __B2B__

    private static boolean b2b_RejectRes(final int[] ids) {
        if (ids != null) {
            for (int i = ids.length; --i >= 0; ) {
                if (100 == ((ids[i] >> 16) & 0x0000ffff)) {
                    return true;
                }
            }
        }
        return false;
    }

//#endif

}
