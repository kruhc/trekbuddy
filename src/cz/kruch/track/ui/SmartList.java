// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.util.NakedVector;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Ticker;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;

/**
 * Unlimited UI list.
 * 
 * http://developers.sun.com/mobility/midp/ttips/customcomponent/index.html
 */
final class SmartList extends Canvas implements UiList {
    private static final int HL_INSET = 2;
    private static final int VL_INSET = 3;
    private static final int SHAKE_LIMIT = 10;
    private static final int colorSbBg = 0x00e6e6e6;
    private static final int colorSbHiBorder = 0x00666666;
    private static final int colorSbLoBorder = 0x00999999;
    private static final int colorSbMain = DeviceScreen.BTN_COLOR;
    private static final int colorSbHiMain = DeviceScreen.BTN_HICOLOR;

    private CommandListener listener;
    private NakedVector items;
    private String title;
    private Image awpt;

    private int top, selected, marked;
    private int visible;
    private int awptSize;
    private boolean dragged;

    private int width, height, sbY, sbWidth, sbHeight, pY;
    private int colorBackSel, colorBackUnsel, colorForeSel, colorForeUnsel;

    public SmartList(String title) {
        try {
            super.setTitle(title);
        } catch (Exception e) { // RIM bug???
            this.title = title;
        }
        this.marked = this.selected = -1;
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
        this.awptSize = getLineHeight() - 2 * 2 * VL_INSET;
        if (this.awptSize < NavigationScreens.wptSize2 << 1) {
            this.awpt = NavigationScreens.resizeImage(NavigationScreens.waypoint,
                                                      this.awptSize, this.awptSize,
                                                      NavigationScreens.SLOW_RESAMPLE);
        } else {
            this.awptSize = NavigationScreens.wptSize2 << 1;
            this.awpt = NavigationScreens.waypoint;
        }
    }

    public Displayable getUI() {
        return this;
    }

    public void setData(NakedVector items) {
        if (items == null) {
            throw new NullPointerException("List items is null");
        }
        this.items = items;
    }

    /* magic - it prevents OutOfMemoryError :-O */
    public void setTicker(Ticker ticker) {
        super.setTicker(ticker);
    }

    // TODO
    public void setSelectCommand(Command command) {
        super.addCommand(command);
    }

