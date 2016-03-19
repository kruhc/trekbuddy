// @LICENSE@

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;

/**
 * Status bar.
 *
 * @author kruhc@seznam.cz
 */
final class Status extends Bar {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Status");
//#endif

    private String status;

    Status(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
/*
        this.clip = new int[]{ gx, -1, -1, -1 };
*/
        resize(width, height);
    }

    public void render(Graphics graphics) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif
        if (!visible || status == null) {
            return;
        }

        // draw status info
//--//#ifndef __CN1__
//#if !__ANDROID__ && !__CN1__
        graphics.drawImage(Desktop.bar, gx, height - bh, Graphics.TOP | Graphics.LEFT);
//#else
        final int cc = graphics.getColor();
        graphics.setARGBColor(Desktop.bar_c);
        graphics.fillRect(gx, height - bh, Desktop.bar_w, Desktop.bar_h);
        graphics.setColor(cc);
        graphics.setAlpha(0xff);
//#endif
        graphics.drawString(status, gx, height - bh, Graphics.TOP | Graphics.LEFT);
    }

    public void setStatus(String status) {
        this.status = status;
    }

/*
    public int[] getClip() {
        if (!visible && !update)
            return null;

        clip[1] = height - bh;
        clip[2] = width;
        clip[3] = bh;

        return clip;
    }
*/
}
