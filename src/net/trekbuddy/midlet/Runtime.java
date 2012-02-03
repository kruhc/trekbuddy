package net.trekbuddy.midlet;

//#ifdef __ANDROID__
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
//#endif
import api.location.LocationProvider;
import api.location.LocationException;
import cz.kruch.track.Resources;

public final class Runtime
//#ifdef __ANDROID__
                            extends Service
//#endif
                            implements IRuntime {
//#ifdef __ANDROID__

    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;

    private final Binder binder = new LocalBinder();

    @Override
    public void onCreate() {
        Log.d(TAG, "[svc] onCreate");
        super.onCreate();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try {
            mStartForeground = getClass().getMethod("startForeground", mStartForegroundSignature);
            mStopForeground = getClass().getMethod("stopForeground", mStopForegroundSignature);
            return;
        } catch (NoSuchMethodException e) {
            // Running on an older platform.
            mStartForeground = mStopForeground = null;
        }
        try {
            mSetForeground = getClass().getMethod("setForeground", mSetForegroundSignature);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Neither startForeground nor setForeground method available");
        }
    }

//#ifdef __BACKPORT__

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "[svc] onStart");
        intent.setAction(ACTION_FOREGROUND);
        handleCommand(intent);
    }

//#else

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "[svc] onStartCommand");
        intent.setAction(ACTION_FOREGROUND);
        handleCommand(intent);

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY;
    }

//#endif

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "[svc] onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "[svc] onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "[svc] onDestroy");

        // Clear flag
        running = false;

        // Make sure our notification is gone.
        stopForegroundCompat(NOTIFICATION_ID);

        super.onDestroy();
    }
    
    @Override
    public void onLowMemory() {
        Log.d(TAG, "[svc] onLowMemory");
    }    

    //
    // start/stop foreground
    //

    static final String ACTION_FOREGROUND = "net.trekbuddy.service.FOREGROUND";
    static final String ACTION_BACKGROUND = "net.trekbuddy.service.BACKGROUND";

    static final String NOTIFICATION_TITLE = TAG;

    static final int NOTIFICATION_ID = 666;

    private static final Class<?>[] mSetForegroundSignature = new Class[] {
        boolean.class};
    private static final Class<?>[] mStartForegroundSignature = new Class[] {
        int.class, Notification.class};
    private static final Class<?>[] mStopForegroundSignature = new Class[] {
        boolean.class};

    private NotificationManager mNM;
    private Method mSetForeground;
    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mSetForegroundArgs = new Object[1];
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];

    void invokeMethod(Method method, Object[] args) {
        try {
            method.invoke(this, args);
        } catch (Exception e) {
            // Should not happen.
            Log.w(TAG, "Unable to invoke method", e);
        }
    }

    private void handleCommand(Intent intent) {
        if (ACTION_FOREGROUND.equals(intent.getAction())) {
            // In this sample, we'll use the same text for the ticker and the expanded notification
            CharSequence text = Resources.getString(Resources.DESKTOP_MSG_TRACKING_ACTIVE);
            // Set the icon, scrolling text and timestamp
            Notification notification = new Notification(R.drawable.app_icon_notif, text,
                                                         System.currentTimeMillis());
            // The PendingIntent to launch our activity if the user selects this notification
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, org.microemu.android.MicroEmulator.class), 0);
            // Set the info for the views that show in the notification panel.
            notification.setLatestEventInfo(this, NOTIFICATION_TITLE, text, contentIntent);
            // Set notification
            startForegroundCompat(NOTIFICATION_ID, notification);
        } else if (ACTION_BACKGROUND.equals(intent.getAction())) {
            // Remove notification
            stopForegroundCompat(NOTIFICATION_ID);
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }
        // Fall back on the old API.
        mSetForegroundArgs[0] = Boolean.TRUE;
        invokeMethod(mSetForeground, mSetForegroundArgs);
        mNM.notify(id, notification);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(mStopForeground, mStopForegroundArgs);
            return;
        }
        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
        mSetForegroundArgs[0] = Boolean.FALSE;
        invokeMethod(mSetForeground, mSetForegroundArgs);
    }

    //
    // ~ start/stop foreground
    //

//#endif

    /*
     * IRuntime
     */

    private volatile LocationProvider provider;

    private volatile boolean running;

    public boolean isRunning() {
        return running;
    }

    public int startTracking(LocationProvider provider) throws LocationException {
//#ifdef __ANDROID__
        Log.d(TAG, "[svc] startTracking");
//#endif
        final int status = provider.start();
        this.provider = provider;
        return status;
    }

    public void quickstartTracking(LocationProvider provider) {
        this.provider = provider;
        restartTracking();
    }

    public void restartTracking() {
//#ifdef __ANDROID__
        Log.d(TAG, "[svc] restartTracking");
//#endif
        final Thread thread = new Thread((Runnable) provider);
//#ifdef __ALL__
        if (cz.kruch.track.TrackingMIDlet.samsung) {
            thread.setPriority(Thread.MIN_PRIORITY);
        }
//#endif
        thread.start();
    }

    public void stopTracking() throws LocationException {
//#ifdef __ANDROID__        
        Log.d(TAG, "[svc] stopTracking");
//#endif
        provider.stop();
    }

    public void afterTracking() {
//#ifdef __ANDROID__
        Log.d(TAG, "[svc] afterTracking");
//#endif
        provider = null;
//#ifdef __ANDROID__
        stopForegroundCompat(NOTIFICATION_ID);
//#endif
    }

    public LocationProvider getProvider() {
        return provider;
    }

//#ifdef __ANDROID__

    public class LocalBinder extends Binder implements IRuntime {

        public boolean isRunning() {
            return Runtime.this.isRunning();
        }

        public int startTracking(LocationProvider provider) throws LocationException {
            return Runtime.this.startTracking(provider);
        }

        public void quickstartTracking(LocationProvider provider) {
            Runtime.this.quickstartTracking(provider);
        }

        public void restartTracking() {
            Runtime.this.restartTracking();
        }

        public void stopTracking() throws LocationException {
            Runtime.this.stopTracking();
        }

        public void afterTracking() {
            Runtime.this.afterTracking();
        }

        public LocationProvider getProvider() {
            return Runtime.this.getProvider();
        }
    }

//#endif

}
