// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.util.NakedVector;
import cz.kruch.track.util.ExtraMath;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Canvas;
import java.util.Vector;

final class SmartList extends Canvas {
    private static final int HL_INSET = 2;

    private CommandListener listener;
    private Vector items;

    private int top, selected, marked;
    private int visible;

    private int width, height;
    private int colorBackSel, colorBackUnsel, colorForeSel, colorForeUnsel;

    public SmartList(String title, CommandListener listener) {
        setTitle(title);
        this.listener = listener;
        this.marked = -1;
        if (cz.kruch.track.configuration.Config.safeColors) {
            this.colorBackSel = 0x000000ff;
            this.colorBackUnsel = 0x00ffffff;
            this.colorForeSel = 0x00ffffff;
            this.colorForeUnsel = 0x0;
        } else {
            this.colorBackSel = Desktop.display.getColor(Display.COLOR_HIGHLIGHTED_BACKGROUND);
            this.colorBackUnsel = Desktop.display.getColor(Display.COLOR_BACKGROUND);
            this.colorForeSel = Desktop.display.getColor(Display.COLOR_HIGHLIGHTED_FOREGROUND);
            this.colorForeUnsel = Desktop.display.getColor(Display.COLOR_FOREGROUND);
        }
    }

    public void setData(Vector items) {
        this.items = items;
        sizeChanged(getWidth(), getHeight());
    }

    protected void sizeChanged(int w, int h) {
        width = w;
        height = h;
        setVisibleCount(h / getLineHeight());
        makeSelectedVisible();
        repaint();
    }

    protected void keyPressed(int i) {
        boolean repaint = false;
        switch (i) {
            case Canvas.KEY_NUM2:
            case Canvas.KEY_NUM3:
            case Canvas.KEY_NUM4:
            case Canvas.KEY_NUM5:
            case Canvas.KEY_NUM6:
            case Canvas.KEY_NUM7:
            case Canvas.KEY_NUM8:
            case Canvas.KEY_NUM9:
                // TODO search by name
                break;
            default: {
                switch (getGameAction(i)) {
                    case Canvas.UP: { // one up
                        if (selected > 0) {
                            --selected;
                            repaint = true;
                        }
                    } break;
                    case Canvas.LEFT: { // page up
                        if (selected > 0) {
                            if (selected == top) {
                                selected -= visible;
                                if (selected < 0) {
                                    selected = 0;
                                }
                            } else {
                                selected = top;
                            }
                            repaint = true;
                        }
                    } break;
                    case Canvas.RIGHT: { // page down
                        if (selected < items.size() - 1) {
                            if (selected == top + visible - 1) {
                                selected += visible;
                            } else {
                                selected = top + visible - 1;
                            }
                            if (selected >= items.size()) {
                                selected = items.size() - 1;
                            }
                            repaint = true;
                        }
                    } break;
                    case Canvas.DOWN: { // one down
                        if (selected < items.size() - 1) {
                            ++selected;
                            repaint = true;
                        }
                    } break;
                    case Canvas.FIRE: { // open selected
                        listener.commandAction(List.SELECT_COMMAND, this);
                    } break;
                }
            }
        }

        if (repaint) {
            makeSelectedVisible();
            repaint();
        }
    }

    protected void keyRepeated(int i) {
        switch (getGameAction(i)) {
            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT:
            case Canvas.DOWN:
                keyPressed(i);
                break;
        }
    }

    protected void pointerPressed(int x, int y) {
        final int count = items.size();
        final int h = getHeight();
        final int lines = h / getLineHeight();
        final double yd = lines >= count ? getLineHeight() : (double) h / lines;
        final int line = ExtraMath.round((double) y / yd);

        // select wpt and open it
        if (line + top < count) {
            setSelectedIndex(line + top, true);
            makeSelectedVisible();
            repaint();
            serviceRepaints();
            listener.commandAction(List.SELECT_COMMAND, this);
        }
    }

    protected void paint(Graphics g) {
        final int w = width;
        final int h = height;
        final int selected = this.selected;
        final int marked = this.marked;
        final int count = this.items.size();
        final int lines = h / getLineHeight();
        final double yd = lines >= count ? getLineHeight() : (double) h / lines;
        final int lh = (int) yd;
        final Object[] items = ((NakedVector) this.items).getData();

        int curr = top;
        int last = top + lines;
        double y = 0;

        g.setFont(Desktop.fontLists);
        g.setColor(colorBackUnsel);
        g.fillRect(0, 0, w, h);
        g.setColor(colorForeUnsel);

        while (curr < last && curr < count) {
            final int yy = ExtraMath.round(y);
            if (curr == selected) { // set color for selected item
                g.setColor(colorBackSel);
                g.fillRect(0, yy, w, lh);
                g.setColor(colorForeSel);
            }
            final String s = items[curr].toString();
            if (curr != marked) {
                g.drawString(s, HL_INSET, yy, Graphics.TOP | Graphics.LEFT);
            } else {
                g.drawString("(*) " + s, HL_INSET, yy, Graphics.TOP | Graphics.LEFT);
            }
            if (curr == selected) { // restore color
                g.setColor(colorForeUnsel);
            }
            curr++;
            y += yd;
        }
    }

    public void setVisibleCount(int count) {
        visible = (count > 0 ? count : 1);
    }

    public int size() {
        return items.size();
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int elementNum, boolean select) {
        if (select) {
            if (elementNum >= 0 && elementNum < items.size()) {
                selected = elementNum;
                makeSelectedVisible();
            } else {
                selected = -1;
            }
            repaint();
        } else {
            throw new IllegalArgumentException("Unselect not supported");
        }
    }

    public String getString(int index) {
        return items.elementAt(index).toString();
    }

    public void setMarked(int elementNum) {
        if (elementNum >= 0 && elementNum < items.size()) {
            marked = elementNum;
        }
    }

    private static int getLineHeight() {
        return Desktop.fontLists.getHeight();
    }

    private void makeSelectedVisible() {
        if (selected >= 0) {
            if (selected < top) {
                top = selected;
            } else if (selected - top + 1 > visible) {
                top = selected - visible + 1;
            }
        }
    }
}
