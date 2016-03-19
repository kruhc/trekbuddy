// @LICENSE@

package cz.kruch.track.hecl;

//#ifdef __HECL__

import org.hecl.Interp;
import org.hecl.Thing;
import org.hecl.HeclException;
import org.hecl.CodeThing;
import org.hecl.NumberThing;
import org.hecl.FloatThing;
import org.hecl.RealThing;

import java.util.Enumeration;
import java.util.Hashtable;
import java.io.InputStreamReader;
import java.io.IOException;

import cz.kruch.track.util.NakedVector;
import cz.kruch.track.fun.Playback;
import cz.kruch.track.configuration.Config;

import api.file.File;
import api.io.BufferedInputStream;
import api.lang.Int;
import api.location.QualifiedCoordinates;

import javax.microedition.lcdui.AlertType;
import javax.microedition.io.Connector;

public class ControlledInterp extends Interp {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("ControlledInterp");
//#endif

    public interface Lookup {
        public Thing get(String varname, int units);
    }

    public interface Proxy {
        public ControlledInterp getInterp();
    }

    String basedir;
    Hashtable orphanes;

    private Lookup fallback;
    private Hashtable codes;
    private Int key, units;

    public ControlledInterp(String basedir, boolean background) throws HeclException {
        super();
        this.basedir = basedir;
        this.codes = new Hashtable(16);
        this.key = new Int(0);

        // extensions
//#ifndef __NO_NET__
        org.hecl.net.HttpCmd.load(this);
//#endif

        // internal commands
        addCommand("var", new VarCmd()); // stateful variables support
        addCommand("distance", new InternalCmds(InternalCmds.CMD_DISTANCE, -1, -1));
        addCommand("puts", new InternalCmds(InternalCmds.CMD_PUTS, -1, -1));
        addCommand("play", new InternalCmds(InternalCmds.CMD_PLAY, -1, -1));

        // removed useless and dangerous
        removeCommand("after");
        removeCommand("exit");

        // process mode
        if (background) {
//            super.start(); // = Thread.start();
            throw new IllegalStateException("Background execution not supported");
        }
    }

    public void addFallback(final Lookup fallback, final Int units) {
        this.fallback = fallback;
        this.units = units;
    }

    public void addOrphans(final Hashtable orphans) {
        this.orphanes = orphans;
    }

    public void optimize() {
        if (Config.heclOpt > 1) {
            for (Enumeration seq = commands.keys(); seq.hasMoreElements(); ) {
                final Object key = seq.nextElement();
                if (commands.get(key) instanceof org.hecl.Proc) {
                    try {
                        ((org.hecl.Proc) commands.get(key)).compile(this);
                    } catch (HeclException e) {
//#ifdef __LOG__
                        cz.kruch.track.util.Logger.out("failed to precompile " + key);
                        e.printStackTrace();
//#endif
                    }
                }
            }
        }
    }

    public void loadUserScripts(final String folder) throws IOException, HeclException {
        File dir = null;

        try {
            // open stores directory
            dir = File.open(folder);

            // parse handlers
            if (dir.exists()) {

                // load all scripts for given folder
                for (final Enumeration e = dir.list(); e.hasMoreElements(); ) {
                    final String filename = (String) e.nextElement();
                    if (File.isOfType(filename, ".hcl")) {

                        // load script from file
                        loadUserScript(folder, File.idenFix(filename));
                    }
                }
            }
        } finally {
            // close dir
            File.closeQuietly(dir);
        }
    }

    public void loadUserScript(final String folder, final String filename) throws IOException, HeclException {
        evalNoncaching(new Thing(loadAsText(folder + filename)));
    }

    public NakedVector getHandlers(final String eventName) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("find handlers for event: " + eventName);
//#endif

        NakedVector v = null;
        for (final Enumeration e = commands.keys(); e.hasMoreElements(); ) {
            final String name = (String) e.nextElement();
            if (name.endsWith(eventName)) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("found handler: " + name);
//#endif
                if (v == null) {
                    v = new NakedVector(4, 2);
                }
                v.addElement(commands.get(name));
            }
        }

        return v;
    }

    /* @overriden we want threading control */
    public synchronized void start() {
        // do nothing
    }

    /* @overriden we want threading control */
    public synchronized void yield() {
        // do nothing
    }

    /* @overriden */
    public synchronized Thing eval(final Thing in) throws HeclException {
        key.setValue(in.hashCode());
        CodeThing thing = (CodeThing) codes.get(key);
        if (thing == null) {
            codes.put(key._clone(), thing = CodeThing.get(this, in));
        }
        return thing.run(this);
    }

    /* @overriden */
    public synchronized Thing getVar(final String varname, final int level) throws HeclException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp get var: " + varname);
//#endif

        if (fallback != null) {
            final Thing builtin = fallback.get(varname, units.intValue());
            if (builtin != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("interp " + varname + " is built-in var: " + builtin);
//#endif
                return builtin;
            }
        }

        Thing result = null;
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp get " + varname);
//#endif
        if (super.existsVar(varname, level)) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp var " + varname + " found at level " + (level < 0 ? stack.size() - 1 : level));
//#endif
            result = super.getVar(varname, level);
        } else if (super.existsVar(varname, 0)) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp var " + varname + " trying for global");
//#endif
            result = super.getVar(varname, 0);
        }
