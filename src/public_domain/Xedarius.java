/*
 * mMath.java
 *
 * Created on 14 January 2006, 20:00
 *
 * (c) 2006 Richard Carless
 *
 * Fills holes in the java maths library
 *
 * ----------------------------------------
 * 2007-04-19: source URL is http://discussion.forum.nokia.com/forum/showthread.php?t=72840
 * 2007-04-19: modified by kruhc@seznam.cz
 * ----------------------------------------
 */

package public_domain;

import java.lang.Math;

public final class Xedarius {

    // constants
    private static final double sq2p1 = 2.414213562373095048802e0;
    private static final double sq2m1 = 0.414213562373095048802e0;
    private static final double p4 = 0.161536412982230228262e2;
    private static final double p3 = 0.26842548195503973794141e3;
    private static final double p2 = 0.11530293515404850115428136e4;
    private static final double p1 = 0.178040631643319697105464587e4;
    private static final double p0 = 0.89678597403663861959987488e3;
    private static final double q4 = 0.5895697050844462222791e2;
    private static final double q3 = 0.536265374031215315104235e3;
    private static final double q2 = 0.16667838148816337184521798e4;
    private static final double q1 = 0.207933497444540981287275926e4;
    private static final double q0 = 0.89678597403663861962481162e3;
    private static final double PIO2 = Math.PI / 2;
    private static final double V2   = 1D / Math.sqrt(2D);

    // reduce
    private static double mxatan(final double arg) {
        double argsq, value;

        argsq = arg * arg;
        value = ((((p4 * argsq + p3) * argsq + p2) * argsq + p1) * argsq + p0);
        value = value / (((((argsq + q4) * argsq + q3) * argsq + q2) * argsq + q1) * argsq + q0);

        return value * arg;
    }

    // reduce
    private static double msatan(final double arg) {
        if (arg < sq2m1)
            return mxatan(arg);
        if (arg > sq2p1)
            return PIO2 - mxatan(1 / arg);
        return PIO2 / 2 + mxatan((arg - 1) / (arg + 1));
    }

    // implementation of atan
    public static double atan(final double arg) {
        if (arg > 0)
            return msatan(arg);
        return -msatan(-arg);
    }

    // implementation of atan2
    public static double atan2(double arg1, final double arg2) {
        if (arg1 + arg2 == arg1) {
            if (arg1 >= 0)
                return PIO2;
            return -PIO2;
        }
        arg1 = atan(arg1 / arg2);
        if (arg2 < 0) {
            if (arg1 <= 0)
                return arg1 + Math.PI;
            return arg1 - Math.PI;
        }

        return arg1;
    }

    // implementation of asin
    public static double asin(double arg) {
        double temp;
        int sign;

        sign = 0;
        if (arg < 0) {
            arg = -arg;
            sign++;
        }
        if (arg > 1)
            return Double.NaN;
        temp = Math.sqrt(1 - arg * arg);
        if (arg > V2)
            temp = PIO2 - atan(temp / arg);
        else
            temp = atan(arg / temp);
        if (sign > 0)
            temp = -temp;

        return temp;
    }

    // implementation of acos
    public static double acos(final double arg) {
        if (arg > 1 || arg < -1)
            return Double.NaN;
        return PIO2 - asin(arg);
    }

    private Xedarius() {
    }
}