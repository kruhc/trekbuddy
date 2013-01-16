// @LICENSE@

package cz.kruch.track.util;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import java.io.IOException;

//#ifdef __ANDROID__

import android.graphics.Bitmap;
import org.microemu.android.device.AndroidImmutableImage;

//#endif

//#ifdef __RIM50__

import net.rim.device.api.system.Bitmap;
import net.rim.device.api.system.EncodedImage;

//#endif

public final class ImageUtils {

    public static Image getGoodIcon(final String resource) throws IOException {
        Image img = Image.createImage(resource);
        if (img != null) {
            if (!Desktop.isHires() && (img.getWidth() > 20) || img.getHeight() > 20) {
                img = resizeImage(img, 16, 16, ImageUtils.SLOW_RESAMPLE, false);
            }
        }
        return img;
    }

    public static Image createRGBImage(final int w, final int h, final int color) {
        final int[] shadow = new int[w * h];
        for (int i = shadow.length; --i >= 0; ) {
            shadow[i] = color;
        }
        return Image.createRGBImage(shadow, w, h, true);
    }

    /**
     * Resizing image by shmoove (http://www.mobilegd.com/postt34.html).
     * Modifications:
     *   1. removed timing
     *   2. simpler line-by-line (uses much less memory) fast resample implementation
     *   3. enabled alpha for slow resample mode
     *   4. not catching exception
     */

    // fixed point constants
    private static final int FP_SHIFT = 13;
    /**
     * fast resampling mode - no antialiase
     */
    public static final int FAST_RESAMPLE = 0;
    /**
     * slow resampling mode - with antialiase
     */
    public static final int SLOW_RESAMPLE = 1;

