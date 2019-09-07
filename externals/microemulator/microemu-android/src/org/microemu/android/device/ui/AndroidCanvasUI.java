/**
 *  MicroEmulator
 *  Copyright (C) 2008 Bartek Teodorczyk <barteo@barteo.net>
 *
 *  It is licensed under the following two licenses as alternatives:
 *    1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 *    2. Apache License (the "AL") Version 2.0
 *
 *  You may not use this file except in compliance with at least one of
 *  the above two licenses.
 *
 *  You may obtain a copy of the LGPL at
 *      http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 *
 *  You may obtain a copy of the AL at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the LGPL or the AL for the specific language governing permissions and
 *  limitations.
 *
 *  @version $Id: AndroidCanvasUI.java 2373 2010-04-29 11:24:08Z barteo@gmail.com $
 */

package org.microemu.android.device.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.game.GameCanvas;

import org.microemu.DisplayAccess;
import org.microemu.MIDletAccess;
import org.microemu.MIDletBridge;
import org.microemu.android.MicroEmulatorActivity;
import org.microemu.android.device.AndroidDeviceDisplay;
import org.microemu.android.device.AndroidDisplayGraphics;
import org.microemu.android.device.AndroidInputMethod;
import org.microemu.android.util.AndroidRepaintListener;
import org.microemu.android.util.Overlay;
import org.microemu.app.ui.DisplayRepaintListener;
import org.microemu.device.Device;
import org.microemu.device.DeviceDisplay;
import org.microemu.device.DeviceFactory;
import org.microemu.device.ui.CanvasUI;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import org.microemu.android.util.AndroidRepaintListener;

public class AndroidCanvasUI extends AndroidDisplayableUI implements CanvasUI {   
    
    private AndroidDisplayGraphics graphics;
    
    private Bitmap bitmap;
    
    private android.graphics.Canvas bitmapCanvas;

    public AndroidCanvasUI(final MicroEmulatorActivity activity, Canvas canvas) {
        super(activity, canvas, false);
       
        activity.post(new Runnable() {
            @Override
            public void run() {
                view = new CanvasView(activity, AndroidCanvasUI.this);
            }
        });
    }

    boolean initGraphics(int width, int height) {
        final AndroidDeviceDisplay deviceDisplay = (AndroidDeviceDisplay) activity.getEmulatorContext().getDeviceDisplay();
        deviceDisplay.setSize(width, height);

        AndroidDisplayGraphics g = graphics;
        if (g == null) {
            g = new AndroidDisplayGraphics();
        }        

        final boolean sizeChanged = bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height;
        if (sizeChanged) {
          synchronized (g) {
            if (bitmapCanvas != null) {
                bitmapCanvas.setBitmap(null);
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            if (bitmapCanvas != null) {
                bitmapCanvas.setBitmap(bitmap);
            } else {
                bitmapCanvas = new android.graphics.Canvas(bitmap);
            }
            g.reset(bitmapCanvas);
          }
        }
        
        if (graphics == null) {
            graphics = g;
        }

        return sizeChanged;
    }
  
    @Override
    public void hideNotify()
    {
        activity.post(new Runnable() {
            @Override 
            public void run() {
		        ((AndroidDeviceDisplay) activity.getEmulatorContext().getDeviceDisplay()).removeDisplayRepaintListener((DisplayRepaintListener) view);
            }
        });
        
        super.hideNotify();
    }

    @Override
    public void showNotify()
    {
        super.showNotify();
        
        activity.post(new Runnable() {
            @Override
            public void run() {
		        ((AndroidDeviceDisplay) activity.getEmulatorContext().getDeviceDisplay()).addDisplayRepaintListener((DisplayRepaintListener) view);
		        ((Canvas) displayable).repaint();
            }
        });
    }
    
    public AndroidDisplayGraphics getGraphics() {
        while (graphics == null) {
            org.microemu.log.Logger.debug("graphics is null");
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return graphics;
    }

    public void flushGraphics(int x, int y, int width, int height) {
        if (view != null) {
            ((CanvasView) view).flushGraphics(x, y, width, height);
        }
    }

//#ifndef __BACKPORT__

    private static class ScaleListener extends android.view.ScaleGestureDetector.SimpleOnScaleGestureListener {

        private float scaleFactor = 1.0f;

        public ScaleListener() {
        }

        @Override
        public boolean onScale(android.view.ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();

            // don't let the object get too small or too large.
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));

            //System.out.println("SCALE DETECTOR; sum factor = " + scaleFactor + "; current change = " + detector.getScaleFactor());

            final AndroidInputMethod inputMethod = (AndroidInputMethod) DeviceFactory.getDevice().getInputMethod();
            inputMethod.pointerScaled((int) (scaleFactor * 1000), 0);

            return true;
        }
    }

