// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.event;

public interface Callback {
    public void invoke(Object result, Throwable throwable);
}
