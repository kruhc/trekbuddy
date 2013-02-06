package cz.kruch.track.util;

public final class GpxVector extends NakedVector {

    private NakedVector ranges;
    private int[] currr;

    public GpxVector(int initialCapacity, int capacityIncrement) {
        super(initialCapacity, capacityIncrement);
    }

    public GpxVector(NakedVector master) {
        super(master);
    }

    public void startSegment() {
        if (currr != null) {
            throw new IllegalStateException("range is opened");
        }
        currr = new int[2];
        currr[0] = size();
    }

    public void endSegment() {
        if (currr == null) {
            throw new IllegalStateException("range is not opened");
        }
        currr[1] = size();
        ensureRanges().addElement(currr);
        currr = null; // needed for consistency check
    }

    public boolean hasTracks() {
        return ranges != null && ranges.size() > 0;
    }

    public NakedVector getRanges() {
        return ranges;
    }

    private NakedVector ensureRanges() {
        if (ranges == null) {
            ranges = new NakedVector(16, 16);
        }
        return ranges;
    }
}
