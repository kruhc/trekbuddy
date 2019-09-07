package com.dsi.ant.antplusdemo;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class ANTPlusService extends Service
{
    private static final String TAG = "ANTPlusDemo - Service.";
    
    public class LocalBinder extends Binder
    {
        public AntPlusManager getManager()
        {
            return mManager;
        }
    }
    
    private final LocalBinder mBinder = new LocalBinder();
    
    private AntPlusManager mManager;
    
    public static final int NOTIFICATION_ID = 1;

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "First Client bound.");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent)
    {
        Log.i(TAG, "Client rebound");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Log.i(TAG, "All clients unbound.");
        // TODO Auto-generated method stub
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "Service created.");
        super.onCreate();
        mManager = new AntPlusManager();
        mManager.start(this);
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
/*
        Notification notification = new Notification(R.drawable.antplus, getString(R.string.Notify_Started),
                System.currentTimeMillis());
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, ANTPlusDemo.class),
                PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(this, getString(R.string.app_name),
                getString(R.string.Notify_Started_Body), pi);
        this.startForeground(NOTIFICATION_ID, notification);
*/
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy()
    {
        mManager.setCallbacks(null);
        mManager.shutDown();
        mManager = null;
        super.onDestroy();
        Log.i(TAG, "Service destroyed.");
    }

}
