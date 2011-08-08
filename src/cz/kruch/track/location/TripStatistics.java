// @LICENSE@

package cz.kruch.track.location;

import api.location.Location;
import api.location.QualifiedCoordinates;

/**
 * Trip statistics helper.
 *
 * @author kruhc@seznam.cz
 */
public final class TripStatistics {
    public static final int TERM_SHORT          = 0;
    public static final int TERM_LONG           = 1;
    
    public static final int HISTORY_DEPTH       = 15;
    public static final int HISTORY_DEPTH_MIN   = 5;

    public static final Location[][] locations = {
            new Location[HISTORY_DEPTH],
            new Location[HISTORY_DEPTH]
    };

    public static int[] counts = { 0, 0 };
    public static int[] positions = { 0, 0 };

    public static void reset() {
        for (int i = 2; --i >= 0; ) {
            final Location[] array = TripStatistics.locations[i];
            for (int j = HISTORY_DEPTH; --j >= 0; ) {
                array[j] = null; // gc hint
            }
            counts[i] = positions[i] = 0;
        }
    }

    public static void locationUpdated(final Location l) {
        // update array
        append(TERM_SHORT, l._clone());

        // recalc
        recalc(l.getTimestamp());
    }

    public static Location getLast(final int term) {
        if (counts[term] > 0) {
            return locations[term][positions[term]];
        }
        return null;
    }

    private static void append(final int term, final Location l) {
        // update term position
        int position = positions[term];
        if (++position == HISTORY_DEPTH) {
            position = 0;
        }
        positions[term] = position;

        // release previous
        final Location[] array = locations[term];
        if (array[position] != null) {
            Location.releaseInstance(array[position]);
            array[position] = null; // gc hint
        }

        // save location
        array[position] = l;

        // update term counter
        int count = TripStatistics.counts[term];
        if (++count > HISTORY_DEPTH) {
            count = HISTORY_DEPTH;
        }
        counts[term] = count;
    }

    private static void recalc(final long timestamp) {
        // local ref for faster access
        final Location[] array = locations[0];

        // calc avg values
        double latAvg = 0D, lonAvg = 0D;
        float courseAvg = 0F, accuracySum = 0F, wSum = 0F/*, altAvg = 0F*/;
        int c = 0/*, satSum = 0*/;

        // calculate avg qcoordinates
        for (int i = HISTORY_DEPTH; --i >= 0; ) {
            final Location l = array[i];
            if (l != null) {
                final QualifiedCoordinates qc = l.getQualifiedCoordinates();
                float hAccuracy = qc.getHorizontalAccuracy();
                if (Float.isNaN(hAccuracy)) {
                    hAccuracy = 50 * 5; // max hdop 50 * 5 m
                } else if (hAccuracy == 0F) { // shit happens
                    hAccuracy = 5;
                }
                final float w = 5F / hAccuracy;
                accuracySum += hAccuracy;
                latAvg += qc.getLat() * w;
                lonAvg += qc.getLon() * w;
                wSum += w;
                c++;
                final float course = l.getCourse();
                if (!Float.isNaN(course)) {
                    courseAvg += course * w;
                }
            }
        }
        if (c > HISTORY_DEPTH_MIN) {
            latAvg /= wSum;
            lonAvg /= wSum;
            courseAvg /= wSum;
            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(latAvg, lonAvg);
            qc.setHorizontalAccuracy(accuracySum / c);
            final Location l = Location.newInstance(qc, timestamp, 1);
            l.setCourse(courseAvg);
            append(TERM_LONG, l);
        }
    }
}
