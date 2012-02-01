package cz.kruch.track.ui;

import cz.kruch.track.util.NakedVector;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;

final class ExtList extends List implements UiList {
    private static final int AWPT_ICON_SIZE = 12;

    private NakedVector items;
    private Image awpt;

    private int marked;

    public ExtList(String title, int listType) {
        super(title, listType);
    }

    public ExtList(String title, int listType, String[] stringElements, Image[] imageElements) {
        super(title, listType, stringElements, imageElements);
        this.marked = -1;
        int awptSize;
        try {
            awptSize = getFont(0).getHeight();
        } catch (Throwable t) { // happens on Android
//#ifdef __ANDROID__
            if (Desktop.screen.getHeight() > 320 || Desktop.screen.getWidth() > 320)
//#else
            if (Desktop.screen.getHeight() > 480 || Desktop.screen.getWidth() > 480)
//#endif
            { // hi-res display
                awptSize = 24;
            } else {
                awptSize = 16;
            }
        }
        if (awptSize < NavigationScreens.wptSize2 << 1) {
            this.awpt = NavigationScreens.resizeImage(NavigationScreens.waypoint,
                                                      awptSize, awptSize,
                                                      NavigationScreens.SLOW_RESAMPLE);
        } else {
            this.awpt = NavigationScreens.waypoint;
        }
    }

    public Displayable getUI() {
        return this;
    }

    public void setData(NakedVector data) {
        this.items = data;
    }

    public Object getSelectedItem() {
        final int selected = getSelectedIndex();
        if (selected >= 0) {
            if (items == null) { // HACK when used as ordinary List
                return getString(getSelectedIndex());
            } else {
                return items.elementAt(getSelectedIndex());
            }
        }
        return null;
    }

    public void setSelectedItem(Object item) {
        setSelectedIndex(indexOf(item), true);
    }

    public void setSelectedIndex(int elementNum, boolean select) {
        super.setSelectedIndex(elementNum, select);
    }

    public void setMarked(int elementNum) {
        if (marked >= 0 && marked < size()) {
            set(marked, getString(marked), null);
        }
        if (elementNum >= 0 && elementNum < items.size()) {
            marked = elementNum;
            set(elementNum, getString(elementNum), awpt);
        }
    }

    public int indexOf(Object item) {
        final Object[] items = this.items.getData();
        for (int i = this.items.size(); --i >= 0; ) {
            if (item.equals(items[i])) {
                return i;
            }
        }
        return -1;
    }

    public void repaint() {
    }
}
