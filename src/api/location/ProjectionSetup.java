// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public class ProjectionSetup {
    private String name;

    public ProjectionSetup(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return (new StringBuffer(32)).append(getName()).append('{').append('}').toString();
    }
}