/*
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp getvar " + varname + " " + level);
//#endif
        if (level == -1) {
            Thing res = (Thing) ((Hashtable) stack.elementAt(0)).get(varname);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp is " + varname + " global? " + (res != null) + "; " + res);
//#endif
            if (res != null) {
                return res;
            } else if (fallback != null) {
                res = fallback.get(varname);
                if (res != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("interp " + varname + " is built-in");
//#endif
                    return res;
                }
            }
        }

        return super.getVar(varname, level);
*/
        return result;
    }

    /* @overriden */
    public synchronized void setVar(final String varname, final Thing value, final int level) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp setvar " + varname + " " + level + " to " + value);
//#endif
        if (super.existsVar(varname, level)) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp set var " + varname + " at level " + level);
//#endif
            super.setVar(varname, value, level);
        } else if (super.existsVar(varname, 0)) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp set var " + varname + " at level 0");
//#endif
            super.setVar(varname, value, 0);
        } else {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp set new local var " + varname);
//#endif
            super.setVar(varname, value, level);
        }
/*
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp setvar " + varname + " " + level + " to " + value);
//#endif
        if (level == -1) {
            final Hashtable lookup = (Hashtable) stack.elementAt(0);
            final Thing res = (Thing) lookup.get(varname);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp is " + varname + " global? " + (res != null) + "; " + res);
//#endif
            if (res != null) {
                lookup.put(varname, value);
                return;
            }
        }
        
        super.setVar(varname, value, level);
*/
    }

    public synchronized Thing resolveVar(final String varname) throws HeclException {
        if (super.existsVar(varname, 0)) {
            return super.getVar(varname, 0);
        }
        return null;
    }

    public synchronized Thing evalNoncaching(final Thing in) throws HeclException {
        return super.eval(in);
    }

    private static String loadAsText(final String filename) throws IOException {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(new BufferedInputStream(Connector.openInputStream(filename), 4096));
            int sol = -1;
            boolean comment = false;
            final char[] buffer = new char[1024];
            final StringBuffer sb = new StringBuffer(4096);
            int c = reader.read(buffer);
            while (c != -1) {
                if (Config.heclOpt > 0) {
                    for (int i = 0; i < c; i++) {
                        switch (buffer[i]) {
                            case '\t': // whitespace
                                break;
                            case '\n': // eol
                                if (sol >= 0) {
                                    sb.append(buffer, sol, i - sol + 1);
                                    sol = -1;
                                }
                                if (comment) {
                                    comment = false;
                                }
                                break;
                            case '\r': // ignore
                                break;
                            case ' ':  // whitespace
                                break;
                            case '#':
                                if (sol < 0) { // start of line
                                    comment = true;
                                }
                                break;
                            default:
                                if (sol < 0 && !comment) {
                                    sol = i;
                                }
                        }
                    }
                    if (sol >= 0) {
                        sb.append(buffer, sol, c - sol);
                        sol = 0;
                    }
                } else {
                    sb.append(buffer, 0, c);
                }
                c = reader.read(buffer);
            }
            return sb.toString();
        } finally {
            try {
                reader.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private static final class VarCmd implements org.hecl.Command {
//#ifdef __LOG__
        private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("ControlledInterp");
//#endif

        /* to avoid $1 */
        public VarCmd() {
        }

        public Thing cmdCode(Interp interp, Thing[] argv) throws HeclException {
            final String var = argv[1].toString();
            if (interp.existsVar(var, 0)) { // var already exists?
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("var " + var + " already declared");
//#endif
            } else { // var does not exists - declare it global
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("var " + var + " being declared with value " + argv[2].getVal().getStringRep());
//#endif
                interp.setVar(var, argv[2], 0);
                if (interp instanceof ControlledInterp) {
                    final Hashtable orphanes = ((ControlledInterp) interp).orphanes;
                    if (orphanes != null && orphanes.containsKey(var)) {
                        interp.getVar(var, 0).setVal((RealThing) orphanes.get(var));
                    }
                }
            }
            return null;
        }
    }

    private static final class InternalCmds extends org.hecl.Operator {
        static final int CMD_DISTANCE   = 0;
        static final int CMD_PUTS       = 1;
        static final int CMD_PLAY       = 2;

        public InternalCmds(int cmdcode, int minargs, int maxargs) {
            super(cmdcode, minargs, maxargs);
        }

        public Thing operate(int cmdcode, Interp interp, Thing[] argv) throws HeclException {
            Thing result = null;

            switch (cmdcode) {
                case CMD_DISTANCE: {
                    final double lat1 = ((NumberThing) argv[1].getVal()).doubleValue();
                    final double lon1 = ((NumberThing) argv[2].getVal()).doubleValue();
                    final double lat2 = ((NumberThing) argv[3].getVal()).doubleValue();
                    final double lon2 = ((NumberThing) argv[4].getVal()).doubleValue();
                    result = FloatThing.create(QualifiedCoordinates.distance(lat1, lon1, lat2, lon2));
                } break;
                case CMD_PUTS: {
                    // does nothing
                    // TODO dangerous
                    System.out.println(argv[1]);
                } break;
                case CMD_PLAY: {
                    Playback.play(((ControlledInterp) interp).basedir, argv[1].toString(), null, AlertType.WARNING);
                } break;
                default:
                    throw new HeclException("Unknown command '"
                                            + argv[0].toString() + "' with code '"
                                            + cmdcode + "'.");
            }

            return result;
        }
    }
}

//#endif