    private static abstract class ScaleDetector {

        public abstract void initialize(Context context);
        public abstract boolean onTouchEvent(MotionEvent event);

        public static ScaleDetector newInstance(Context context) {
            ScaleDetector detector = null;
            /*
            try {
                Class.forName("android.view.ScaleGestureDetector");
                detector = (ScaleDetector) Class.forName("org.microemu.android.device.ui.AndroidCanvasUI$FroyoScaleDetector").newInstance();
            } catch (Throwable t) {
                detector = new org.microemu.android.device.ui.AndroidCanvasUI.EclairScaleDetector();
            }
            */
            System.out.println("VERSION.SDK_INT = " + android.os.Build.VERSION.SDK_INT);
            if (android.os.Build.VERSION.SDK_INT >= 8) {
                try {
                    detector = (ScaleDetector) Class.forName("org.microemu.android.device.ui.AndroidCanvasUI$FroyoScaleDetector").newInstance();
                } catch (Throwable t) {
                    // ignore
                }
            }
            if (detector == null) {
                detector = new org.microemu.android.device.ui.AndroidCanvasUI.EclairScaleDetector();
            }
            System.out.println("SCALE DETECTOR: " + detector);
            detector.initialize(context);

            return detector;
        }
    }

    private static class EclairScaleDetector extends ScaleDetector {

        public EclairScaleDetector() {
        }

        public void initialize(Context context) {
        }

        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }
    }

    private static class FroyoScaleDetector extends ScaleDetector {

        private android.view.ScaleGestureDetector detector;

        public FroyoScaleDetector() {
        }

        public void initialize(Context context) {
            this.detector = new android.view.ScaleGestureDetector(context, new ScaleListener());
        }

        public boolean onTouchEvent(MotionEvent event) {
            return detector.onTouchEvent(event);
        }
    }