    /**
     * Gets a source image along with new size for it and resizes it.
     *
     * @param src   The source image.
     * @param destW The new width for the destination image.
     * @param destH The new heigth for the destination image.
     * @param mode  A flag indicating what type of resizing we want to do: FAST_RESAMPLE or SLOW_RESAMPLE.
     * @return The resized image.
     */
    public static Image resizeImage(Image src, int destW, int destH, int mode, boolean recycle) {

        final int srcW = src.getWidth();
        final int srcH = src.getHeight();

        // paranoia
        if (srcW == destW || srcH == destH) {
            throw new IllegalStateException("resize " + srcW + "x" + srcH + " to " + destW + "x" + destH);
        }

//#ifdef __ANDROID__

        Bitmap bitmap = ((AndroidImmutableImage) src).getBitmap();
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, destW, destH,
                                                  mode == SLOW_RESAMPLE || Config.tilesScaleFiltered);
        if (recycle && bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return new AndroidImmutableImage(scaled);

//-#elifdef __RIM__

//        int[] rgb = new int[srcW * srcH];
//        src.getRGB(rgb, 0, srcW, 0, 0, srcW, srcH);
//
//        Bitmap bitmap = new Bitmap(srcW, srcH);
//        bitmap.setARGB(rgb, 0, srcW, 0, 0, srcW, srcH);
//        rgb = null;
//
//        Bitmap scaled = new Bitmap(destW, destH);
//        bitmap.scaleInto(scaled, Bitmap.FILTER_LANCZOS);
//        bitmap = null;
//
//        rgb = new int[destW * destH];
//        scaled.getARGB(rgb, 0, destW, 0, 0, destW, destH);
//        scaled = null;
//
//        return Image.createRGBImage(rgb, destW, destH, true);

//#elifdef __CN1__

        return src.scaled(destW, destH);

//#else

        // create pixel arrays
        int[] srcPixels = new int[srcW];
        int[] destPixels; // array to hold destination pixels

        if (mode == FAST_RESAMPLE) {

            // destination pixels line, to save memory
            destPixels = new int[destW];

            // create result bitmap
            final Image mem = Image.createImage(destW, destH);
            final Graphics g = mem.getGraphics();
/*

            // scale with no filtering
            int index1 = 0;
            int index0_y = 0;

            for (int y = 0; y < destH; y++) {
                final int y_ = index0_y / destH;
                int index0_x = 0;
                src.getRGB(srcPixels, 0, srcW, 0, y_, srcW, 1);

                for (int x = 0; x < destW; x++) {
                    final int x_ = index0_x / destW;
                    destPixels[index1++] = srcPixels[x_];

                    // for next pixel
                    index0_x += srcW;
                }
                // For next line
                index0_y += srcH;

                // write line to destination
                g.drawRGB(destPixels, 0, destW, 0, y, destW, 1, true);

                // start at 0 dest line
                index1 = 0;
            }
*/
            /*
             * More readable version of the same.
             * http://translatater.googlecode.com/svn-history/r2/trunk/src/com/jeffpalm/j2me/flashcards/ImagResizer.java
             */

            // precalculate src/dest ratios
            final int ratioW = (srcW << FP_SHIFT) / destW;
            final int ratioH = (srcH << FP_SHIFT) / destH;

            // simple point smapled resizing
            // loop through the destination pixels, find the matching pixel on the source and use that
            for (int destY = 0; destY < destH; ++destY) {
                int p = 0;
                int srcY = (destY * ratioH) >> FP_SHIFT; // calculate beginning of sample
                // int srcY = (destY * srcH) / destH;
                src.getRGB(srcPixels, 0, srcW, 0, srcY, srcW, 1);
                for (int destX = 0; destX < destW; ++destX) {
                    int srcX = (destX * ratioW) >> FP_SHIFT; // calculate beginning of sample
                    // int srcX = (destX * srcW) / destW;
                    destPixels[p++] = srcPixels[srcX];
                }
                g.drawRGB(destPixels, 0, destW, 0, destY, destW, 1, true);
            }

            // return immutable image
            return Image.createImage(mem);

        } else {
            
            // array to hold destination pixels
            destPixels = new int[destW * destH];

            // precalculate src/dest ratios
            final int ratioW = (srcW << FP_SHIFT) / destW;
            final int ratioH = (srcH << FP_SHIFT) / destH;

            final int aSize = destW * srcH; // I do not understand this size, but I tripple-checked it 
            byte[] tmpA = new byte[aSize];
            byte[] tmpR = new byte[aSize]; // temporary buffer for the horizontal resampling step
            byte[] tmpG = new byte[aSize];
            byte[] tmpB = new byte[aSize];

            // variables to perform additive blending
            int argb; // color extracted from source
            int a, r, g, b; // separate channels of the color
            int count; // number of pixels sampled for calculating the average

            // the resampling will be separated into 2 steps for simplicity
            // the first step will keep the same height and just stretch the picture horizontally
            // the second step will take the intermediate result and stretch it vertically

            // horizontal resampling
            int p = 0;
            for (int y = 0; y < srcH; ++y) {
                src.getRGB(srcPixels, 0, srcW, 0, y, srcW, 1);
                for (int x = 0; x < destW; ++x) {
                    int srcX = (x * ratioW) >> FP_SHIFT; // calculate beginning of sample
                    int srcX2 = ((x + 1) * ratioW) >> FP_SHIFT; // calculate end of sample
                    if (srcX2 >= srcW) srcX2 = srcW - 1;

                    count = srcX2 - srcX + 1;
                    // now loop from srcX to srcX2 and add up the values for each channel
                    for (a = r = b = g = 0; srcX <= srcX2; srcX++) {
                        argb = srcPixels[srcX];
                        a += (argb >> 24) & 0xFF;
                        r += (argb >> 16) & 0xFF;
                        g += (argb >> 8) & 0xFF;
                        b += argb & 0xFF;
                    }
                    // average out the channel values
                    tmpA[p] = (byte) (a / count);
                    tmpR[p] = (byte) (r / count);
                    tmpG[p] = (byte) (g / count);
                    tmpB[p] = (byte) (b / count);
                    p++;
                }
            }

            // vertical resampling of the temporary buffer (which has been horizontally resampled)
            for (int x = 0; x < destW; ++x)
                for (int y = 0, xx = x; y < destH; y++, xx += destW) {
                    int srcY = (y * ratioH) >> FP_SHIFT; // calculate beginning of sample
                    int srcY2 = ((y + 1) * ratioH) >> FP_SHIFT; // calculate end of sample
                    if (srcY2 >= srcH) srcY2 = srcH - 1;

                    count = srcY2 - srcY + 1;
                    // now loop from srcY to srcY2 and add up the values for each channel
                    p = x + srcY * destW;
                    for (a = r = b = g = 0; srcY <= srcY2; srcY++, p += destW) {
                        a += tmpA[p] & 0xFF; // alpha channel
                        r += tmpR[p] & 0xFF; // red channel
                        g += tmpG[p] & 0xFF; // green channel
                        b += tmpB[p] & 0xFF; // blue channel
                    }
                    // recreate color from the averaged channels and place it into the destination buffer
                    destPixels[xx] = ((a / count) << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
                }
        }

        // null buffer
        srcPixels = null;

        // return a new image created from the destination pixel buffer
        return Image.createRGBImage(destPixels, destW, destH, true);
        // note that if you put back alpha support, have to change false above to true or the alpha channel will be ignored

//#endif

    }

//#ifdef __RIM50__

    public static Image RIMresizeImage(java.io.InputStream stream, int prescale, int x2) throws IOException {

/*
        api.io.NakedByteArrayOutputStream baos = new api.io.NakedByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int c = stream.read(buffer);
        while (c > -1) {
            baos.write(buffer, 0, c);
            c = stream.read(buffer);
        }
        buffer = null;
        baos.close();
*/

/*
        Bitmap bitmap = Bitmap.createBitmapFromBytes(baos.getBuf(), 0, baos.getCount(), 1);
        baos = null;
        final int destW = prescale(prescale, bitmap.getWidth()) << x2;
        final int destH = prescale(prescale, bitmap.getHeight()) << x2;
        Bitmap scaled = new Bitmap(destW, destH);
        bitmap.scaleInto(scaled, Config.tilesScaleFiltered ? Bitmap.FILTER_BILINEAR : Bitmap.FILTER_BOX,
                         Bitmap.SCALE_TO_FIT);
        bitmap = null;
        int[] rgb = new int[destW * destH];
        scaled.getARGB(rgb, 0, destW, 0, 0, destW, destH);
        scaled = null;

        return Image.createRGBImage(rgb, destW, destH, false);
*/
/*
        EncodedImage image = EncodedImage.createEncodedImage(baos.getBuf(), 0, baos.getCount());
        baos = null;
        final int srcW = image.getWidth();
        final int srcH = image.getHeight();
        final int destW = prescale(prescale, srcW) << x2;
        final int destH = prescale(prescale, srcH) << x2;
        final int scaleFactorX = Fixed32.div(Fixed32.toFP(srcW), Fixed32.toFP(destW));
        final int scaleFactorY = Fixed32.div(Fixed32.toFP(srcH), Fixed32.toFP(destH));
        image.setDecodeMode(EncodedImage.DECODE_READONLY | EncodedImage.DECODE_NO_DITHER);
        Bitmap bitmap = image.scaleImage32(scaleFactorX, scaleFactorY).getBitmap();
        image = null;
        final int[] rgb = new int[destW * destH];
        bitmap.getARGB(rgb, 0, destW, 0, 0, destW, destH);
        bitmap = null;

        return Image.createRGBImage(rgb, destW, destH, false);
*/
        byte[] data = net.rim.device.api.io.IOUtilities.streamToBytes(stream, 4096);
        EncodedImage image = EncodedImage.createEncodedImage(data, 0, data.length);
        data = null; // gc hint
        image.setDecodeMode(EncodedImage.DECODE_READONLY | EncodedImage.DECODE_NO_DITHER /* | EncodedImage.DECODE_NATIVE*/);
        Bitmap bitmap = image.getBitmap();
        image = null; // gc hint
        final int destW = prescale(prescale, bitmap.getWidth()) << x2;
        final int destH = prescale(prescale, bitmap.getHeight()) << x2;
        Bitmap scaled = new Bitmap(destW, destH);
        bitmap.scaleInto(scaled, Config.tilesScaleFiltered ? Bitmap.FILTER_BILINEAR
                                                           : Bitmap.FILTER_BOX,
                         Bitmap.SCALE_TO_FIT);
        bitmap = null; // gc hint
        final int[] rgb = new int[destW * destH];
        scaled.getARGB(rgb, 0, destW, 0, 0, destW, destH);
        scaled = null; // gc hint

        return Image.createRGBImage(rgb, destW, destH, false);
    }

    private static int prescale(final int ps, final int i) {
        return (i * ps) / 100;
    }

//#endif

}
