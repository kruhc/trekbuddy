// @LICENSE@

package cz.kruch.track.hecl;

//#ifdef __HECL__

import api.file.File;
import api.lang.RuntimeException;
import api.lang.Int;
import api.location.Location;
import api.util.Comparator;

import java.util.Enumeration;
import java.util.Vector;
import java.util.Date;
import java.util.Hashtable;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.util.NakedVector;
import cz.kruch.track.util.ImageUtils;
import cz.kruch.track.Resources;
import cz.kruch.track.event.Callback;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.FileBrowser;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Image;

import org.hecl.HeclException;
import org.hecl.Thing;
import org.hecl.ListThing;
import org.hecl.RealThing;
import org.hecl.NumberThing;
import org.hecl.IntThing;
import org.hecl.LongThing;
import org.hecl.FloatThing;
import org.hecl.DoubleThing;
import org.hecl.StringThing;

public final class PluginManager implements CommandListener, Runnable, Comparator {
//#ifdef __ANDROID__
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;
//#endif
    
    private static final String PROC_GET_VERSION            = "getVersion";
    private static final String PROC_GET_NAME               = "getName";
    private static final String PROC_GET_OPTIONS            = "getOptions";
    private static final String PROC_GET_STATUS             = "getStatus";
    private static final String PROC_GET_DETAIL             = "getDetail";
    private static final String PROC_GET_ACTIONS            = "getActions";
    private static final String PROC_ON_TRACKING_START      = "onTrackingStart";
    private static final String PROC_ON_TRACKING_STOP       = "onTrackingStop";
    private static final String PROC_ON_STATUS_CHANGED      = "onStatusChanged";
    private static final String PROC_ON_LOCATION_UPDATED    = "onLocationUpdated";

    private static final int EVENT_ON_TRACKING_START     = 0;
    private static final int EVENT_ON_TRACKING_STOP      = 1;
    private static final int EVENT_ON_LOCATION_UPDATED   = 2;
    private static final int EVENT_ON_STATUS_CHANGED     = 3;

    private static final String NAMESPACE_SEPARATOR = "::";
    private static final String STATUS_OK = "OK";

    public static abstract class Plugin implements Callback {
        protected String name, ns;
        protected String version;
        protected Vector options, actions;
        protected boolean enabled;

        private String errorMessage;
        private Date errorTime;
        private int errors;

        public Plugin() {
            this.name = "unknown";
        }

        protected Plugin(String name, String version, String ns) {
            this.name = name;
            this.version = version;
            this.ns = ns;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVersion() {
            return version;
        }

        public String getName() {
            return name;
        }

        public String getFullName() {
            if (name != null) {
                final StringBuffer sb = new StringBuffer(32);
                sb.append(name);
                sb.append(" v");
                if (version != null) {
                    sb.append(version);
                } else {
                    sb.append("???");
                }
                return sb.toString();
            }
            return getName();
        }

        public String getStatus() {
            return null;
        }

        public String getDetail() {
            return null;
        }

        public Vector getActions() {
            return actions;
        }

        public boolean hasOptions() {
            return options != null;
        }

        public Vector getOptions() {
            return options;
        }

        public boolean hasError() {
            return errorMessage != null;
        }

        public String getError() {
            return errorMessage;
        }

        public Date getErrorTime() {
            return errorTime;
        }

        public int getErrors() {
            return errors;
        }

        void setError(String message) {
            errorMessage = message;
            errorTime = new Date();
            errors++;
        }

        void clearError() {
            errorMessage = null;
            errorTime = null;
        }

        void reset() {
            errorMessage = null;
            errorTime = null;
        }

        String getNs() {
            return ns;
        }

        public String toString() {
            return getName();
        }

        public void invoke(Object result, Throwable throwable, Object source) {
            try {
                if (result instanceof DataInputStream) {
                    loadOptions((DataInputStream) result);
                } else if (result instanceof DataOutputStream) {
                    saveOptions((DataOutputStream) result);
                }
            } catch (Exception e) {
                throw new RuntimeException(getName(), e);
            }
        }

        protected void loadOptions(DataInputStream in) throws IOException {
        }

        protected void saveOptions(DataOutputStream out) throws IOException {
        }

        protected void setVisible(Form form) {
        }

        public abstract Form appendOptions(Form form);
        public abstract void grabOptions(Form form);
        public abstract String execute(Form form, String action);
    }

