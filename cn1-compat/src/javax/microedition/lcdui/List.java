package javax.microedition.lcdui;

//#define __XAML__

//#ifdef __XAML__
import com.codename1.ui.FriendlyAccess;
//#else
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.list.DefaultListModel;
import com.codename1.ui.list.DefaultListCellRenderer;
import com.codename1.ui.layouts.BoxLayout;
//#endif

public class List extends Screen implements Choice {
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);

    private Command selectCommand;
    private Font font;
    private Object contextObject;

//#ifdef __XAML__
    private java.util.List<String> stringElements;
    private java.util.List<Image> imageElements;
    private int selectedIndex = -1;
    private Peer peer;
//#else
    private com.codename1.ui.List cn1List;
    private DefaultListModel cn1Model;
//#endif

    public List(String title, int listType) {
        this(title, listType, null, null);
    }

    public List(String title, int listType, String[] stringElements, Image[] imageElements) {
        super.setTitle(title);
//#ifdef __LOG__
        com.codename1.io.Log.p("List.ctor of type " + listType, com.codename1.io.Log.DEBUG);
//#endif
//#ifdef __XAML__
        final int size = stringElements == null ? 0 : stringElements.length;
        this.stringElements = arrayToList(stringElements, size);
        this.imageElements = arrayToList(imageElements, size);
//#else
        if (stringElements != null) {
            this.cn1Model = new DefaultListModel(stringElements);
        } else {
            this.cn1Model = new DefaultListModel();
        }
        this.cn1List = new com.codename1.ui.List(this.cn1Model);
        this.cn1List.addActionListener(this);
        ((DefaultListCellRenderer) this.cn1List.getRenderer()).setShowNumbers(false);
        com.codename1.ui.Form cn1Form = new com.codename1.ui.Form(title);
        cn1Form.getContentPane().setLayout(new BoxLayout(BoxLayout.Y_AXIS));
        cn1Form.addComponent(this.cn1List);
        super.setDisplayable(cn1Form);
//#endif
        this.font = Font.getDefaultFont();
    }

    public Object getContextObject() {
        return contextObject;
    }

    public void setContextObject(Object contextObject) {
        this.contextObject = contextObject;
    }

//#ifdef __XAML__

    public void show() {
        FriendlyAccess.execute("show-list", new Object[]{ this });
    }

    public java.util.List<String> MIDP_getStringElements() {
        return stringElements;
    }

    public java.util.List<Image> MIDP_getImageElements() {
        return imageElements;
    }

    public Command MIDP_getSelectCommand() {
        return selectCommand;
    }

    public void MIDP_setPeer(Peer peer) {
        this.peer = peer;
    }

//#else

    public void actionPerformed(ActionEvent actionEvent) {
//#ifdef __LOG__
        com.codename1.io.Log.p("List.actionPerformed; " + actionEvent.getComponent() + "; " + actionEvent.getCommand() + "; " + actionEvent.getSource(), com.codename1.io.Log.DEBUG);
//#endif
        if (actionEvent.getCommand() == null) { // TODO or actionEvent.getSource() instanceof cn1List???
//#ifdef __LOG__
            com.codename1.io.Log.p("List.actionPerformed - SELECT", com.codename1.io.Log.DEBUG);
//#endif
            getListener().commandAction(selectCommand == null ? SELECT_COMMAND : selectCommand, this);
        } else {
            super.actionPerformed(actionEvent);
        }
    }

//#endif

    public int append(String stringPart, Image imagePart) {
//#ifdef __XAML__
        stringElements.add(stringPart);
        imageElements.add(imagePart);
        if (peer != null)
            peer.append(stringPart, imagePart);
        return stringElements.size() - 1;
//#else
        cn1Model.addItem(stringPart);
        return cn1Model.getSize() - 1;
//#endif
    }

    public void delete(int elementNum) {
//#ifdef __XAML__
        stringElements.remove(elementNum);
        imageElements.remove(elementNum);
        if (peer != null)
            peer.delete(elementNum);
//#else
        cn1Model.removeItem(elementNum);
//#endif
    }

    public void deleteAll() {
//#ifdef __XAML__
        stringElements.clear();
        imageElements.clear();
        if (peer != null)
            peer.deleteAll();
//#else
        cn1Model.removeAll();
//#endif
    }

    public int getSelectedFlags(boolean[] array) {
        com.codename1.io.Log.p("List.getSelectedFlags not implemented", com.codename1.io.Log.ERROR);
        throw new Error("List.getSelectedFlags not implemented");
    }

    public void setSelectedFlags(boolean[] array) {
        com.codename1.io.Log.p("List.setSelectedFlags not implemented", com.codename1.io.Log.ERROR);
        throw new Error("List.setSelectedFlags not implemented");
    }
    
    public int getSelectedIndex() {
//#ifdef __LOG__
        com.codename1.io.Log.p("List.getSelectIndex", com.codename1.io.Log.DEBUG);
//#endif
//#ifdef __XAML__
        return selectedIndex;
//#else
        return cn1List.getSelectedIndex();
//#endif
    }

    public void setSelectedIndex(int elementNum, boolean selected) {
        if (selected) {
//#ifdef __XAML__
            selectedIndex = elementNum;
//#else
            cn1List.setSelectedIndex(elementNum, true); // true to scroll
//#endif
        }
        // suppose we never call selSelectedIndex with false
    }
    
    public String getString(int elementNum) {
//#ifdef __LOG__
        com.codename1.io.Log.p("List.getString; " + elementNum, com.codename1.io.Log.DEBUG);
//#endif
//#ifdef __XAML__
        return stringElements.get(elementNum);
//#else
        return cn1Model.getItemAt(elementNum).toString();
//#endif
    }

    public void set(int elementNum, String stringPart, Image imagePart) {
//#ifdef __XAML__
        stringElements.set(elementNum, stringPart);
        imageElements.set(elementNum, imagePart);
        if (peer != null)
            peer.set(elementNum, stringPart, imagePart);
//#else
        cn1Model.setItem(elementNum, stringPart);
//#endif
    }

    public void setFitPolicy(int fitPolicy) {
        com.codename1.io.Log.p("List.setFitPolicy not implemented", com.codename1.io.Log.WARNING);
    }

    public Font getFont(int elementNum) {
        com.codename1.io.Log.p("List.getFont not implemented yet", com.codename1.io.Log.WARNING);
        return this.font;
    }

    public void setFont(int elementNum, Font font) {
        com.codename1.io.Log.p("List.setFont not implemented yet", com.codename1.io.Log.WARNING);
        this.font = font;
    }

    public void setSelectCommand(Command command) {
        selectCommand = command;
    }

    public int size() {
//#ifdef __XAML__
        return stringElements.size();
//#else
        return cn1Model.getSize();
//#endif
    }

    private static <T> java.util.List<T> arrayToList(final T[] array, final int size) {
        final java.util.ArrayList<T> result = new java.util.ArrayList<T>(size);
        if (array != null) {
            for (T item : array) {
                result.add(item);
            }
        } else {
            for (int i = 0; i < size; i++) {
                result.add(null);
            }
        }
        return result;
    }

//#ifdef __XAML__

    public interface Peer {
        void append(String stringPart, Image imagePart);
        void delete(int itemNum);
        void deleteAll();
        void set(int elementNum, String stringPart, Image imagePart);
    }

//#endif

}
