package api.lang;

public final class Int {
    private int value;

    public Int(int value) {
        this.value = value;
    }

    public Int clone() {
        return new Int(value);
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int hashCode() {
        return value;
    }

    public boolean equals(Object object) {
        if (object instanceof Int) {
            return ((Int) object).value == value;
        }
        return false;
    }

    public String toString() {
        return Integer.toString(value);
    }
}
