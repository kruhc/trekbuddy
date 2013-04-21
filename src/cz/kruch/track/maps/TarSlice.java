// @LICENSE@

package cz.kruch.track.maps;

/**
 * Represents map tile in a tar.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class TarSlice extends Slice {

    private int blockOffset;

    TarSlice() {
        this.blockOffset = -1;
    }

    public int getBlockOffset() {
        return blockOffset;
    }

    public void setBlockOffset(final int blockOffset) {
        this.blockOffset = blockOffset;
    }
}
