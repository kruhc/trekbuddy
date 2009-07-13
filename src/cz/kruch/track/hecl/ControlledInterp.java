// @LICENSE@

package cz.kruch.track.hecl;

import org.hecl.Interp;
import org.hecl.HeclException;
import org.hecl.Thing;
import org.hecl.NumberThing;
import org.hecl.FloatThing;
import org.hecl.StringThing;

import java.util.Enumeration;

import cz.kruch.track.util.NakedVector;
import cz.kruch.track.fun.Playback;
import cz.kruch.track.configuration.Config;
import api.location.QualifiedCoordinates;

import javax.microedition.lcdui.AlertType;

public final class ControlledInterp extends Interp {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("ControlledInterp");
//#endif

    public interface Lookup {
        public Thing get(String varname);
    }

    private Lookup fallback;

    public ControlledInterp(boolean background) throws HeclException {
        super();

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
            super.start(); // Thread.start();
        }
    }

    public void addFallback(Lookup fallback) {
        this.fallback = fallback;
    }

    public NakedVector getHandlers(String eventName) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("find handlers for event: " + eventName);
//#endif

        NakedVector v = null;
        for (Enumeration e = commands.keys(); e.hasMoreElements(); ) {
            final String name = (String) e.nextElement();
            if (name.endsWith(eventName)) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("found handler: " + name);
//#endif
                if (v == null) {
                    v = new NakedVector(4, 4);
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

    /* @overriden */
    public synchronized Thing getVar(String varname, int level) throws HeclException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp get var: " + varname);
//#endif
        if (fallback != null) {
            final Thing res = fallback.get(varname);
            if (res != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("interp " + varname + " is built-in var: " + res);
//#endif
                return res;
            }
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp get " + varname);
//#endif
        if (super.existsVar(varname, level)) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp var " + varname + " found at level " + (level < 0 ? stack.size() - 1 : level));
//#endif
            return super.getVar(varname, level);
        } else {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp var " + varname + " trying for global");
//#endif
            return super.getVar(varname, 0);
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
    }

    /* @overriden */
    public synchronized void setVar(String varname, Thing value, int level) {
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
                if (log.isEnabled()) log.debug("varcmd " + var + " already declared");
//#endif
            } else { // var does not exists - declare it global
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("varcmd " + var + " being declared");
//#endif
                interp.setVar(var, argv[2], 0);
//                interp.setVar(var, Interp.GLOBALREFTHING, -1);
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
                } break;
                case CMD_PLAY: {
                    if (!Playback.play(Config.getFolderURL(Config.FOLDER_PROFILES) + argv[1].toString())) {
                        AlertType.WARNING.playSound(cz.kruch.track.ui.Desktop.display);
                    }
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
