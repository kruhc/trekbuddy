// @LICENSE@

package cz.kruch.track.util;

import java.util.Vector;
import java.util.NoSuchElementException;

/**
 * Naked version of java.util.Vector.
 * <ul>
 * <li>unsynchronized access</li>
 * <li>provides direct access to element array</li>
 * <ul>
 */
public class NakedVector extends Vector {

    public NakedVector(int initialCapacity) {
        super(initialCapacity);
    }

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
    
    /*
     * Unsynchronized versions of Vector's methods.
     */

    public int size() {
        return super.elementCount;
    }

    public boolean isEmpty() {
        return super.elementCount == 0;
    }

    public boolean containsReference(Object object) {
        final Object[] elementData = super.elementData;
        for (int i = 0, N = super.elementCount; i < N; i++) {
            if (object == elementData[i]) {
                return true;
            }
        }
        return false;
    }

    public void addElement(Object object) {
        final int nc = super.elementCount + 1;
        if (nc > super.elementData.length) {
            super.ensureCapacity(nc);
        }
        super.elementData[super.elementCount++] = object;
    }

    public void insertElementAt(Object object, int index) {
        final int nc = super.elementCount + 1;
        if (index >= nc) {
            throw new ArrayIndexOutOfBoundsException(index + " > " + super.elementCount);
        }
        if (nc > elementData.length) {
            super.ensureCapacity(nc);
        }
        System.arraycopy(elementData, index, elementData, index + 1, super.elementCount - index);
        elementData[index] = object;
        super.elementCount += 1;
    }

    public Object firstElement() {
        if (super.elementCount == 0) {
            throw new NoSuchElementException();
        }
        return super.elementData[0];
    }

    public Object lastElement() {
        if (super.elementCount == 0) {
            throw new NoSuchElementException();
        }
        return super.elementData[super.elementCount - 1];
    }

    public Object elementAt(int index) {
        if (index >= super.elementCount) {
          throw new ArrayIndexOutOfBoundsException(index + " >= " + super.elementCount);
        }
        if (index < 0) {
          throw new ArrayIndexOutOfBoundsException(index);
        }
        return super.elementData[index];
    }

    public void removeElementAt(int index) {
        if (index >= super.elementCount) {
          throw new ArrayIndexOutOfBoundsException(index + " >= " + super.elementCount);
        }
        if (index < 0) {
          throw new ArrayIndexOutOfBoundsException(index);
        }
        final Object[] elementData = super.elementData;
        final int j = super.elementCount - index - 1;
        if (j > 0) {
          System.arraycopy(elementData, index + 1, elementData, index, j);
        }
        super.elementCount -= 1;
        elementData[super.elementCount] = null;
    }

    public void removeAllElements() {
        final Object[] elementData = super.elementData;
        for (int i = 0, N = super.elementCount; i < N; i++) {
            elementData[i] = null;
        }
        super.elementCount = 0;
    }

    public void setElementAt(Object object, int index) {
        if (index >= super.elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + super.elementCount);
        }
        super.elementData[index] = object;
    }
}
