// @LICENSE@

package cz.kruch.track.fun;

//#ifdef __ANDROID__

import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import javax.microedition.media.MediaException;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import java.util.Vector;
import java.util.List;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

/**
 * Android camera.
 */
final class AndroidCamera extends Camera implements CommandListener,
        android.hardware.Camera.PictureCallback {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidCamera");
//#endif
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;

    private Preview preview;

    private View form;
    private CommandListener listener;

    private org.microemu.android.MicroEmulatorActivity activity;

    AndroidCamera() {
        this.activity = cz.kruch.track.TrackingMIDlet.getActivity();
    }

    void getResolutions(final Vector v) {
        android.hardware.Camera camera = null;
        List<android.hardware.Camera.Size> sizes = null;
        try {
            camera = android.hardware.Camera.open();
            sizes = camera.getParameters().getSupportedPictureSizes();
        } catch (Exception e) {
            Log.w(TAG, "Camera.getSupportedPictureSizes failed", e);
        } finally {
            try {
                camera.release();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
            System.runFinalization(); // force JNI cleanup
        }
        if (sizes != null) {
            StringBuffer sb = new StringBuffer(16);
            for (android.hardware.Camera.Size size : sizes) {
                v.addElement(sb.delete(0, sb.length()).append(size.width).append('x').append(size.height).toString());
            }
        }
    }

    void open() throws MediaException {

        // save form's command listener
        if (callback instanceof CommandListener) { // assertion: should be true!
            listener = (CommandListener) callback;
        } else {
            throw new IllegalStateException("assertion failed: parent it not command listener: " + callback);
        }

        // going to mix microemu and direct use of api 
        activity.post(new Runnable() {
            public void run() {

                final RelativeLayout fl = new RelativeLayout(activity);
                fl.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.FILL_PARENT));

                preview = new Preview(activity);
                preview.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                final RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT);
                rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                fl.addView(preview, rlp);

                final Button btn = new Button(activity);
                btn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                btn.setText(getCommandLabel());
                btn.setOnClickListener(new Button.OnClickListener() {
                    public void onClick(View v) {
                        preview.camera.takePicture(null, null, AndroidCamera.this);
                    }
                });
                final RelativeLayout.LayoutParams rlb = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                rlb.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                rlb.addRule(RelativeLayout.CENTER_HORIZONTAL);
                fl.addView(btn, rlb);

                form = activity.getContentView();
                cz.kruch.track.ui.Desktop.display.getCurrent().setCommandListener(AndroidCamera.this);
                activity.setContentView(fl);
                fl.invalidate();
            }
        });
    }

    void shutdown() {

        // restore form displayable command listener
        activity.post(new Runnable(){
            public void run() {
//                if (preview != null) {
//                    preview.close();
                    preview = null;
//                }
                if (listener != null) {
                    cz.kruch.track.ui.Desktop.display.getCurrent().setCommandListener(listener);
                    listener = null;
                }
                activity.setContentView(form);
                form = null;
            }
        });
    }

    public void commandAction(Command command, Displayable displayable) {
        if (Command.BACK != command.getCommandType()) {
            Log.w(TAG, "only Back key expected");
        }
        shutdown();
    }

    public void onPictureTaken(final byte[] bytes, android.hardware.Camera camera) {
        Desktop.getDiskWorker().enqueue(new Runnable() {
            public void run() {
                String result = null;
                Throwable throwable = null;
                try {
                    result = saveImage(bytes);
                } catch (Throwable t) {
                    throwable = t;
                }
                finished(result, throwable);
            }
        });
        shutdown();
    }

    private static String getCommandLabel() {
        return (new StringBuffer(16)).append(Resources.getString(Resources.NAV_CMD_TAKE)).append(' ').append(Resources.getString(Resources.NAV_FLD_SNAPSHOT)).toString();
    }

    private final class Preview extends SurfaceView
            implements Runnable, SurfaceHolder.Callback,
                       android.hardware.Camera.AutoFocusCallback {

        private android.hardware.Camera camera;

        Preview(Context context) {
            super(context);
            final SurfaceHolder holder = getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "preview surface created");

            // open cam
            camera = android.hardware.Camera.open();

            // set picture size
            android.hardware.Camera.Parameters p = camera.getParameters();
            try {
                final android.hardware.Camera.Size size = p.getSupportedPictureSizes().get(Config.snapshotFormatIdx);
                p.setPictureSize(size.width, size.height);
                Log.i(TAG, "set resolution to " + size.width + "x" + size.height);
            } catch (Exception e) {
                Log.w(TAG, "failed to set resolution; index " + Config.snapshotFormatIdx, e);
            }

            // set preview size
            if (android.os.Build.VERSION.SDK_INT >= 14) {
                try {
                    final android.hardware.Camera.Size size = p.getSupportedPreviewSizes().get(0);
                    p.setPreviewSize(size.width, size.height);
                    Log.i(TAG, "set preview size to " + size.width + "x" + size.height);
                } catch (Exception e) {
                    Log.w(TAG, "failed to set preview size", e);
                }
            }

            // set continuous autofocus
            if (android.os.Build.VERSION.SDK_INT >= 14) { // on 4.x use continuous autofocus
                try {
                    p.setFocusMode("continuous-picture"); // value of FOCUS_MODE_CONTINUOUS_PICTURE
                    Log.i(TAG, "use continuous-picture camera mode");
                    state.append("continuous-picture mode -> ");
                } catch (Exception e) {
                     Log.w(TAG, "failed to set continuous autofocus", e);
                }
            }

            // geotag
            if (gpsCoords != null && gpsTimestamp != 0) { // assertion: should be true
                p.setGpsLatitude(gpsCoords.getLat());
                p.setGpsLongitude(gpsCoords.getLon());
                if (gpsCoords.getAlt() != Float.NaN) {
                    p.setGpsAltitude(gpsCoords.getAlt());
                }
                p.setGpsTimestamp(gpsTimestamp / 1000);
            }

            // set params
            try {
                camera.setParameters(p);
            } catch (Exception e) {
                Log.e(TAG, "set parameters failed", e);
            }

            try {

                // set preview
                camera.setPreviewDisplay(holder);

                // start previewing
                camera.startPreview();

                // set autofocus
                if (android.os.Build.VERSION.SDK_INT >= 14) { // on 4.x use continuous autofocus
/* moved to initialization before activating preview
                    try {
                        p = camera.getParameters();
                        p.setFocusMode("continuous-picture"); // value of FOCUS_MODE_CONTINUOUS_PICTURE
                        camera.setParameters(p);
                        Log.i(TAG, "use continuous-picture camera mode");
                        state.append("continuous-picture mode -> ");
                    } catch (Exception e) {
                         // ignore
                    }
*/
                } else { // use manual autofocus
                    camera.autoFocus(this);
                    Log.i(TAG, "use autofocus camera mode");
                    state.append("autofocus mode -> ");
                }

            } catch (Exception e) {
                Log.e(TAG, "start preview failed", e);
                close();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "preview surface destroyed");
            close();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            Log.d(TAG, "preview surface changed");
        }

        public void run() {
            if (camera != null) {
                camera.autoFocus(this);
            }
        }

        public void onAutoFocus(boolean success, android.hardware.Camera camera) {
            if (this.camera != null) {
                postDelayed(this, 3500);
            }
        }

        private void close() {

            // close cam if any
            if (camera != null) {

                // stop continuous autofocus
                if (android.os.Build.VERSION.SDK_INT < 14) {
                    camera.cancelAutoFocus();
                    Log.i(TAG, "autofocus canceled");
                }

                // stop preview
                camera.stopPreview();
                Log.i(TAG, "preview stopped");

                // release
                camera.release();
                camera = null;
                Log.i(TAG, "camera released");

                // force JNI cleanup
                System.runFinalization();
            }
        }
    }
}

//#endif
