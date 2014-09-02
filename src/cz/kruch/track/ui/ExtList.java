package cz.kruch.track.ui;

import cz.kruch.track.util.NakedVector;
import cz.kruch.track.util.ImageUtils;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;

final class ExtList extends List implements UiList {

    private NakedVector items;
    private Image awpt, selected;

    private int marked, selectedOld;

    public ExtList(String title, int listType) {
        super(title, listType);
    }

    public ExtList(String title, int listType, String[] stringElements, Image[] imageElements) {
        super(title, listType, stringElements, imageElements);
        this.marked = this.selectedOld = -1;
        int iconSize;
        try {
            iconSize = getFont(0).getHeight();
        } catch (Throwable t) { // happens on Android, because of incomplete List implementation
            iconSize = 16 + Desktop.getHiresLevel() * 8;
        }
        if (iconSize < NavigationScreens.wptSize2 << 1) {
            this.awpt = ImageUtils.resizeImage(NavigationScreens.waypoint,
                                               iconSize, iconSize,
                                               ImageUtils.SLOW_RESAMPLE, false);
        } else {
            this.awpt = NavigationScreens.waypoint;
        }
        if (iconSize < NavigationScreens.selectedSize2 << 1) {
            this.selected = ImageUtils.resizeImage(NavigationScreens.selected,
                                                   iconSize, iconSize,
                                                   ImageUtils.SLOW_RESAMPLE, false);
        } else {
            this.selected = NavigationScreens.selected;
        }
    }

    public Displayable getUI() {
        return this;
    }

    public void setData(NakedVector data) {
        this.items = data;
    }

    public void setAll(String[] stringElements) {
//#ifndef __ANDROID__
        for (int i = 0, N = stringElements.length; i < N; i++) {
            set(i, stringElements[i], null);
        }
//#else
        deleteAll();
        appendAll(stringElements, null);
//#endif
    }

    public Object getSelectedItem() {
        final int elementNum = getSelectedIndex();
        if (elementNum >= 0 && elementNum < size()) {
            if (items == null) { // HACK when used as ordinary List
                return getString(elementNum);
            } else {
                return items.elementAt(elementNum);
            }
        }
        return null;
    }

    public void setSelectedIndex(int elementNum, boolean selected) {
        setSelected(elementNum, selected, true);
    }

    public void setSelectedItem(Object item, boolean highlight) {
        final int idx = indexOf(item);
        if (idx > -1) {
            setSelected(idx, true, highlight);
        }
    }

    private void setSelected(int idx, boolean selected, boolean highlight) {
        super.setSelectedIndex(idx, selected);
        if (Config.extListMode == Config.LISTMODE_CUSTOM) {
            if (selectedOld > -1 && selectedOld != marked) {
                set(selectedOld, getString(selectedOld), null);
            }
            selectedOld = idx;
            if (highlight && idx != marked) {
                set(idx, getString(idx), this.selected);
            }
        }
    }

    public void setMarked(int elementNum) {
        // already set?
        if (elementNum == marked) {
            return;
        }
        // unmark previous
        if (marked >= 0 && marked < size()) {
            if (selectedOld == marked && Config.extListMode == Config.LISTMODE_CUSTOM) {
                set(marked, getString(marked), selected);
            } else {
                set(marked, getString(marked), null);
            }
        }
        // marked item
        if (elementNum >= 0 && elementNum < items.size()) {
            marked = elementNum;
            set(elementNum, getString(elementNum), awpt);
        }
    }

    public int indexOf(Object item) {
        if (items != null) {
            final Object[] items = this.items.getData();
            for (int i = this.items.size(); --i >= 0; ) {
                if (item.equals(items[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void repaint() {
    }
}