    public static class HeclPlugin extends Plugin {
        static final Thing[] procVoidArgv = { Thing.emptyThing() };

        private String filename;
        private ControlledInterp interp;

        org.hecl.Command procGetStatus, procGetDetail,
                         eventOnTrackingStart, eventOnTrackingStop, eventOnLocationUpdated;

        public HeclPlugin(String filename) {
            this.ns = filename.substring(5, filename.length() - 4); // 5: "live.".length, 4: ".hcl".length
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }

        public String getName() {
            if (name != null) {
                return name;
            }
            return "unknown / " + filename;
        }

        public String getStatus() {
            if (procGetStatus != null) {
                return invoke(procGetStatus, procVoidArgv).toString();
            }
            return super.getStatus();
        }

        public String getDetail() {
            if (procGetDetail != null) {
                return invoke(procGetDetail, procVoidArgv).toString();
            }
            return super.getDetail();
        }

        public Form appendOptions(Form form) {
            final Vector options = getOptions();
            for (int i = 0, N = options.size(); i < N; i++) {
                final String optionName = options.elementAt(i).toString();
                final Thing optionVar = getVar(optionName);
                if (optionVar != null) {
                    final RealThing rt = optionVar.getVal();
                    final int inputType, maxLen;
                    if (rt instanceof NumberThing) {
                        if (Config.numericInputHack) {
                            inputType = TextField.ANY;
                        } else {
                            inputType = ((NumberThing) rt).isIntegral() ? TextField.NUMERIC : TextField.DECIMAL;
                        }
                        maxLen = 24;
                    } else {
                        inputType = TextField.ANY;
                        maxLen = 128;
                    }
                    final TextField tf = new TextField(optionName, rt.getStringRep(), maxLen, inputType);
                    form.append(tf);
                } else {
                    form.append(new StringItem(optionName, "<unknown option>"));
                }
            }

            return form;
        }

        public void grabOptions(Form form) {
            for (int i = 0, N = form.size(); i < N; i++) {
                final Item item = form.get(i);
                if (item instanceof TextField) {
                    final TextField tf = (TextField) item;
                    final String value = tf.getString().trim();
                    final Thing var = getVar(tf.getLabel());
                    final RealThing rt = var.getVal();
                    if (rt instanceof IntThing) {
                        var.setVal(new IntThing(value));
                    } else if (rt instanceof LongThing) {
                        var.setVal(new LongThing(value));
                    } else if (rt instanceof FloatThing) {
                        var.setVal(new FloatThing(value));
                    } else if (rt instanceof DoubleThing) {
                        var.setVal(new DoubleThing(value));
                    } else {
                        var.setVal(new StringThing(value));
                    }
                }
            }
        }

        public Thing getVar(String name) {
            try {
                Thing result = interp.resolveVar(name);
                if (result == null && PluginManager.instance.proxy != null && PluginManager.instance.proxy.getInterp() != null) {
                    result = PluginManager.instance.proxy.getInterp().resolveVar(name);
                }
                return result;
            } catch (Throwable t) {
//#ifdef __ANDROID__
                android.util.Log.d(TAG, "plugin getVar failed [" + name +"]", t);
//#elifdef __SYMBIAN__
                System.err.println("[TrekBuddy] plugin getVar failed");
                t.printStackTrace();
//#endif
            }
            return null;
        }

        public String toString() {
            return getFilename();
        }

        public String execute(Form form, String actionCommand) {
//            final org.hecl.Command hc = firstUnknown(actionCommand);
//            final Thing hr = hc.cmdCode(interp, HeclPlugin.procVoidArgv);
//            return hr.toString();
            throw new api.lang.RuntimeException("execute", null);
        }

