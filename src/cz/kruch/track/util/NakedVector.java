// @LICENSE@

package cz.kruch.track.util;

import java.util.Vector;

public class NakedVector extends Vector {

    public NakedVector(int initialCapacity, int capacityIncrement) {
        super(initialCapacity, capacityIncrement);
    }

    public NakedVector(NakedVector master) {
        super(master.size(), 0);
        System.arraycopy(master.elementData, 0, super.elementData, 0, master.elementCount);
        super.elementCount = master.elementCount;
    }

    public Object[] getData() {
        return super.elementData;
    }
}
