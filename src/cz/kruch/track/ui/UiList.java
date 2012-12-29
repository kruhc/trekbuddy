package cz.kruch.track.ui;

import cz.kruch.track.util.NakedVector;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Ticker;

interface UiList {
    Displayable getUI();
    public void repaint();
    void setData(NakedVector items);

    void addCommand(Command cmd);
    void removeCommand(Command cmd);
    void setCommandListener(CommandListener listener);
    void setSelectCommand(Command cmd);

    Object getSelectedItem();
    void setSelectedItem(Object item, boolean highlight);

    int getSelectedIndex();
    void setSelectedIndex(int elementNum, boolean select);
    void setMarked(int elementNum);

    void delete(int elementNum);
    int indexOf(Object item);
    int size();
    void setFitPolicy(int policy);

    boolean isShown();
    Ticker getTicker();
}