//#endif

    //
    // CanvasUI
    //
    
    public class CanvasView extends SurfaceView implements DisplayRepaintListener {
        
        private final static int FIRST_DRAG_SENSITIVITY_X = 5;
        
        private final static int FIRST_DRAG_SENSITIVITY_Y = 5;
        
        private int pressedX = -FIRST_DRAG_SENSITIVITY_X;
        
        private int pressedY = -FIRST_DRAG_SENSITIVITY_Y;
        
        private AndroidCanvasUI ui;
        
        private Overlay overlay;
        
        private Matrix scale = new Matrix();

        private AndroidKeyListener keyListener;
        
        private int inputType = InputType.TYPE_CLASS_TEXT;

        private Paint paint = new Paint(0);
//#ifndef __BACKPORT__
        private ScaleDetector scaleDetector;
//#endif
        private boolean inMultitouchGesture;

        private volatile boolean isValid;

        public CanvasView(Context context, AndroidCanvasUI ui) {
            super(context);
            this.ui = ui;            
            
            setFocusable(true);
            setFocusableInTouchMode(true);
            setId(1611);
//#ifndef __BACKPORT__
            scaleDetector = ScaleDetector.newInstance(context);
//#endif
            final SurfaceHolder holder = getHolder();
            holder.setFormat(android.graphics.PixelFormat.RGB_565);
            holder.addCallback(new Callback() {

                public void surfaceCreated(SurfaceHolder holder) {
                    isValid = true;
                }
                
                public void surfaceDestroyed(SurfaceHolder holder) {
                    isValid = false;
                }

                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    if (initGraphics(width, height)) {
                        final MIDletAccess ma = MIDletBridge.getMIDletAccess();
                        if (ma == null) {
                            return;
                        }
                        final DisplayAccess da = ma.getDisplayAccess();
                        if (da != null) {
                            da.sizeChanged();
                        }
                    } else {
                        flushGraphics(0, 0, width, height);
                    }
                }
            });
        }
        
        public AndroidCanvasUI getUI() {
            return ui;
        }             

        public void flushGraphics(int x, int y, int width, int height) {
            // TODO handle x, y, width and height
            if (repaintListener == null) {
              if (isValid) {
                final SurfaceHolder holder = getHolder();
                try {
                    final android.graphics.Canvas canvas = holder.getSurface().isValid() ? holder.lockCanvas() : null;
                    if (canvas != null) {
                        try {
                            synchronized (graphics) {
                                canvas.drawBitmap(bitmap, 0, 0, null); // scale, paint
                            }
                            if (overlay != null) {
                                overlay.onDraw(canvas);
                            }
                        } finally {
                            holder.unlockCanvasAndPost(canvas);
                        }
                    } else {
                        org.microemu.log.Logger.warn("null canvas");
                    } 
                } catch (IllegalArgumentException e) {
                    org.microemu.log.Logger.error("lockCanvas failed (known bug?!?)", e);
                }
              }
            } else {
                repaintListener.flushGraphics();
            }
        }

        public void setOverlay(Overlay overlay) {
            this.overlay = overlay;
        }
        
        public void setScale(float sx, float sy) {
            scale.reset();
            scale.postScale(sx, sy);
        }

        public void setKeyListener(AndroidKeyListener keyListener, int inputType) {
        	this.keyListener = keyListener;
        	this.inputType = inputType;
        }

        //
        // View
        //
        
        @Override
        public void onDraw(android.graphics.Canvas androidCanvas) {
            final MIDletAccess ma = MIDletBridge.getMIDletAccess();
            if (ma == null) {
                return;
            }
            graphics.reset(androidCanvas);
            graphics.setClip(0, 0, androidCanvas.getWidth(), androidCanvas.getHeight());
            ma.getDisplayAccess().paint(graphics);
            if (overlay != null) {
                overlay.onDraw(androidCanvas);
            }
        }   
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (overlay != null && overlay.onTouchEvent(event)) {
                return true;
            }
//#ifndef __BACKPORT__
            scaleDetector.onTouchEvent(event);
//#endif
            final Device device = DeviceFactory.getDevice();
            final AndroidInputMethod inputMethod = (AndroidInputMethod) device.getInputMethod();
            final int x = (int) event.getX();
            final int y = (int) event.getY();
//#ifndef __BACKPORT__
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
//#else
            switch (event.getAction()) {
//#endif
            case MotionEvent.ACTION_DOWN:
                inputMethod.pointerPressed(x, y);
                pressedX = x;
                pressedY = y;
                break;
            case MotionEvent.ACTION_UP:
                inputMethod.pointerReleased(x, y);
                inMultitouchGesture = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (inMultitouchGesture) 
                   break;
                if (x > (pressedX - FIRST_DRAG_SENSITIVITY_X) &&  x < (pressedX + FIRST_DRAG_SENSITIVITY_X)
                        && y > (pressedY - FIRST_DRAG_SENSITIVITY_Y) &&  y < (pressedY + FIRST_DRAG_SENSITIVITY_Y)) {
                } else {
                    pressedX = -FIRST_DRAG_SENSITIVITY_X;
                    pressedY = -FIRST_DRAG_SENSITIVITY_Y;
                    inputMethod.pointerDragged(x, y);
                }
                break;
//#ifndef __BACKPORT__
            case MotionEvent.ACTION_POINTER_DOWN:
                inputMethod.pointerPressed(x, y);
                inMultitouchGesture = true;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                inputMethod.pointerReleased(x, y);
                break;
//#endif
            default:
                return false;
            }
            
            return true;
        }

        //
        // DisplayRepaintListener
        //
      
        @Override
        public void repaintInvoked(Object repaintObject)
        {
            final Rect r = (Rect) repaintObject;
            if (!(displayable instanceof GameCanvas)) {
                initGraphics(r.width(), r.height());
                onDraw(bitmapCanvas);
            }
            flushGraphics(r.left, r.top, r.width(), r.height());
        }       
        
        private AndroidRepaintListener repaintListener;

        public void setAndroidRepaintListener(AndroidRepaintListener repaintListener)
        {
            this.repaintListener = repaintListener;
        }

    }

}