    public void setCommandListener(CommandListener listener) {
        super.setCommandListener(this.listener = listener);
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

    public Object getSelectedItem() {
        if (selected >= 0 && selected < items.size()) {
            return items.elementAt(selected);
        }
        return null;
    }

    public void setSelectedItem(Object item) {
        setSelectedIndex(indexOf(item), true);
    }

/* REMOVE
    public String getString(int index) {
        return items.elementAt(index).toString();
    }

*/
    public void setMarked(int elementNum) {
        if (elementNum >= 0 && elementNum < items.size()) {
            marked = elementNum;
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

    public int size() {
        return items.size();
    }

    //
    // canvas handling
    //

    protected void showNotify() {
        if (title != null) {
            try {
                super.setTitle(title);
            } catch (Exception e) {
                // ignore
            }
        }
        recalculate(getWidth(), getHeight());
    }

    protected void sizeChanged(int w, int h) {
        recalculate(w, h);
//#ifdef __ANDROID__
        /* does not paint when shown for the first time on Android without this */
        repaint();
//#endif
    }

    protected void keyPressed(int i) {
        boolean repaint = false;
        switch (i) {
            case Canvas.KEY_NUM1:
				selected = 0;
				repaint = true;
				break;
            case Canvas.KEY_NUM2:
				repaint = stepOnto('a', 'A');
				if (!repaint) {
					repaint = stepOnto('b', 'B');
					if (!repaint) {
						repaint = stepOnto('c', 'C');
					}
				}
				break;
            case Canvas.KEY_NUM3:
				repaint = stepOnto('d', 'D');
				if (!repaint) {
					repaint = stepOnto('e', 'E');
					if (!repaint) {
						repaint = stepOnto('f', 'F');
					}
				}
				break;
            case Canvas.KEY_NUM4:
				repaint = stepOnto('g', 'G');
				if (!repaint) {
					repaint = stepOnto('h', 'H');
					if (!repaint) {
						repaint = stepOnto('i', 'I');
					}
				}
				break;
            case Canvas.KEY_NUM5:
				repaint = stepOnto('j', 'J');
				if (!repaint) {
					repaint = stepOnto('k', 'K');
					if (!repaint) {
						repaint = stepOnto('l', 'L');
					}
				}
				break;
            case Canvas.KEY_NUM6:
				repaint = stepOnto('m', 'M');
				if (!repaint) {
					repaint = stepOnto('n', 'N');
					if (!repaint) {
						repaint = stepOnto('o', 'O');
					}
				}
				break;
            case Canvas.KEY_NUM7:
				repaint = stepOnto('p', 'P');
				if (!repaint) {
					repaint = stepOnto('q', 'Q');
					if (!repaint) {
						repaint = stepOnto('r', 'R');
						if (!repaint) {
							repaint = stepOnto('s', 'S');
						}
					}
				}
				break;
            case Canvas.KEY_NUM8:
				repaint = stepOnto('t', 'T');
				if (!repaint) {
					repaint = stepOnto('u', 'U');
					if (!repaint) {
						repaint = stepOnto('v', 'V');
					}
				}
				break;
            case Canvas.KEY_NUM9:
				repaint = stepOnto('w', 'W');
				if (!repaint) {
					repaint = stepOnto('x', 'X');
					if (!repaint) {
						repaint = stepOnto('y', 'Y');
						if (!repaint) {
							repaint = stepOnto('z', 'Z');
						}
					}
				}
				break;
            case Canvas.KEY_POUND:
				selected = items.size() - 1;
				repaint = true;
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
        final int h = height;
        final int lines = h / getLineHeight();

        if (count <= lines || x < width - sbWidth - (HL_INSET << 1)) { // select wpt and open it
            int line = y / getLineHeight();
            if (line > lines) line--;
            if (line + top < count) {
                setSelectedIndex(line + top, true);
                makeSelectedVisible();
                repaint();
                serviceRepaints();
                pY = y;
            }
        } else { // move scrollbar
            if (y < sbY || y > sbY + sbHeight) {
                // TODO quickjump
            } else {
                pY = y;
            }
        }

        dragged = false;
    }

    protected void pointerDragged(int x, int y) {
        final int count = items.size();
        final int lines = height / getLineHeight();
        final int dy = y - pY;

        if (Math.abs(dy) > SHAKE_LIMIT && count > lines) {
            if (x > width - sbWidth - (HL_INSET << 1)) {
                final int dl = cz.kruch.track.util.ExtraMath.round(count * ((float) dy / height));
                if (Math.abs(dl) > 0) {
                    pY = y;
                    top += dl;
                    if (dl > 0) {
                        if (y > sbY + sbHeight) {
                            top++;
                        }
                        if (top + visible > count) {
                            top = count - visible;
                        }
                    } else {
                        if (y < sbY) {
                            top--;
                        }
                        if (top < 0) {
                            top = 0;
                        }
                    }
                    repaint();
                }
            } else {
                final int dl = dy / getLineHeight();
                if (Math.abs(dl) > 0) {
                    pY = y - dy % getLineHeight();
                    top -= dl;
                    if (top >= 0) {
                        if (top + visible > count) {
                            top = count - visible;
                        }
                        repaint();
                    } else {
                        top = 0;
                    }
                }
            }
        }

        if (Math.abs(dy) > SHAKE_LIMIT) { // filter shaking finger
            dragged = true;
        }
    }

    protected void pointerReleased(int x, int y) {
		final int count = items.size();
		final int lines = height / getLineHeight();

        if (lines < count) {
            this.pY = y;
        }
        if (x < width - sbWidth - (HL_INSET << 1) && !dragged) {
            int line = y / getLineHeight();
            if (line > lines) line--;
            if (line + top < count) {
                listener.commandAction(List.SELECT_COMMAND, this);
            }
        }
    }

    protected void paint(Graphics g) {
        final int w = width;
        final int h = height;
        final int selected = this.selected;
        final int marked = this.marked;
        final int count = this.items.size();
        final int lh = getLineHeight();
        final int lines = h / lh;
        final int sbWidth = this.sbWidth;
        final int sbHeight = this.sbHeight;
        final Object[] items = this.items.getData();

        int curr = top;
        int last = top + lines + 1 + 1/* partial */;
        int y = 0;

        g.setFont(Desktop.fontLists);
        g.setColor(colorBackUnsel);
        g.fillRect(0, 0, w, h);
        g.setColor(colorForeUnsel);

        while (curr < last && curr < count) {
            if (curr == selected) { // set color for selected item
                g.setColor(colorBackSel);
                g.fillRect(0, y, w, lh);
                g.setColor(colorForeSel);
            }
            final String s = items[curr].toString();
            if (curr != marked) {
                g.drawString(s, HL_INSET, y + VL_INSET, Graphics.TOP | Graphics.LEFT);
            } else {
                final int dy = (lh - awptSize) >> 1;
                g.drawImage(awpt, HL_INSET, y /*+ VL_INSET*/ + dy, Graphics.TOP | Graphics.LEFT);
                g.drawString(/*"(*) " + */s, HL_INSET + awptSize + HL_INSET, y + VL_INSET, Graphics.TOP | Graphics.LEFT);
            }
            if (curr == selected) { // restore color
                g.setColor(colorForeUnsel);
            }
            curr++;
            y += lh;
        }

		if (lines < count) { // draw scrollbar
			sbY = (int) (h * ((float)top / size()));

			g.setColor(colorSbHiBorder);
			g.drawLine(w - sbWidth - 2, 0, w - sbWidth - 2, h);
			g.setColor(colorSbLoBorder);
			g.drawLine(w - sbWidth - 1, 0, w - sbWidth - 1, h);
			g.setColor(colorSbBg);
			g.fillRect(w - sbWidth, 0, sbWidth - 1, h);
			g.setColor(colorSbHiMain);
			g.drawRect(w - sbWidth - 2, sbY, sbWidth + 2, sbHeight);
			g.setColor(colorSbMain);
			g.fillRect(w - sbWidth - 1, sbY + 1, sbWidth, sbHeight - 1);
			g.setColor(colorSbLoBorder);
			g.drawLine(w - sbWidth - 1, sbY + sbHeight + 1, w - 1, sbY + sbHeight + 1);
			g.setColor(colorSbHiBorder);
			g.drawLine(w - 1, 0, w - 1, h);
		}
	}

    //
    // private methods
    //

    private void setVisibleCount(int count) {
        visible = (count > 0 ? count : 1);
    }

    private void recalculate(int w, int h) {
        width = w;
        height = h;
        if (Desktop.screen.hasPointerEvents()) {
            sbWidth = (int) ((float)w * .05);
            if (sbWidth < 20) {
                sbWidth = 20;
            }
        } else {
            sbWidth = 5;
        }
        final int v = h / getLineHeight();
        sbHeight = (int) (h * ((float)v / size()));
        setVisibleCount(v);
        makeSelectedVisible();
    }

    private static int getLineHeight() {
        return Desktop.fontLists.getHeight() + (VL_INSET << 1);
    }

    private void makeSelectedVisible() {
        if (visible > 0 && selected >= 0) {
            if (selected < top) {
                top = selected;
            } else if (selected - top + 1 > visible) {
                top = selected - visible + 1;
            }
        }
    }

	private boolean stepOnto(final char lc, final char uc) {
		final Object[] raw = this.items.getData();
		for (int N = items.size(), i = 0; i < N; i++) {
			final char c = raw[i].toString().charAt(0);
			if (c == lc || c == uc) {
				selected = i;
				return true;
			}
		}
		return false;
	}
}
