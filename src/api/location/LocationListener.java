// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public interface LocationListener {
    public void locationUpdated(LocationProvider provider,
                                Location location);

    public void providerStateChanged(LocationProvider provider,
                                     int newState);
}
