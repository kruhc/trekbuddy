package javax.microedition.lcdui;

public interface Choice {
    public static final int EXCLUSIVE = 1;
    public static final int IMPLICIT = 3;
    public static final int MULTIPLE = 2;
    public static final int POPUP = 4;
    public static final int TEXT_WRAP_DEFAULT = 0;
    public static final int TEXT_WRAP_OFF = 2;
    public static final int TEXT_WRAP_ON = 1;

    int append(String stringPart, Image imagePart);

    void delete(int elementNum);

    void deleteAll();

    void set(int elementNum, String stringPart, Image imagePart);

    public int getSelectedFlags(boolean[] array);

    public void setSelectedFlags(boolean[] array);

    int getSelectedIndex();

    public void setSelectedIndex(int elementNum, boolean selected);

    String getString(int elementNum);

    void setFitPolicy(int fitPolicy);

    public Font getFont(int elementNum);

    void setFont(int elementNum, Font font);

    int size();
}
