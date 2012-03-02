package api.lang;

public final class Int {
    private int value;

    public Int(int value) {
        this.value = value;
    }

    public int intValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int inc() {
        return ++value;
    }

    public int dec() {
        return --value;
    }

    public Int _clone() {
        return new Int(value);
    }

    public int hashCode() {
        return value;
    }

    public boolean equals(Object object) {
        return object instanceof Int && ((Int) object).value == value;
    }

    public String toString() {
        return Integer.toString(value);
    }
}
