package cz.kruch.track.sensor;

//#ifdef __ANDROID__

import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.dsi.ant.AntInterface;
import com.dsi.ant.antplusdemo.AntPlusManager;
import com.dsi.ant.antplusdemo.ANTPlusService;

import cz.kruch.track.hecl.PluginManager;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import java.util.Vector;

public final class ANTPlus implements AntPlusManager.Callbacks {
    // log tag
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE.concat("/ANT+");

    /**
     * The default proximity search bin.
     */
    private static final byte DEFAULT_BIN = 7;

    /**
     * The default event buffering buffer threshold.
     */
    private static final short DEFAULT_BUFFER_THRESHOLD = 0;

    /**
     * Shared preferences data filename.
     */
    public static final String PREFS_NAME = "net.trekbuddy.midlet.antprefs"; 

    private static ANTPlus instance;

    private Context context;
    private AntPlusManager manager;
    private ANTPlusPlugin plugin;

    private String message = "Not connected";
    private StringItem console;

    private boolean bound;

    private volatile int sensorBPM;
    private volatile long sensorBPMt;

    public static boolean isSupported() {
        return AntInterface.hasAntSupport(cz.kruch.track.TrackingMIDlet.getActivity());
    }

    public static ANTPlus getInstance() {
        if (instance == null) {
            instance = new ANTPlus(cz.kruch.track.TrackingMIDlet.getActivity());
        }
        return instance;
    }

    public static void initialize() {
        if (isSupported()) {
            getInstance().init();
        }
    }

    public static void dispose() {
        if (isSupported()) {
            getInstance().destroy();
        }
    }

    private ANTPlus(Context context) {
        this.context = context;
    }

    private void init() {
        PluginManager.getInstance().addPlugin(plugin = new ANTPlusPlugin());
    }

    private void destroy() {
        //PluginManager.getInstance().removePlugin(plugin);
    }

    public int getSensorBPM() {
        if (((System.currentTimeMillis() - sensorBPMt) / 1000) < 60) {
            return sensorBPM;
        }
        return -1;
    }