        void setup(final ControlledInterp interp,
                   final org.hecl.Command hName, final org.hecl.Command hVersion,
                   final org.hecl.Command hOptions, final org.hecl.Command hActions) {
            this.interp = interp;
            if (hName != null) {
                try {
                    this.name = hName.cmdCode(interp, procVoidArgv).toString();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            if (hVersion != null) {
                try {
                    this.version = hVersion.cmdCode(interp, procVoidArgv).toString();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            if (hOptions != null) {
                try {
                    this.options = ListThing.get(hOptions.cmdCode(interp, procVoidArgv));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            if (hActions != null) {
                try {
                    this.actions = ListThing.get(hActions.cmdCode(interp, procVoidArgv));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        protected void loadOptions(final DataInputStream in) throws IOException {
            int i = in.readInt();
            enabled = in.readBoolean();
            while (--i >= 0) {
                final String optionName = in.readUTF();
                final int optionType = in.readByte();
                final RealThing rt;
                switch (optionType) {
                    case 0: {
                        rt = new IntThing(in.readInt());
                    } break;
                    case 1: {
                        rt = new LongThing(in.readLong());
                    } break;
                    case 2: {
                        rt = new FloatThing(in.readFloat());
                    } break;
                    case 3: {
                        rt = new DoubleThing(in.readDouble());
                    } break;
                    default: {
                        rt = new StringThing(in.readUTF());
                    }
                }
                final Thing optionVar = getVar(optionName);
                if (optionVar != null) {
                    optionVar.setVal(rt);
//#ifdef __ANDROID__
                    android.util.Log.d(TAG, "set value for " + optionName + " [" + rt.getStringRep() + "]");
//#endif
                } else {
                    final PluginManager instance = PluginManager.instance;
                    if (instance.orphanOptions == null) {
                        instance.orphanOptions = new Hashtable(4);
                    }
                    instance.orphanOptions.put(optionName, rt);
//#ifdef __ANDROID__
                    android.util.Log.d(TAG, "saved orphan option " + optionName + " [" + rt.getStringRep() + "]");
//#endif
                }
            }
        }

        protected void saveOptions(final DataOutputStream out) throws IOException {
            final Vector options = getOptions();
            out.writeInt(options.size());
            out.writeBoolean(enabled);
            for (int i = 0, N = options.size(); i < N; i++) {
                final String optionName = options.elementAt(i).toString();
                final Thing optionVar = getVar(optionName);
                if (optionVar != null) {
                    out.writeUTF(optionName);
                    final RealThing rt = optionVar.getVal();
                    if (rt instanceof IntThing) {
                        out.writeByte(0);
                        out.writeInt(((IntThing) rt).intValue());
                    } else if (rt instanceof LongThing) {
                        out.writeByte(1);
                        out.writeLong(((LongThing) rt).longValue());
                    } else if (rt instanceof FloatThing) {
                        out.writeByte(2);
                        out.writeFloat(((FloatThing) rt).floatValue());
                    } else if (rt instanceof DoubleThing) {
                        out.writeByte(3);
                        out.writeDouble(((DoubleThing) rt).doubleValue());
                    } else {
                        out.writeByte(255);
                        out.writeUTF(optionVar.toString());
                    }
                }
            }
        }

        private Thing invoke(final org.hecl.Command command, final Thing[] argv) {
            try {
                return command.cmdCode(PluginManager.instance.interp, argv);
            } catch (Throwable t) {
//#ifdef __ANDROID__
                android.util.Log.d(TAG, "plugin invoke failed [" + command +"]", t);
//#endif                
                return new Thing("{ERROR: " + command + "} " + t.toString());
            }
        }
    }

    /*private*/ static PluginManager instance;

    private ControlledInterp interp;
    private NakedVector plugins;
    private ControlledInterp.Lookup fallback;
    /*private*/ ControlledInterp.Proxy proxy;
    /*private*/ Hashtable orphanOptions;

    private Displayable pane;
    private Image iconOK, iconUnknown, iconError;

    private volatile String _actionCommand;
    private volatile Plugin _actionPlugin;
    private volatile Form _actionForm;

    public static PluginManager getInstance() {
        if (instance == null) {
            instance = new PluginManager();
        }
        return instance;
    }

//#ifdef __ANDROID__

    public static void jvmReset() {
        instance = null;
    }

//#endif    

    private PluginManager() {
        try {
            this.iconOK = ImageUtils.getGoodIcon("/resources/plugin.ok.png");
            this.iconUnknown = ImageUtils.getGoodIcon("/resources/plugin.not.png");
            this.iconError = ImageUtils.getGoodIcon("/resources/plugin.err.png");
        } catch (Throwable t) {
            // ignore
        }
    }

    public void addPlugin(Plugin plugin) {
        if (plugins == null) {
            plugins = new NakedVector(4, 2);
        }
        plugins.addElement(plugin);
    }

    public Plugin getPlugin(int elementNum) {
        return (Plugin) plugins.elementAt(elementNum);
    }

    public NakedVector getPlugins() {
        return plugins;
    }

    public Hashtable getOrphanOptions() {
        return orphanOptions;
    }

    public int size() {
        if (plugins == null) {
            return 0;
        }
        return plugins.size();
    }

    public void trackingStarted() {
        resetPlugins();
        enqueueEvent(EVENT_ON_TRACKING_START);
    }

    public void trackingStopped() {
        enqueueEvent(EVENT_ON_TRACKING_STOP);
    }

    public void locationUpdated(Location l) {
        enqueueEvent(EVENT_ON_LOCATION_UPDATED);
    }

    public void show() {
        if (plugins == null) {
            Desktop.showInfo(Resources.getString(Resources.DESKTOP_MSG_NO_PLUGINS), null);
        } else {
            try {
                final List pane = new List(Resources.getString(Resources.DESKTOP_CMD_LIVE), List.IMPLICIT);
                pane.setFitPolicy(Choice.TEXT_WRAP_OFF);
                final Object[] ps = plugins.getData();
                for (int i = 0, N = plugins.size(); i < N; i++) {
                    final Plugin plugin = (Plugin) ps[i];
                    final Image icon;
                    if (plugin.isEnabled()) {
                        icon = STATUS_OK.equals(plugin.getStatus()) && !plugin.hasError() ? iconOK : iconError;
                    } else {
                        icon = iconUnknown;
                    }
                    pane.append(plugin.getFullName(), icon);
                }
                pane.setCommandListener(this);
                pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
                Desktop.display.setCurrent(pane);
            } catch (Throwable t) {
                Desktop.showError("Failed to show plugins", t, null);
            }
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        final int type = command.getCommandType();
        if (Desktop.BACK_CMD_TYPE == type || Desktop.CANCEL_CMD_TYPE == type) {
            final Displayable next;
//            if (displayable instanceof List) {
                next = Desktop.screen;
//            } else {
//                next = pane;
//            }
            Desktop.display.setCurrent(next);
            pane = null;
            if (_actionPlugin != null) {
                _actionPlugin.setVisible(null);
                _actionPlugin = null;
            }
            _actionForm = null;
        } else {
            if (displayable instanceof List) {
                final List list = (List) displayable;
                final Plugin plugin = (Plugin) plugins.elementAt(list.getSelectedIndex());
                if (plugin.isEnabled()) {
                    final Form box = new Form(plugin.getFullName());
                    final String status;
                    if (plugin.hasError()) {
                        status = (new StringBuffer(plugin.getError())
                                .append(" [").append(plugin.getErrorTime()).append("]")).toString();
                    } else {
                        status = STATUS_OK;
                    }
                    box.append(new StringItem("Status", status));
                    box.append(new StringItem("Message", plugin.getDetail()));
                    final Vector actions = plugin.getActions();
                    if (actions != null) {
                        for (int i = 0, N = actions.size(); i < N; i++) {
                            box.addCommand(new Command(actions.elementAt(i).toString(), Desktop.POSITIVE_CMD_TYPE, i));
                        }
                        _actionPlugin = plugin;
                        _actionPlugin.setVisible(box);
                    }
                    box.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
                    box.setCommandListener(this);
                    Desktop.display.setCurrent(box);
                    pane = displayable;
                }
            } else {
                _actionForm = (Form) displayable;
                _actionCommand = command.getLabel();
                Desktop.getLiveWorker().enqueue(this);
            }
        }
    }

    public void saveConfiguration() {
        if (plugins == null) {
            return;
        }
        final Object[] ps = plugins.getData();
        for (int i = 0, N = plugins.size(); i < N; i++) {
            final Plugin plugin = (Plugin) ps[i];
            if (plugin.hasOptions()) {
                try {
                    Config.update(Config.PLUGIN_110 + plugin.getNs(), plugin);
                } catch (ConfigurationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void addFallback(final ControlledInterp.Lookup fallback) {
        this.fallback = fallback;
    }

    public void addProxy(final ControlledInterp.Proxy proxy) {
        this.proxy = proxy;
    }

    public void run() {
        try {
            if (_actionCommand == null) { // load HECL plugins
                load();
            } else {
                try {
                    String result;
                    if (_actionPlugin instanceof HeclPlugin) {
                        final org.hecl.Command hc = firstUnknown(_actionCommand);
                        final Thing hr = hc.cmdCode(interp, HeclPlugin.procVoidArgv);
                        result = hr.toString();
                    } else {
                        result = _actionPlugin.execute(_actionForm, _actionCommand);
                    }
                    Desktop.showInfo((new StringBuffer(64).append('[').append(_actionCommand)
                            .append("] ").append(result)).toString(), null);
                } catch (Throwable t) {
//#ifdef __ANDROID__
                    android.util.Log.e(TAG, "plugin command: " + _actionCommand, t);
//#elifdef __SYMBIAN__
                    System.err.println("[TrekBuddy] plugin command: " + _actionCommand);
                    t.printStackTrace();
//#endif
                    Desktop.showError(_actionCommand, t, null);
                }
            }
        } catch (Throwable t) {
//#ifdef __ANDROID__
            android.util.Log.e(TAG, "plugins loading failed", t);
//#elifdef __SYMBIAN__
            System.err.println("[TrekBuddy] plugins loading failed");
            t.printStackTrace();
//#endif
        } finally {
            _actionCommand = null;
        }
    }

    public int compare(Object o1, Object o2) {
        return ((Plugin) o1).getName().compareTo(((Plugin) o2).getName());
    }

    private void enqueueEvent(final int eventType) {
        if (plugins == null) {
            return;
        }
        final LiveEvent event = new LiveEvent(eventType);
        switch (eventType) {
            case EVENT_ON_TRACKING_START:
            case EVENT_ON_TRACKING_STOP:
                Desktop.getLiveWorker().enqueue(event);
            break;
            case EVENT_ON_LOCATION_UPDATED:
                if (!event.equals(Desktop.getLiveWorker().peek())) {
                    Desktop.getLiveWorker().enqueue(event);
                }
            break;
        }
    }

    void fireEvent(final int eventType) {
        final Object[] ps = plugins.getData();
        for (int i = 0, N = plugins.size(); i < N; i++) {
            final Plugin plugin = (Plugin) ps[i];
            if (plugin.isEnabled() && plugin instanceof HeclPlugin) {
                final HeclPlugin hp = (HeclPlugin) plugin;
                final org.hecl.Command proc;
                switch (eventType) {
                    case EVENT_ON_TRACKING_START:
                        interp.cacheversion = 0;
                        proc = hp.eventOnTrackingStart;
                    break;
                    case EVENT_ON_TRACKING_STOP:
                        proc = hp.eventOnTrackingStop;
                    break;
                    case EVENT_ON_LOCATION_UPDATED:
                        if (plugin.hasError()) {
                            proc = null;
                        } else {
                            interp.cacheversion++;
                            proc = hp.eventOnLocationUpdated;
                        }
                    break;
                    default:
                        proc = null;
                }
                if (proc != null) {
                    try {
                        proc.cmdCode(interp, HeclPlugin.procVoidArgv);
                        plugin.clearError();
                    } catch (Throwable t) {
//#ifdef __ANDROID__
                        android.util.Log.e(TAG, "plugin event: " + eventType, t);
//#elifdef __SYMBIAN__
                        System.err.println("[TrekBuddy] plugin event: " + eventType);
                        t.printStackTrace();
//#endif
                        plugin.setError(t.toString());
                    }
                }
            }
        }
    }

    private void load() throws IOException, HeclException {

        // offset for built-in plugins
        int hoffset = 0;
        if (plugins != null) {
            hoffset = plugins.size();
        }

        // "plugins" folder
        final String folder = Config.getFolderURL(Config.FOLDER_PLUGINS);

        // find all plugins
        File dir = null;
        try {
            dir = File.open(folder);
            for (final Enumeration seq = dir.list(); seq.hasMoreElements(); ) {
                final String filename = (String) seq.nextElement();
                final String candidate = filename.toLowerCase();
                if (candidate.startsWith("live.") && File.isOfType(candidate, ".hcl")) {
                    if (plugins == null) {
                        plugins = new NakedVector(4, 2);
                    }
                    plugins.addElement(new HeclPlugin(File.idenFix(filename)));
                }
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            File.closeQuietly(dir);
        }

        // found anything
        if (plugins != null && plugins.size() > 0) {

            // string buffer
            final StringBuffer sb = new StringBuffer(32);

            // create interp with extra HTTP commands
            interp = new ControlledInterp(Config.FOLDER_PLUGINS, false);
            interp.addFallback(fallback, new Int(Config.units));

            // load all
            final Object[] ps = plugins.getData();
            for (int i = hoffset, N = plugins.size(); i < N; i++) {

                // load script from file
                final HeclPlugin plugin = (HeclPlugin) ps[i];
                interp.loadUserScript(folder, plugin.getFilename());

                // get plugin namespace
                final String ns = plugin.getNs();

                // set handlers
                plugin.setup(interp, handlerFor(ns, PROC_GET_NAME, sb),
                                     handlerFor(ns, PROC_GET_VERSION, sb),
                                     handlerFor(ns, PROC_GET_OPTIONS, sb),
                                     handlerFor(ns, PROC_GET_ACTIONS, sb));
                plugin.procGetStatus = handlerFor(ns, PROC_GET_STATUS, sb);
                plugin.procGetDetail = handlerFor(ns, PROC_GET_DETAIL, sb);
                plugin.eventOnTrackingStart = handlerFor(ns, PROC_ON_TRACKING_START, sb);
                plugin.eventOnTrackingStop = handlerFor(ns, PROC_ON_TRACKING_STOP, sb);
                plugin.eventOnLocationUpdated = handlerFor(ns, PROC_ON_LOCATION_UPDATED, sb);

//#ifdef __ANDROID__
                android.util.Log.i(TAG, "plugin '" + plugin.getName() + "' version: " + plugin.getVersion());
//#elifdef __SYMBIAN__
                System.err.println("[TrekBuddy] plugin '" + plugin.getName() + "' version: " + plugin.getVersion());
//#endif

                // load configuration
                if (plugin.hasOptions()) {
                    try {
                        Config.initialize(Config.PLUGIN_110 + plugin.getNs(), plugin);
                    } catch (ConfigurationException e) {
                        plugin.setError(e.toString());
                    }
                }
            }

            // sort them by friendly name
            FileBrowser.sort(plugins.getData(), this, 0, plugins.size() - 1);

            // turn on optimizations
            interp.optimize();
        }
    }

    private org.hecl.Command handlerFor(final String ns, final String procname,
                                        final StringBuffer sb) {
        sb.setLength(0);
        sb.append(ns).append(NAMESPACE_SEPARATOR).append(procname);
        return firstUnknown(sb.toString());
    }

    private org.hecl.Command firstUnknown(final String handlerName) {
        final Vector handlers = interp.getHandlers(handlerName);
        if (handlers != null && handlers.size() != 0) {
            return (org.hecl.Command) handlers.elementAt(0);
        }
        return null;
    }

    private void resetPlugins() {
        if (plugins != null) {
            final Object[] ps = plugins.getData();
            for (int i = 0, N = plugins.size(); i < N; i++) {
                final Plugin plugin = (Plugin) ps[i];
                plugin.reset();
            }
        }
    }

    private final class LiveEvent implements Runnable {
        private int type;

        public LiveEvent(int type) {
            this.type = type;
        }

        public boolean equals(Object object) {
            return object instanceof LiveEvent && ((LiveEvent) object).type == type;
        }

        public void run() {
            PluginManager.this.fireEvent(type);
        }
    }
}

//#endif
