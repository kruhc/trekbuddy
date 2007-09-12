/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.maps;

/**
 * Represents map tile in a tar.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class TarSlice extends Slice {
    private int blockOffset;

    TarSlice(String path) throws InvalidMapException {
        super(path);
    }

/*
    public TarSlice(CharArrayTokenizer.Token token) throws InvalidMapException {
        super(token);
    }
*/

    int getBlockOffset() {
        return blockOffset;
    }

    void setBlockOffset(int blockOffset) {
        this.blockOffset = blockOffset;
    }
}