    void start() {
        Log.i(TAG, "start");
        sensorBPM = -1;
        sensorBPMt = 0;
        bound = context.bindService(new Intent(context, ANTPlusService.class), connection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bindService; " + bound);
    }

    void stop() {
        Log.i(TAG, "stop");
        if (manager != null) {
            saveState();
            manager.setCallbacks(null);
            if (manager.isChannelOpen(AntPlusManager.HRM_CHANNEL)) {
                manager.closeChannel(AntPlusManager.HRM_CHANNEL);
            }
        }
        if (bound) {
            context.unbindService(connection);
            Log.d(TAG, "unbindService");
        }
        sensorBPM = -1;
        sensorBPMt = 0;
        updateSpecific("");
    }

    void updateDetail(String detail) {
        if (detail != null) {
            message = detail;
        }
    }

    void updateSpecific(String specific) {
        if (console != null) {
            console.setText(specific);
        }
    }

    private void drawWindow() {
        final boolean showChannels = manager.checkAntState();
        if (showChannels) {
            drawChannelState(AntPlusManager.HRM_CHANNEL);
        } else {
            updateSpecific(manager.getAntStateText());
        }
    }

    private void drawChannelState(final byte channel) {
        switch (channel) {
            case AntPlusManager.HRM_CHANNEL:
                switch (manager.getHrmState()) {
                    case CLOSED:
                        updateSpecific("Closed");
                        break;
                    case OFFLINE:
                        updateSpecific("Offline");
                        break;
                    case SEARCHING:
                        updateSpecific("Searching");
                        break;
                    case PENDING_OPEN:
                        updateSpecific("Opening");
                        break;
                    case TRACKING_STATUS:
                        //This state should not show up for this channel, but in the case it does
                        //We can consider it equivalent to showing the data.
                    case TRACKING_DATA:
                        break;
                }
                break;
        }
    }

    private void drawChannelData(final byte channel) {
        switch (channel) {
            case AntPlusManager.HRM_CHANNEL:
                switch (manager.getHrmState()) {
                    case CLOSED:
                        break;
                    case OFFLINE:
                        break;
                    case SEARCHING:
                        break;
                    case PENDING_OPEN:
                        break;
                    case TRACKING_STATUS:
                        //There is no Status state for the HRM channel, so we will attempt to show latest data instead
                    case TRACKING_DATA:
                        sensorBPM = manager.getBPM();
                        sensorBPMt = System.currentTimeMillis();
                        updateSpecific(String.valueOf(sensorBPM));
                        break;
                }
                break;
        }
    }

    void loadConfiguration() {
        final SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        manager.setDeviceNumberHRM((short) settings.getInt("DeviceNumberHRM", AntPlusManager.WILDCARD));
        manager.setProximityThreshold((byte) settings.getInt("ProximityThreshold", DEFAULT_BIN));
        manager.setBufferThreshold((short) settings.getInt("BufferThreshold", DEFAULT_BUFFER_THRESHOLD));
        Log.d(TAG, "loaded cfg; HRM device ID: " + manager.getDeviceNumberHRM() +
              "; proximity threshold: " + manager.getProximityThreshold() +
              "; buffer threshold: " + manager.getBufferThreshold());
    }

    void saveState() {
        final SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("DeviceNumberHRM", manager.getDeviceNumberHRM());
        editor.putInt("ProximityThreshold", manager.getProximityThreshold());
        editor.putInt("BufferThreshold", manager.getBufferThreshold());
        editor.commit();
        Log.d(TAG, "saved cfg; HRM device ID: " + manager.getDeviceNumberHRM() +
              "; proximity threshold: " + manager.getProximityThreshold() +
              "; buffer threshold: " + manager.getBufferThreshold());
    }

    public void errorCallback() {
        Log.e(TAG, "errorCallback");
    }

    public void notifyAntStateChanged() {
        Log.d(TAG, "notifyAntStateChanged; status: " + manager.getAntStateText());
        if (manager.checkAntState() && manager.isInterfaceClaimed()) {
            if (!manager.isChannelOpen(AntPlusManager.HRM_CHANNEL)) {
                Log.i(TAG, "reset and open HRM channel");
                manager.openChannel(AntPlusManager.HRM_CHANNEL, true);
                manager.requestReset();
            }
        }
        drawWindow();
    }

    public void notifyChannelStateChanged(final byte channel) {
        if (AntPlusManager.HRM_CHANNEL == channel) {
            Log.d(TAG, "notifyChannelStateChanged; HRM channel state: " + manager.getHrmState());
            drawChannelState(channel);
        }
    }

    public void notifyChannelDataChanged(final byte channel) {
        if (AntPlusManager.HRM_CHANNEL == channel) {
            Log.d(TAG, "notifyChannelDataChanged; HRM channel state: " + manager.getHrmState());
            drawChannelData(channel);
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected");
            updateDetail("Disconnected");

            // this is very unlikely to happen with a local service (ie. one in the same process)
            manager.setCallbacks(null);
            manager = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            updateDetail("Connected");

            manager = ((ANTPlusService.LocalBinder) service).getManager();
            manager.setCallbacks(ANTPlus.this);

            loadConfiguration();
            notifyAntStateChanged();

            manager.checkAntState();
        }
    };

    private final class ANTPlusPlugin extends PluginManager.Plugin {

        private static final String CMD_START = "Start";
        private static final String CMD_STOP = "Stop";

        public ANTPlusPlugin() {
            super("ANT+ HRM", "1.0", "antplus");
            super.actions = new Vector();
            super.actions.addElement(CMD_START);
            super.actions.addElement(CMD_STOP);
        }

        public void setVisible(Form form) {
            if (form == null) {
                console = null;
            } else {
                form.append(console = new StringItem("Detail", "BPM: ---"));
            }
        }

        public String getStatus() {
            return getSensorBPM() > -1 ? "OK" : null;
        }

        public String getDetail() {
            return message;
        }

        public Form appendOptions(Form form) {
            return form;
        }

        public void grabOptions(Form form) {
        }

        public String execute(Form form, String action) {
            String result = "OK";
            if (CMD_START.equals(action)) {
                updateSpecific("Connecting...");
                start();
            } else if (CMD_STOP.equals(action)) {
                updateSpecific("Disconnecting...");
                stop();
            } else {
                result = "Unsupported action!";
            }
            return result;
        }
    }
}

//#endif
