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
//    private final QualifiedCoordinates[] coordinatesAvg;
//    private final int[] satAvg;

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
        int position = positions[term];

        // rotate
        if (++position == HISTORY_DEPTH) {
            position = 0;
        }

        // release previous
        final Location[] array = locations[term];
        if (array[position] != null) {
            Location.releaseInstance(array[position]);
            array[position] = null; // gc hint
        }

        // save location
        array[position] = l;

        // update term position
        positions[term] = position;

        // update term counter
        final int[] counts = TripStatistics.counts;
        counts[term]++;
        if (counts[term] > HISTORY_DEPTH) {
            counts[term] = HISTORY_DEPTH;
        }
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
                final float hAccuracy = qc.getHorizontalAccuracy();
                if (!Float.isNaN(hAccuracy)) {
                    final float w = 5F / hAccuracy;
                    accuracySum += hAccuracy;
//                  satSum += l.getSat();
                    latAvg += qc.getLat() * w;
                    lonAvg += qc.getLon() * w;
//                  altAvg += qc.getAlt();
                    wSum += w;
                    c++;
                    final float course = l.getCourse();
                    if (!Float.isNaN(course)) {
                        courseAvg += course * w;
                    }
                }
            }
        }
        if (c > HISTORY_DEPTH_MIN) {
            latAvg /= wSum;
            lonAvg /= wSum;
//            altAvg /= c;
            courseAvg /= wSum;
/*
            QualifiedCoordinates.releaseInstance(coordinatesAvg[0]);
            coordinatesAvg[0] = null; // gc hint
            coordinatesAvg[0] = QualifiedCoordinates.newInstance(latAvg, lonAvg);
            coordinatesAvg[0].setHorizontalAccuracy(accuracySum / c);
*/
//            satAvg[term] = satSum / c;

            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(latAvg, lonAvg);
            qc.setHorizontalAccuracy(accuracySum / c);
            final Location l = Location.newInstance(qc, timestamp, 1);
            l.setCourse(courseAvg);
            append(TERM_LONG, l);
        }

/*
        // set non-avg qcoordinates - it is last position
        QualifiedCoordinates.releaseInstance(coordinatesAvg[1]);
        coordinatesAvg[1] = null; // gc hint
        coordinatesAvg[1] = locations[position].getQualifiedCoordinates().clone();

        // remember number of valid position in array
        count = c;
*/
    }
}
