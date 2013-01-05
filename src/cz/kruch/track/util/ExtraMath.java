// @LICENSE@

package cz.kruch.track.util;

/**
 * Implementations of math function that are missing in CLDC.
 * <p>
 * <tt>ln</tt>, <tt>pow</tt> - based on great article by <b>Jacques Laporte</b> (<i>http://www.jacques-laporte.org/</i>)
 * </p>
 * <p>
 * <tt>asin</tt>, <tt>acos</tt>, <tt>atan</tt> ... - adapted from post
 * by user 'xedarius' at Nokia developer forum (<i>http://discussion.forum.nokia.com/forum/showthread.php?t=72840</i>)
 * </p>
 *
 * @author Richard Carless (xedarius at Nokia forum)
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class ExtraMath {

    private ExtraMath() {
    }

    public static int grade(final double d) {
        int i = 1;
        while ((d / i) >= 10) {
            i *= 10;
        }

        return i;
    }

    public static long round(final double d) {
/*
        int i = (int) d;
        final double v = d - i;
        if (v > 0.5D) {
            i++;
        } else if (v < -0.5D) {
            i--;
        }

        return i;
*/
        return (long) Math.floor(d + 0.5D);
    }

    public static int round(final float f) {
        return (int) Math.floor(f + 0.5F);
    }

    /****************************************
     *               ln, pow                *
     ****************************************/

//#ifdef __ANDROID__

    public static double ln(double value) {
        return Math.log(value);
    }

    public static double pow(final double arg1, final double arg2) {
        return Math.pow(arg1, arg2);
    }

//#elifdef __RIM50__

    public static double ln(double value) {
        return net.rim.device.api.util.MathUtilities.log(value);
    }

    public static double pow(final double arg1, final double arg2) {
        return net.rim.device.api.util.MathUtilities.pow(arg1, arg2);
    }

//#else

    private static final double[] N = {
            2,
            1.1,
            1.01,
            1.001,
            1.0001,
            1.00001,
            1.000001,
            1.0000001,
            1.00000001,
            1.000000001,
            1.0000000001,
            1.00000000001,
            1.000000000001,
            1.0000000000001
    };
    private static final double[] LN = {
            0.69314718055994531941723212145818,
            0.095310179804324860043952123280765,
            0.0099503308531680828482153575442607,
            9.9950033308353316680939892053501e-4,
            9.9995000333308335333166680951131e-5,
            9.9999500003333308333533331666681e-6,
            9.9999950000033333308333353333317e-7,
            9.9999995000000333333308333335333e-8,
            9.9999999500000003333333308333334e-9,
            9.9999999950000000033333333308333e-10,
            9.9999999995000000000333333333308e-11,
            9.9999999999500000000003333333333e-12,
            9.9999999999950000000000033333333e-13,
            9.9999999999995000000000000333333e-14
        };
    private static final double LN10 = 2.3025850929940456840179914546844;

    public static double ln(double value) {
        if (value >= 0D) {
            double fix = 0D;
            while (value < 1D) {
                value *= 10;
                fix -= LN10;
            }
            while (value > 10D) {
                value /= 10;
                fix += LN10;
            }

            final double[] N = ExtraMath.N;
            final double[] LN = ExtraMath.LN;
            double result = ExtraMath.LN10;
            double inter = value;

            for (int n = N.length, i = 0; i < n; ) {
                final double interi = inter * N[i];
                if (interi > 10D) {
                    i++;
                } else {
                    inter *= N[i];
                    result -= LN[i];
                }
            }

            return result + fix;
        }

        throw new IllegalArgumentException("ln(" + value + ")");
    }

    public static double pow(final double arg1, final double arg2) {
        if (arg1 == 0D) {
            return 0D;
        }
        if (arg2 == 0D) {
            return 1D;
        }

        final double[] N = ExtraMath.N;
        final double[] LN = ExtraMath.LN;
        final double lnresult = arg2 * ln(arg1);
        double result = 1D;
        double inter = lnresult;

        if (lnresult < 0D) {
            for (int n = N.length, i = 1; i < n; ) {
                final double interi = inter + LN[i];
                if (interi > 0D) {
                    i++;
                } else {
                    inter += LN[i];
                    result /= N[i];
                }
            }
        } else {
            for (int n = N.length, i = 1; i < n; ) {
                final double interi = inter - LN[i];
                if (interi < 0D) {
                    i++;
                } else {
                    inter -= LN[i];
                    result *= N[i];
                }
            }
        }

        return result;
    }

//#endif

    public static double powi(double num, int exp) {
        double result = 1D;

        while (exp > 0) {
            if (exp % 2 == 1)
                result *= num;
            exp >>= 1;
            num *= num;
        }

        return result;
    }

    /****************************************
     *          asin, acos, atan, ...       *
     ****************************************/

//#ifdef __ANDROID__

    public static double atan(final double arg) {
        return Math.atan(arg);
    }

    public static double atan2(double arg1, final double arg2) {
        return Math.atan2(arg1, arg2);
    }

    public static double asin(double arg) {
        return Math.asin(arg);
    }

    public static double acos(final double arg) {
        return Math.acos(arg);
    }

//#elifdef __RIM50__

    public static double atan(final double arg) {
        return net.rim.device.api.util.MathUtilities.atan(arg);
    }

    public static double atan2(double arg1, final double arg2) {
        return net.rim.device.api.util.MathUtilities.atan2(arg1, arg2);
    }

    public static double asin(double arg) {
        return net.rim.device.api.util.MathUtilities.asin(arg);
    }

    public static double acos(final double arg) {
        return net.rim.device.api.util.MathUtilities.acos(arg);
    }
    
//#else

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

//#endif
    
}
