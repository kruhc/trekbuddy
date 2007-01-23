// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

public final class ExtraMath {
    private static final double SINS[] = new double[90 + 1];
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

    public static void initialize() {
        for (int i = 0; i <= 90; i++) {
            SINS[i] = Math.sin(Math.toRadians(i));
        }
    }

    public static int asin(double sina) {
        if (sina < 0D) {
            throw new IllegalArgumentException("Invalid sin(a) value: " + sina);
        }
        if (sina > 1D) {
            return 90;
        }

        float step = 23;
        int direction = 0;
        int cycles = 0;
        double[] sins = SINS;
        int i = 45;

        for ( ; i >= 0 && i <= 90; ) {
            boolean b;
            if (sins[i] > sina) {
                b = direction != 0 && direction != -1;
                direction = -1;
                i -= step;
            } else if (sins[i] < sina) {
                b = direction != 0 && direction != 1;
                direction = 1;
                i += step;
            } else {
                return i;
            }

            if (step == 1 && b) {
                return i;
            }

            if (!b) {
                step /= 2;
            } else {
                step--;
            }
            if (step < 1) {
                step = 1;
            }

            if (cycles++ > 25) {
                throw new IllegalStateException("asin(a) failure - too many cycles");
            }
        }

        return i;
    }

    public static double ln(double value) {
        if (value < 0D) {
            throw new IllegalArgumentException("ln(" + value + ")");
        }

        double fix = 0D;
        while (value < 1D) {
            value *= 10;
            fix -= LN10;
        }
        while (value > 10D) {
            value /= 10;
            fix += LN10;
        }

        double result = LN10;
        double inter = value;

        for (int n = N.length, i = 0; i < n; ) {
            double interi = inter * N[i];
            if (interi > 10D) {
                i++;
            } else {
                inter *= N[i];
                result -= LN[i];
            }
        }

        return result + fix;
    }

    public static double pow(double arg1, double arg2) {
        if (arg1 == 0D) {
            return 0D;
        }
        if (arg2 == 0D) {
            return 1D;
        }

        double lnresult = arg2 * ln(arg1);
        double result = 1D;
        double inter = lnresult;

        if (lnresult < 0D) {
            for (int i = 1; i < N.length; ) {
                double interi = inter + LN[i];
                if (interi > 0D) {
                    i++;
                } else {
                    inter += LN[i];
                    result /= N[i];
                }
            }
        } else {
            for (int i = 1; i < N.length; ) {
                double interi = inter - LN[i];
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
}
