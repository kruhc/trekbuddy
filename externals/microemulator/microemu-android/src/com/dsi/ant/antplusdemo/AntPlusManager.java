/*
 * Copyright 2010 Dynastream Innovations Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.dsi.ant.antplusdemo;

import java.lang.reflect.Field;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.dsi.ant.exception.*;
import com.dsi.ant.AntInterface;
import com.dsi.ant.AntInterfaceIntent;
import com.dsi.ant.AntMesg;
import com.dsi.ant.AntDefine;

/**
 * This class handles connecting to the AntRadio service, setting up the channels,
 * and processing Ant events.
 */
public class AntPlusManager {

    /**
     * Pair to any device.
     */
    public static final short WILDCARD = 0;

    /**
     * Defines the interface needed to work with all call backs this class makes
     */
    public interface Callbacks
    {
        public void errorCallback();
        public void notifyAntStateChanged();
        public void notifyChannelStateChanged(byte channel);
        public void notifyChannelDataChanged(byte channel);
    }
    
    /** The Log Tag. */
    public static final String TAG = "ANTApp";
    
    /** The interface to the ANT radio. */
    private AntInterface mAntReceiver;
    
    /** Is the ANT background service connected. */
    private boolean mServiceConnected = false;
    
    /** Stores which ANT status Intents to receive. */
    private IntentFilter statusIntentFilter;
    
    /** Flag to know if an ANT Reset was triggered by this application. */
    private boolean mAntResetSent = false;
    
    /** Flag if waiting for ANT_ENABLED. Default is now false, We assume ANT is disabled until told otherwise.*/
    private boolean mEnabling = false;
    
    /** Flag if waiting for ANT_DISABLED. Default is false, will be set to true when a disable is attempted. */
    private boolean mDisabling = false;
    
    // ANT Channels
    /** The ANT channel for the HRM. */
    public static final byte HRM_CHANNEL = (byte) 0;
    
    /** ANT+ device type for an HRM */
    private static final byte HRM_DEVICE_TYPE = 0x78;
    
    /** ANT+ channel period for an HRM */
    private static final short HRM_PERIOD = 8070;
    
    /** The ANT channel for the SDM. */
    public static final byte SDM_CHANNEL = (byte) 1;
    
    /** ANT+ device type for an SDM */
    private static final byte SDM_DEVICE_TYPE = 0x7C;
    
    /** ANT+ channel period for an SDM */
    private static final short SDM_PERIOD = 8134;
    
    /** The ANT channel for the Weight Scale. */
    public static final byte WEIGHT_CHANNEL = (byte) 2;
    
    /** ANT+ device type for a Weight scale */
    private static final byte WEIGHT_DEVICE_TYPE = 0x77;
    
    /** ANT+ channel period for a Weight scale */
    private static final short WEIGHT_PERIOD = 8192;
    
    //TODO: This string will eventually be provided by the system or by AntLib
    /** String used to represent ant in the radios list. */
    private static final String RADIO_ANT = "ant";
    
 // Variables to keep track of the status of each sensor
    /** Has the user profile been sent to the weight scale. */
    private boolean mHasSentProfile;
    
    /** The weight scale has calculated the weight. */
    private boolean mSessionDone;
    
    /** The current HRM page/toggle bit state. */
    private HRMStatePage mStateHRM = HRMStatePage.INIT;
    
    /** Has the starting distance been recorded. */
    private boolean mDistanceInit;
    
    /** Has the starting stride count been recorded. */
    private boolean mStridesInit;
    
    /** The distance. */
    private float mAccumDistance;
    
    /** The number of strides. */
    private long mAccumStrides;
    
    /** The m prev distance. */
    private float mPrevDistance;
    
    /** The m prev strides. */
    private int mPrevStrides;
    
    /** Description of ANT's current state */
    private String mAntStateText = "";
    
    /** Possible states of a device channel */
    public enum ChannelStates
    {
       /** Channel was explicitly closed or has not been opened */
       CLOSED,
       
       /** User has requested we open the channel, but we are waiting for a reset */
       PENDING_OPEN,
       
       /** Channel is opened, but we have not received any data yet */
       SEARCHING,
       
       /** Channel is opened and has received status data from the device most recently */
       TRACKING_STATUS,
       
       /** Channel is opened and has received measurement data most recently */
       TRACKING_DATA,
       
       /** Channel is closed as the result of a search timeout */
       OFFLINE
    }

    /** Current state of the HRM channel */
    private ChannelStates mHrmState = ChannelStates.CLOSED;

    /** Last measured BPM form the HRM device */
    private int mBPM = 0;

    /** Current state of the SDM channel */
    private ChannelStates mSdmState = ChannelStates.CLOSED;
    
    /** Last measured cadence from the SDM */
    private float mCadence = 0f;

    /** Last measured speed from the SDM */
    private float mSpeed = 0f;

    /** Current state of the weight scale channel */
    private ChannelStates mWeightState = ChannelStates.CLOSED;
    
    /** Most recent status string for the weight scale */
    private String mWeightStatus = "";
    
    /** Most recent weight received */
    private int mWeight = 0;
    
    //Flags used for deferred opening of channels
    /** Flag indicating that opening of the HRM channel was deferred */
    private boolean mDeferredHrmStart = false;
    
    /** Flag indicating that opening of the SDM channel was deferred */
    private boolean mDeferredSdmStart = false;
    
    /** Flag indicating that opening of the weight scale channel was deferred */
    private boolean mDeferredWeightStart = false;
    
    /** HRM device number. */
    private short mDeviceNumberHRM;
    
    /** SDM device number. */
    private short mDeviceNumberSDM;
    
    /** Weight scale device number. */
    private short mDeviceNumberWGT;
    
    /** Devices must be within this bin to be found during (proximity) search. */
    private byte mProximityThreshold;
    
    private ChannelConfiguration channelConfig[];
    
    //TODO You will want to set a separate threshold for screen off and (if desired) screen on.
    /** Data buffered for event buffering before flush. */
    private short mBufferThreshold;
    
    /** If this application has control of the ANT Interface. */
    private boolean mClaimedAntInterface;

    /**
     * The possible HRM page toggle bit states.
     */
    public enum HRMStatePage
    {
       /** Toggle bit is 0. */
       TOGGLE0,
       
       /** Toggle bit is 1. */
       TOGGLE1,
       
       /** Initialising (bit value not checked). */
       INIT,
       
       /** Extended pages are valid. */
       EXT
    }
    
    private Context mContext;
    
    private Callbacks mCallbackSink;
    
    /**
     * Default Constructor
     */
    public AntPlusManager()
    {
        Log.d(TAG, "AntChannelManager: enter Constructor");
        
        channelConfig = new ChannelConfiguration[3];
        
        //Set initial state values
        mDeferredHrmStart = false;
        mHrmState = ChannelStates.CLOSED;
        channelConfig[HRM_CHANNEL] = new ChannelConfiguration();
        mDeferredSdmStart = false;
        mSdmState = ChannelStates.CLOSED;
        channelConfig[SDM_CHANNEL] = new ChannelConfiguration();
        mDeferredWeightStart = false;
        mWeightState = ChannelStates.CLOSED;
        channelConfig[WEIGHT_CHANNEL] = new ChannelConfiguration();
        
        mClaimedAntInterface = false;
        
        // ANT intent broadcasts.
        statusIntentFilter = new IntentFilter();
        statusIntentFilter.addAction(AntInterfaceIntent.ANT_ENABLED_ACTION);
        statusIntentFilter.addAction(AntInterfaceIntent.ANT_ENABLING_ACTION);
        statusIntentFilter.addAction(AntInterfaceIntent.ANT_DISABLED_ACTION);
        statusIntentFilter.addAction(AntInterfaceIntent.ANT_DISABLING_ACTION);
        statusIntentFilter.addAction(AntInterfaceIntent.ANT_RESET_ACTION);
        statusIntentFilter.addAction(AntInterfaceIntent.ANT_INTERFACE_CLAIMED_ACTION);
        statusIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        
        mAntReceiver = new AntInterface();
    }
    
    
    /**
     * Creates the connection to the ANT service back-end.
     */
    public boolean start(Context context)
    {
        boolean initialised = false;
        
        mContext = context;
        
        if(AntInterface.hasAntSupport(mContext))
        {
            mContext.registerReceiver(mAntStatusReceiver, statusIntentFilter);
            
            if(!mAntReceiver.initService(mContext, mAntServiceListener))
            {
                // Need the ANT Radio Service installed.
                Log.e(TAG, "AntChannelManager Constructor: No ANT Service.");
                requestServiceInstall();
            }
            else
            {
                mServiceConnected = mAntReceiver.isServiceConnected();

                if(mServiceConnected)
                {
                    try
                    {
                        mClaimedAntInterface = mAntReceiver.hasClaimedInterface();
                        if(mClaimedAntInterface)
                        {
                            receiveAntRxMessages(true);
                        }
                    }
                    catch (AntInterfaceException e)
                    {
                        antError();
                    }
                }
                
                initialised = true;
            }
        }
        
        return initialised;
    }
    
    /**
     * Requests that the user install the needed service for ant
     */
    private void requestServiceInstall()
    {
        Toast installNotification = Toast.makeText(mContext, "Service required"/*mContext.getResources().getString(R.string.Notify_Service_Required)*/, Toast.LENGTH_LONG);
        installNotification.show();

        AntInterface.goToMarket(mContext);
    }
    
    public void setCallbacks(Callbacks callbacks)
    {
        mCallbackSink = callbacks;
    }
    
    //Getters and setters
    
    public boolean isServiceConnected()
    {
        return mServiceConnected;
    }
    
    public boolean isInterfaceClaimed() 
    {
        return mClaimedAntInterface;
    }

    public short getDeviceNumberHRM()
    {
        return mDeviceNumberHRM;
    }

    public void setDeviceNumberHRM(short deviceNumberHRM)
    {
        this.mDeviceNumberHRM = deviceNumberHRM;
    }

    public short getDeviceNumberSDM()
    {
        return mDeviceNumberSDM;
    }

    public void setDeviceNumberSDM(short deviceNumberSDM)
    {
        this.mDeviceNumberSDM = deviceNumberSDM;
    }

    public short getDeviceNumberWGT()
    {
        return mDeviceNumberWGT;
    }

    public void setDeviceNumberWGT(short deviceNumberWGT)
    {
        this.mDeviceNumberWGT = deviceNumberWGT;
    }
    
    public byte getProximityThreshold()
    {
        return mProximityThreshold;
    }

    public void setProximityThreshold(byte proximityThreshold)
    {
        this.mProximityThreshold = proximityThreshold;
    }

    public short getBufferThreshold()
    {
        return mBufferThreshold;
    }

    public void setBufferThreshold(short bufferThreshold)
    {
        this.mBufferThreshold = bufferThreshold;
    }
    
    public HRMStatePage getStateHRM()
    {
        return mStateHRM;
    }

    public float getAccumDistance()
    {
        return mAccumDistance;
    }

    public long getAccumStrides()
    {
        return mAccumStrides;
    }

    public ChannelStates getHrmState()
    {
        return mHrmState;
    }

    public int getBPM()
    {
        return mBPM;
    }

    public ChannelStates getSdmState()
    {
        return mSdmState;
    }

    public float getCadence()
    {
        return mCadence;
    }

    public float getSpeed()
    {
        return mSpeed;
    }

    public ChannelStates getWeightState()
    {
        return mWeightState;
    }

    public String getWeightStatus()
    {
        return mWeightStatus;
    }

    public int getWeight()
    {
        return mWeight;
    }

    public String getAntStateText()
    {
        return mAntStateText;
    }
    
    /**
     * Checks if ANT can be used by this application
     * Sets the AntState string to reflect current status.
     * @return true if this application can use the ANT chip, false otherwise.
     */
    public boolean checkAntState()
    {
        try
        {
            if(!AntInterface.hasAntSupport(mContext))
            {
                Log.w(TAG, "updateDisplay: ANT not supported");

                mAntStateText = "Not supported"; // mContext.getString(R.string.Text_ANT_Not_Supported);
                return false;
            }
            else if(isAirPlaneModeOn())
            {
                mAntStateText = "Airplane mode"; // mContext.getString(R.string.Text_Airplane_Mode);
                return false;
            }
            else if(mEnabling)
            {
                mAntStateText = "Enabling"; // mContext.getString(R.string.Text_Enabling);
                return false;
            }
            else if(mDisabling)
            {
                mAntStateText = "Disabling"; // mContext.getString(R.string.Text_Disabling);
                return false;
            }
            else if(mServiceConnected)
            {
                if(!mAntReceiver.isEnabled())
                {
                    mAntStateText = "Disabled"; // mContext.getString(R.string.Text_Disabled);
                    return false;
                }
                if(mAntReceiver.hasClaimedInterface() || mAntReceiver.claimInterface())
                {
                    return true;
                }
                else
                {
                    mAntStateText = "In use"; // mContext.getString(R.string.Text_ANT_In_Use);
                    return false;
                }
            }
            else
            {
                Log.w(TAG, "updateDisplay: Service not connected");

                mAntStateText = "Not connected"; // mContext.getString(R.string.Text_Disabled);
                return false;
            }
        }
        catch(AntInterfaceException e)
        {
            antError();
            return false;
        }
    }

    /**
     * Attempts to claim the Ant interface
     */
    public void tryClaimAnt()
    {
        try
        {
            mAntReceiver.requestForceClaimInterface(TAG/*mContext.getResources().getString(R.string.app_name)*/);
        }
        catch(AntInterfaceException e)
        {
            antError();
        }
    }

    /**
     * Unregisters all our receivers in preparation for application shutdown
     */
    public void shutDown()
    {
        try
        {
            mContext.unregisterReceiver(mAntStatusReceiver);
        }
        catch(IllegalArgumentException e)
        {
            // Receiver wasn't registered, ignore as that's what we wanted anyway
        }
        
        receiveAntRxMessages(false);
        
        if(mServiceConnected)
        {
            try
            {
                if(mClaimedAntInterface)
                {
                    Log.d(TAG, "AntChannelManager.shutDown: Releasing interface");

                    mAntReceiver.releaseInterface();
                }

                mAntReceiver.stopRequestForceClaimInterface();
            }
            catch(AntServiceNotConnectedException e)
            {
                // Ignore as we are disconnecting the service/closing the app anyway
            }
            catch(AntInterfaceException e)
            {
               Log.w(TAG, "Exception in AntChannelManager.shutDown", e);
            }
            
            mAntReceiver.releaseService();
        }
    }

    /**
     * Class for receiving notifications about ANT service state.
     */
    private AntInterface.ServiceListener mAntServiceListener = new AntInterface.ServiceListener()
    {
        public void onServiceConnected()
        {
            Log.d(TAG, "mAntServiceListener onServiceConnected()");

            mServiceConnected = true;

            try
            {

                mClaimedAntInterface = mAntReceiver.hasClaimedInterface();

                if (mClaimedAntInterface)
                {
                    // mAntMessageReceiver should be registered any time we have
                    // control of the ANT Interface
                    receiveAntRxMessages(true);
                } else
                {
                    // Need to claim the ANT Interface if it is available, now
                    // the service is connected
                    mClaimedAntInterface = mAntReceiver.claimInterface();
                }
            } catch (AntInterfaceException e)
            {
                antError();
            }

            Log.d(TAG, "mAntServiceListener Displaying icons only if radio enabled");
            if(mCallbackSink != null)
                mCallbackSink.notifyAntStateChanged();
        }

        public void onServiceDisconnected()
        {
            Log.d(TAG, "mAntServiceListener onServiceDisconnected()");

            mServiceConnected = false;
            mEnabling = false;
            mDisabling = false;

            if (mClaimedAntInterface)
            {
                receiveAntRxMessages(false);
            }

            if(mCallbackSink != null)
                mCallbackSink.notifyAntStateChanged();
        }
    };
    
    /**
     * Configure the ANT radio to the user settings.
     */
    public void setAntConfiguration()
    {
        try
        {
            if(mServiceConnected && mClaimedAntInterface && mAntReceiver.isEnabled())
            {
                try
                {
                    // Event Buffering Configuration
                    if(mBufferThreshold > 0)
                    {
                        //TODO For easy demonstration will set screen on and screen off thresholds to the same value.
                        // No buffering by interval here.
                        mAntReceiver.ANTConfigEventBuffering((short)0xFFFF, mBufferThreshold, (short)0xFFFF, mBufferThreshold);
                    }
                    else
                    {
                        mAntReceiver.ANTDisableEventBuffering();
                    }
                }
                catch(AntInterfaceException e)
                {
                    Log.e(TAG, "Could not configure event buffering", e);
                }
            }
            else
            {
                Log.i(TAG, "Can't set event buffering right now.");
            }
        } catch (AntInterfaceException e)
        {
            Log.e(TAG, "Problem checking enabled state.");
        }
    }
    
    /**
     * Display to user that an error has occured communicating with ANT Radio.
     */
    private void antError()
    {
        mAntStateText = "ANT error"; // mContext.getString(R.string.Text_ANT_Error);
        if(mCallbackSink != null)
            mCallbackSink.errorCallback();
    }
    
    /**
     * Opens a given channel using the proper configuration for the channel's sensor type.
     * @param channel The channel to Open.
     * @param deferToNextReset If true, channel will not open until the next reset.
     */
    public void openChannel(byte channel, boolean deferToNextReset)
    {
        Log.i(TAG, "Starting service.");
        mContext.startService(new Intent(mContext, ANTPlusService.class));
        if (!deferToNextReset)
        {
            channelConfig[channel].deviceNumber = 0;
            channelConfig[channel].deviceType = 0;
            channelConfig[channel].TransmissionType = 0; // Set to 0 for wild card search
            channelConfig[channel].period = 0;
            channelConfig[channel].freq = 57; // 2457Mhz (ANT+ frequency)
            channelConfig[channel].proxSearch = mProximityThreshold;
            switch (channel)
            {
                case HRM_CHANNEL:
                    channelConfig[channel].deviceNumber = mDeviceNumberHRM;
                    channelConfig[channel].deviceType = HRM_DEVICE_TYPE;
                    channelConfig[channel].period = HRM_PERIOD;
                    mHrmState = ChannelStates.PENDING_OPEN;
                    break;
                case SDM_CHANNEL:
                    channelConfig[channel].deviceNumber = mDeviceNumberSDM;
                    channelConfig[channel].deviceType = SDM_DEVICE_TYPE;
                    channelConfig[channel].period = SDM_PERIOD;
                    mSdmState = ChannelStates.PENDING_OPEN;

                    mDistanceInit = false;
                    mStridesInit = false;
                    mAccumDistance = 0;
                    mAccumStrides = 0;
                    mPrevDistance = 0;
                    mPrevStrides = 0;
                    break;
                case WEIGHT_CHANNEL:
                    channelConfig[channel].deviceNumber = mDeviceNumberWGT;
                    channelConfig[channel].deviceType = WEIGHT_DEVICE_TYPE;
                    channelConfig[channel].period = WEIGHT_PERIOD;
                    mWeightState = ChannelStates.PENDING_OPEN;

                    // Reset session
                    mHasSentProfile = false;
                    mSessionDone = false;
                    break;
            }
            if(mCallbackSink != null)
                mCallbackSink.notifyChannelStateChanged(channel);
            // Configure and open channel
            antChannelSetup(
                    (byte) 0x01, // Network: 1 (ANT+)
                    channel // channelConfig[channel] holds all the required info
                    );
        }
        else
        {
            switch(channel)
            {
                case HRM_CHANNEL:
                    mDeferredHrmStart = true;
                    mHrmState = ChannelStates.PENDING_OPEN;
                    break;
                case SDM_CHANNEL:
                    mDeferredSdmStart = true;
                    mSdmState = ChannelStates.PENDING_OPEN;
                    break;
                case WEIGHT_CHANNEL:
                    mDeferredWeightStart = true;
                    mWeightState = ChannelStates.PENDING_OPEN;
                    break;
            }
        }
    }
    
    /**
     * Attempts to cleanly close a specified channel 
     * @param channel The channel to close.
     */
    public void closeChannel(byte channel)
    {
        channelConfig[channel].isInitializing = false;
        channelConfig[channel].isDeinitializing = true;

        switch(channel)
        {
            case HRM_CHANNEL:
                mHrmState = ChannelStates.CLOSED;
                break;
            case SDM_CHANNEL:
                mSdmState = ChannelStates.CLOSED;
                break;
            case WEIGHT_CHANNEL:
                mWeightState = ChannelStates.CLOSED;
                break;
        }
        if(mCallbackSink != null)
            mCallbackSink.notifyChannelStateChanged(channel);
        try
        {
           mAntReceiver.ANTCloseChannel(channel);
           // Unassign channel after getting channel closed event
        }
        catch (AntInterfaceException e)
        {
           Log.w(TAG, "closeChannel: could not cleanly close channel " + channel + ".");
           antError();
        }
        if((mHrmState == ChannelStates.CLOSED || mHrmState == ChannelStates.OFFLINE) &&
                (mSdmState == ChannelStates.CLOSED || mSdmState == ChannelStates.OFFLINE) &&
                (mWeightState == ChannelStates.CLOSED || mWeightState == ChannelStates.OFFLINE))
        {
            Log.i(TAG, "Stopping service.");
            mContext.stopService(new Intent(mContext, ANTPlusService.class));
        }
    }
    
    /**
     * Resets the channel state machines, used in error recovery.
     */
    public void clearChannelStates()
    {
        Log.i(TAG, "Stopping service.");
        mContext.stopService(new Intent(mContext, ANTPlusService.class));
        mHrmState = ChannelStates.CLOSED;
        mSdmState = ChannelStates.CLOSED;
        mWeightState = ChannelStates.CLOSED;
        if(mCallbackSink != null)
        {
            mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
            mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
            mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
        }
    }
    
    /** check to see if a channel is open */
    public boolean isChannelOpen(byte channel)
    {
        switch(channel)
        {
            case HRM_CHANNEL:
                if(mHrmState == ChannelStates.CLOSED || mHrmState == ChannelStates.OFFLINE)
                    return false;
                break;
            case SDM_CHANNEL:
                if(mSdmState == ChannelStates.CLOSED || mSdmState == ChannelStates.OFFLINE)
                    return false;
                break;
            case WEIGHT_CHANNEL:
                if(mWeightState == ChannelStates.CLOSED || mWeightState == ChannelStates.OFFLINE)
                    return false;
                break;
            default:
                return false;
        }
        return true;
    }
    
    /** request an ANT reset */
    public void requestReset()
    {
        try
        {
            mAntResetSent = true;
            mAntReceiver.ANTResetSystem();
            setAntConfiguration();
        } catch (AntInterfaceException e) {
            Log.e(TAG, "Could not reset ANT. " + e.getMessage());
            mAntResetSent = false;
            //Cancel pending channel open requests
            if(mDeferredHrmStart)
            {
                mDeferredHrmStart = false;
                mHrmState = ChannelStates.CLOSED;
                if(mCallbackSink != null)
                    mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
            }
            if(mDeferredSdmStart)
            {
                mDeferredSdmStart = false;
                mSdmState = ChannelStates.CLOSED;
                if(mCallbackSink != null)
                    mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
            }
            if(mDeferredWeightStart)
            {
                mDeferredWeightStart = false;
                mWeightState = ChannelStates.CLOSED;
                if(mCallbackSink != null)
                    mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
            }
        }
    }
    
    /**
     * Check if ANT is enabled
     * @return True if ANT is enabled, false otherwise.
     */
    public boolean isEnabled()
    {
        if(mAntReceiver == null || !mAntReceiver.isServiceConnected())
            return false;
        try
        {
            return mAntReceiver.isEnabled();
        } catch (AntInterfaceException e)
        {
            Log.w(TAG, "Problem checking enabled state. " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Attempt to enable the ANT chip.
     */
    public void doEnable()
    {
        if(mAntReceiver == null || mDisabling || isAirPlaneModeOn())
            return;
        try
        {
            mAntReceiver.enable();
        } catch (AntInterfaceException e)
        {
            //Not much error recovery possible.
            Log.e(TAG, "Could not enable ANT. " + e.getMessage());
            return;
        }
    }
    
    /**
     * Attempt to disable the ANT chip.
     */
    public void doDisable()
    {
        if(mAntReceiver == null || mEnabling)
            return;
        try
        {
            mAntReceiver.disable();
        } catch (AntInterfaceException e)
        {
            //Not much error recovery possible.
            Log.e(TAG, "Could not disable ANT. " + e.getMessage());
            return;
        }
    }
    
    /** Receives all of the ANT status intents. */
    private final BroadcastReceiver mAntStatusReceiver = new BroadcastReceiver() 
    {      
       public void onReceive(Context context, Intent intent) 
       {
          String ANTAction = intent.getAction();

          Log.d(TAG, "enter onReceive: " + ANTAction);
          if (ANTAction.equals(AntInterfaceIntent.ANT_ENABLING_ACTION))
          {
              Log.i(TAG, "onReceive: ANT ENABLING");
              mEnabling = true;
              mDisabling = false;
              mAntStateText = "Enabling"; // mContext.getString(R.string.Text_Enabling);
              if(mCallbackSink != null)
                  mCallbackSink.notifyAntStateChanged();
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_ENABLED_ACTION)) 
          {
             Log.i(TAG, "onReceive: ANT ENABLED");
             
             mEnabling = false;
             mDisabling = false;
             mAntStateText = "Enabled"; // mContext.getString(R.string.Text_Enabled);
             if(mCallbackSink != null)
                 mCallbackSink.notifyAntStateChanged();
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_DISABLING_ACTION))
          {
              Log.i(TAG, "onReceive: ANT DISABLING");
              mEnabling = false;
              mDisabling = true;
              mAntStateText = "Disabling"; // mContext.getString(R.string.Text_Disabling);
              if(mCallbackSink != null)
                  mCallbackSink.notifyAntStateChanged();
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_DISABLED_ACTION)) 
          {
             Log.i(TAG, "onReceive: ANT DISABLED");
             mHrmState = ChannelStates.CLOSED;
             mSdmState = ChannelStates.CLOSED;
             mWeightState = ChannelStates.CLOSED;
             mAntStateText = "Disabled"; // mContext.getString(R.string.Text_Disabled);
             
             mEnabling = false;
             mDisabling = false;
             
             if(mCallbackSink != null)
             {
                 mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
                 mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
                 mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                 mCallbackSink.notifyAntStateChanged();
             }
             Log.i(TAG, "Stopping service.");
             mContext.stopService(new Intent(mContext, ANTPlusService.class));
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_RESET_ACTION))
          {
             Log.d(TAG, "onReceive: ANT RESET");
             
             Log.i(TAG, "Stopping service.");
             mContext.stopService(new Intent(mContext, ANTPlusService.class));
             
             if(false == mAntResetSent)
             {
                //Someone else triggered an ANT reset
                Log.d(TAG, "onReceive: ANT RESET: Resetting state");
                
                if(mHrmState != ChannelStates.CLOSED)
                {
                   mHrmState = ChannelStates.CLOSED;
                   if(mCallbackSink != null)
                       mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                }
                
                if(mSdmState != ChannelStates.CLOSED)
                {
                   mSdmState = ChannelStates.CLOSED;
                   if(mCallbackSink != null)
                       mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
                }
                
                if(mWeightState != ChannelStates.CLOSED)
                {
                   mWeightState = ChannelStates.CLOSED;
                   if(mCallbackSink != null)
                       mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
                }
             }
             else
             {
                mAntResetSent = false;
                //Reconfigure event buffering
                setAntConfiguration();
                //Check if opening a channel was deferred, if so open it now.
                if(mDeferredHrmStart)
                {
                    openChannel(HRM_CHANNEL, false);
                    mDeferredHrmStart = false;
                }
                if(mDeferredSdmStart)
                {
                    openChannel(SDM_CHANNEL, false);
                    mDeferredSdmStart = false;
                }
                if(mDeferredWeightStart)
                {
                    openChannel(WEIGHT_CHANNEL, false);
                    mDeferredWeightStart = false;
                }
             }
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_INTERFACE_CLAIMED_ACTION)) 
          {
             Log.i(TAG, "onReceive: ANT INTERFACE CLAIMED");
             
             boolean wasClaimed = mClaimedAntInterface;
             
             // Could also read ANT_INTERFACE_CLAIMED_PID from intent and see if it matches the current process PID.
             try
             {
                 mClaimedAntInterface = mAntReceiver.hasClaimedInterface();

                 if(mClaimedAntInterface)
                 {
                     Log.i(TAG, "onReceive: ANT Interface claimed");

                     receiveAntRxMessages(true);

                     mAntStateText = "Interface claimed"; // mContext.getString(R.string.Text_ANT_In_Use);
                     if(mCallbackSink != null)
                         mCallbackSink.notifyAntStateChanged();
                 }
                 else
                 {
                     // Another application claimed the ANT Interface...
                     if(wasClaimed)
                     {
                         // ...and we had control before that.  
                         Log.i(TAG, "onReceive: ANT Interface released");
                         
                         Log.i(TAG, "Stopping service.");
                         mContext.stopService(new Intent(mContext, ANTPlusService.class));

                         receiveAntRxMessages(false);
                         
                         mAntStateText = "In use"; // mContext.getString(R.string.Text_ANT_In_Use);
                         if(mCallbackSink != null)
                             mCallbackSink.notifyAntStateChanged();
                     }
                 }
             }
             catch(AntInterfaceException e)
             {
                 antError();
             }
          }
          else if (ANTAction.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED))
          {
              Log.i(TAG, "onReceive: AIR_PLANE_MODE_CHANGED");
              if(isAirPlaneModeOn())
              {
                  mHrmState = ChannelStates.CLOSED;
                  mSdmState = ChannelStates.CLOSED;
                  mWeightState = ChannelStates.CLOSED;
                  mAntStateText = "Airplane mode"; // mContext.getString(R.string.Text_Airplane_Mode);
                  
                  Log.i(TAG, "Stopping service.");
                  mContext.stopService(new Intent(mContext, ANTPlusService.class));
                  
                  if(mCallbackSink != null)
                  {
                      mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
                      mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
                      mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                      mCallbackSink.notifyAntStateChanged();
                  }
              }
              else
              {
                  if(mCallbackSink != null)
                      mCallbackSink.notifyAntStateChanged();
              }
          }
          if(mCallbackSink != null)
              mCallbackSink.notifyAntStateChanged();
       }
    };
    
    public static String getHexString(byte[] data)
    {
        if(null == data)
        {
            return "";
        }

        StringBuffer hexString = new StringBuffer();
        for(int i = 0;i < data.length; i++)
        {
           hexString.append("[").append(String.format("%02X", data[i] & 0xFF)).append("]");
        }

        return hexString.toString();
    }
    
    /** Receives all of the ANT message intents and dispatches to the proper handler. */
    private final BroadcastReceiver mAntMessageReceiver = new BroadcastReceiver() 
    {      
       Context mContext;

       public void onReceive(Context context, Intent intent) 
       {
          mContext = context;
          String ANTAction = intent.getAction();

          Log.d(TAG, "enter onReceive: " + ANTAction);
          if (ANTAction.equals(AntInterfaceIntent.ANT_RX_MESSAGE_ACTION)) 
          {
             Log.d(TAG, "onReceive: ANT RX MESSAGE");

             byte[] ANTRxMessage = intent.getByteArrayExtra(AntInterfaceIntent.ANT_MESSAGE);

             Log.d(TAG, "Rx:"+ getHexString(ANTRxMessage));

             switch(ANTRxMessage[AntMesg.MESG_ID_OFFSET])
             {
                 case AntMesg.MESG_STARTUP_MESG_ID:
                     break;
                 case AntMesg.MESG_BROADCAST_DATA_ID:
                 case AntMesg.MESG_ACKNOWLEDGED_DATA_ID:
                     byte channelNum = ANTRxMessage[AntMesg.MESG_DATA_OFFSET];
                     switch(channelNum)
                     {
                         case HRM_CHANNEL:
                             antDecodeHRM(ANTRxMessage);
                             break;
                         case SDM_CHANNEL:
                             antDecodeSDM(ANTRxMessage);
                             break;
                         case WEIGHT_CHANNEL:
                             antDecodeWeight(ANTRxMessage);
                             break;
                     }
                     break;
                 case AntMesg.MESG_BURST_DATA_ID:
                     break;
                 case AntMesg.MESG_RESPONSE_EVENT_ID:
                     responseEventHandler(ANTRxMessage);
                     break;
                 case AntMesg.MESG_CHANNEL_STATUS_ID:
                     break;
                 case AntMesg.MESG_CHANNEL_ID_ID:
                     short deviceNum = (short) ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1]&0xFF | ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2]&0xFF) << 8)) & 0xFFFF);
                     switch(ANTRxMessage[AntMesg.MESG_DATA_OFFSET]) //Switch on channel number
                     {
                         case HRM_CHANNEL:
                             Log.i(TAG, "onRecieve: Received HRM device number: " + deviceNum);
                             mDeviceNumberHRM = deviceNum;
                             break;
                         case SDM_CHANNEL:
                             Log.i(TAG, "onRecieve: Received SDM device number: " + deviceNum);
                             mDeviceNumberSDM = deviceNum;
                             break;
                         case WEIGHT_CHANNEL:
                             Log.i(TAG, "onRecieve: Received Weight device number: " + deviceNum);
                             mDeviceNumberWGT = deviceNum;
                             break;
                     }
                     break;
                 case AntMesg.MESG_VERSION_ID:
                     break;
                 case AntMesg.MESG_CAPABILITIES_ID:
                     break;
                 case AntMesg.MESG_GET_SERIAL_NUM_ID:
                     break;
                 case AntMesg.MESG_EXT_ACKNOWLEDGED_DATA_ID:
                     break;
                 case AntMesg.MESG_EXT_BROADCAST_DATA_ID:
                     break;
                 case AntMesg.MESG_EXT_BURST_DATA_ID:
                     break;
             }
          }
       }
       
       /**
        * Handles response and channel event messages
        * @param ANTRxMessage
        */
       private void responseEventHandler(byte[] ANTRxMessage)
       {
           // For a list of possible message codes
           // see ANT Message Protocol and Usage section 9.5.6.1
           // available from thisisant.com
           byte channelNumber = ANTRxMessage[AntMesg.MESG_DATA_OFFSET];

           if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_EVENT_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.EVENT_RX_SEARCH_TIMEOUT))
           {
               // A channel timed out searching, unassign it
               channelConfig[channelNumber].isInitializing = false;
               channelConfig[channelNumber].isDeinitializing = false;

               switch(channelNumber)
               {
                   case HRM_CHANNEL:
                       try
                       {
                           Log.i(TAG, "responseEventHandler: Received search timeout on HRM channel");

                           mHrmState = ChannelStates.OFFLINE;
                           if(mCallbackSink != null)
                               mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                           mAntReceiver.ANTUnassignChannel(HRM_CHANNEL);
                       }
                       catch(AntInterfaceException e)
                       {
                           antError();
                       }
                       break;
                   case SDM_CHANNEL:
                       try
                       {
                           Log.i(TAG, "responseEventHandler: Received search timeout on SDM channel");

                           mSdmState = ChannelStates.OFFLINE;
                           if(mCallbackSink != null)
                               mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
                           mAntReceiver.ANTUnassignChannel(SDM_CHANNEL);
                       }
                       catch(AntInterfaceException e)
                       {
                           antError();
                       }
                       break;
                   case WEIGHT_CHANNEL:
                       try
                       {
                           Log.i(TAG, "responseEventHandler: Received search timeout on weight channel");

                           mWeightState = ChannelStates.OFFLINE;
                           if(mCallbackSink != null)
                               mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
                           mAntReceiver.ANTUnassignChannel(WEIGHT_CHANNEL);
                       }
                       catch(AntInterfaceException e)
                       {
                           antError();
                       }
                       break;
               }
               if((mHrmState == ChannelStates.CLOSED || mHrmState == ChannelStates.OFFLINE) &&
                       (mSdmState == ChannelStates.CLOSED || mSdmState == ChannelStates.OFFLINE) &&
                       (mWeightState == ChannelStates.CLOSED || mWeightState == ChannelStates.OFFLINE))
               {
                   Log.i(TAG, "Stopping service.");
                   mContext.stopService(new Intent(mContext, ANTPlusService.class));
               }
           }
           
           if (channelConfig[channelNumber].isInitializing)
           {
               if (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] != 0) // Error response
               {
                   Log.e(TAG, String.format("Error code(%#02x) on message ID(%#02x) on channel %d", ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2], ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1], channelNumber));
               }
               else
               {
                   switch (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1]) // Switch on Message ID
                   {
                       case AntMesg.MESG_ASSIGN_CHANNEL_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelId(channelNumber, channelConfig[channelNumber].deviceNumber, channelConfig[channelNumber].deviceType, channelConfig[channelNumber].TransmissionType);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_ID_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelPeriod(channelNumber, channelConfig[channelNumber].period);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_MESG_PERIOD_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelRFFreq(channelNumber, channelConfig[channelNumber].freq);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_RADIO_FREQ_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelSearchTimeout(channelNumber, (byte)0); // Disable high priority search
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_SEARCH_TIMEOUT_ID:
                           try
                           {
                               mAntReceiver.ANTSetLowPriorityChannelSearchTimeout(channelNumber,(byte) 12); // Set search timeout to 30 seconds (low priority search)
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_SET_LP_SEARCH_TIMEOUT_ID:
                           if (channelConfig[channelNumber].deviceNumber == WILDCARD)
                           {
                               try
                               {
                                   mAntReceiver.ANTSetProximitySearch(channelNumber, channelConfig[channelNumber].proxSearch);   // Configure proximity search, if using wild card search
                               }
                               catch (AntInterfaceException e)
                               {
                                   antError();
                               }
                           }
                           else
                           {
                               try
                               {
                                   mAntReceiver.ANTOpenChannel(channelNumber);
                               }
                               catch (AntInterfaceException e)
                               {
                                   antError();
                               }
                           }
                           break;
                       case AntMesg.MESG_PROX_SEARCH_CONFIG_ID:
                           try
                           {
                               mAntReceiver.ANTOpenChannel(channelNumber);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_OPEN_CHANNEL_ID:
                           channelConfig[channelNumber].isInitializing = false;
                           switch (channelNumber)
                           {
                               case HRM_CHANNEL:
                                   mHrmState = ChannelStates.SEARCHING;
                                   if(mCallbackSink != null)
                                       mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                                   break;
                               case SDM_CHANNEL:
                                   mSdmState = ChannelStates.SEARCHING;
                                   if(mCallbackSink != null)
                                       mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
                                   break;
                               case WEIGHT_CHANNEL:
                                   mWeightState = ChannelStates.SEARCHING;
                                   if(mCallbackSink != null)
                                       mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
                                   break;
                           }
                   }
               }
           }
           else if (channelConfig[channelNumber].isDeinitializing)
           {
               if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_EVENT_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.EVENT_CHANNEL_CLOSED))
               {
                   try
                   {
                       mAntReceiver.ANTUnassignChannel(channelNumber);
                   }
                   catch (AntInterfaceException e)
                   {
                       antError();
                   }
               }
               else if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_UNASSIGN_CHANNEL_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.RESPONSE_NO_ERROR))
               {
                   channelConfig[channelNumber].isDeinitializing = false;
               }
           }
       }
       
       
       /**
        * Decode ANT+ Weight scale messages.
        *
        * @param ANTRxMessage the received ANT message.
        */
       private void antDecodeWeight(byte[] ANTRxMessage)
       {
           //TODO: Send the decoded data to the service to be recorded
          Log.d(TAG, "antDecodeWeight start");
          
         Log.d(TAG, "antDecodeWeight: Received broadcast");

         if(mDeviceNumberWGT == WILDCARD)
         {
             try
             {
                 Log.i(TAG, "antDecodeWeight: Requesting device number");

                 mAntReceiver.ANTRequestMessage(WEIGHT_CHANNEL, AntMesg.MESG_CHANNEL_ID_ID);
             }
             catch(AntInterfaceException e)
             {
                 antError();
             }
         }

         if(!mHasSentProfile)
         {
             try
             {
                 byte[] Profile = {0x3A, 0x10, 0x00, 0x02, 
                         (byte)0xFF, (byte)0x99, (byte)0xAD, 0x04};   // Sample user profile (male, 25)
                 mAntReceiver.ANTSendBroadcastData(WEIGHT_CHANNEL, Profile);
                 mHasSentProfile = true;
                 Log.i(TAG, "Weight user profile sent");
             }
             catch(AntInterfaceException e)
             {
                 antError();
             }
         }
         else
         {               
            if(ANTRxMessage[3] == 0x01)   // check for data page 1
            {
               mWeight = (ANTRxMessage[9]&0xFF  | ((ANTRxMessage[10]&0xFF) << 8)) & 0xFFFF;
               if(mWeight == 0xFFFF)
               {
                  mWeightState = ChannelStates.TRACKING_STATUS;
                  mWeightStatus = "Invalid"; // mContext.getResources().getString(R.string.Invalid);
                  if(mCallbackSink != null)
                      mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
               }
               else if(mWeight == 0xFFFE)
               {
                  mWeightState = ChannelStates.TRACKING_STATUS;
                  if(!mSessionDone)
                     mWeightStatus = "Computing"; // mContext.getResources().getString(R.string.Computing);
                  else
                     mWeightStatus = "New session"; // mContext.getResources().getString(R.string.NewSession);
                  if(mCallbackSink != null)
                      mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
               }
               else
               {
                  mWeightState = ChannelStates.TRACKING_DATA;
                  mWeightStatus = "Connected"; // mContext.getResources().getString(R.string.Connected);
                  mSessionDone = true;
                  if(mCallbackSink != null)
                  {
                      mCallbackSink.notifyChannelStateChanged(WEIGHT_CHANNEL);
                      mCallbackSink.notifyChannelDataChanged(WEIGHT_CHANNEL);
                  }
               }
            }
          }
          Log.d(TAG, "antDecodeWeight end");
       }

       /**
        * Decode ANT+ SDM messages.
        *
        * @param ANTRxMessage the received ANT message.
        */
       private void antDecodeSDM(byte[] ANTRxMessage)
       {
         //TODO: Send the decoded data to the service to be recorded
          Log.d(TAG, "antDecodeSDM start");
          
         Log.d(TAG, "antDecodeSDM: Received broadcast");

         if(mSdmState != ChannelStates.CLOSED)
         {
            Log.d(TAG, "antDecodeSDM: Tracking data");

            mSdmState = ChannelStates.TRACKING_DATA;
            if(mCallbackSink != null)
                mCallbackSink.notifyChannelStateChanged(SDM_CHANNEL);
         }
         
         // If using a wild card search, request the channel ID
         if(mDeviceNumberSDM == WILDCARD)
         {
             try
             {
                 Log.i(TAG, "antDecodeSDM: Requesting device number");

                 mAntReceiver.ANTRequestMessage(SDM_CHANNEL, AntMesg.MESG_CHANNEL_ID_ID);
             }
             catch(AntInterfaceException e)
             {
                 antError();
             }
         }

         if ((ANTRxMessage[3]) == 0x01)  //check for data page 1
         {
            mSpeed = (ANTRxMessage[7] & 0x0F) + ((ANTRxMessage[8] & 0xFF)) / 256.0f;
            float distance = (ANTRxMessage[6] & 0xFF) + (((ANTRxMessage[7] >>> 4) & 0x0F) / 16.0f);
            int strides = ANTRxMessage[9] & 0xFF;
            
            if(!mDistanceInit)   // Calculate cumulative distance
            {
               mPrevDistance = distance;
               mDistanceInit = true;
            }
            
            if(!mStridesInit)    // Calculate cumulative stride count
                {
                   mPrevStrides = strides;
                   mStridesInit = true;
                }          
  
                mAccumDistance += (distance - mPrevDistance);
                if(mPrevDistance > distance)
                   mAccumDistance += 256.0;
                mPrevDistance = distance;
                
                mAccumStrides += (strides - mPrevStrides);
                if(mPrevStrides > strides)
                   mAccumStrides += 256;
                mPrevStrides = strides;
             }
             if(ANTRxMessage[3] == 0x02) // check for data page 2
         {
            mCadence = (ANTRxMessage[6] & 0xFF) + (((ANTRxMessage[7] >>> 4) & 0x0F) / 16.0f);               
            mSpeed = (ANTRxMessage[7] & 0x0F) + ((ANTRxMessage[8] & 0xFF)) / 256.0f;
         }
         if(mCallbackSink != null)
             mCallbackSink.notifyChannelDataChanged(SDM_CHANNEL);
        
         Log.d(TAG, "antDecodeSDM end");
       }

       /**
        * Decode ANT+ HRM messages.
        *
        * @param ANTRxMessage the received ANT message.
        */
       private void antDecodeHRM(byte[] ANTRxMessage)
       {
         //TODO: Send the decoded data to the service to be recorded
          Log.d(TAG, "antDecodeHRM start");
          
         Log.d(TAG, "antDecodeHRM: Received broadcast");
         
         if(mHrmState != ChannelStates.CLOSED)
         {
            Log.d(TAG, "antDecodeHRMM: Tracking data");

            mHrmState = ChannelStates.TRACKING_DATA;
            if(mCallbackSink != null)
                mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
         }

         if(mDeviceNumberHRM == WILDCARD)
         {
             try
             {
                 Log.i(TAG, "antDecodeHRM: Requesting device number");
                 mAntReceiver.ANTRequestMessage(HRM_CHANNEL, AntMesg.MESG_CHANNEL_ID_ID);
             }
             catch(AntInterfaceException e)
             {
                 antError();
             }
         }

         // Monitor page toggle bit
         if(mStateHRM != HRMStatePage.EXT)
         {
            if(mStateHRM == HRMStatePage.INIT)
            {
               if((ANTRxMessage[3] & (byte) 0x80) == 0)
                  mStateHRM = HRMStatePage.TOGGLE0;
               else
                  mStateHRM = HRMStatePage.TOGGLE1;
            }
            else if(mStateHRM == HRMStatePage.TOGGLE0)
            {
               if((ANTRxMessage[3] & (byte) 0x80) != 0)
                  mStateHRM = HRMStatePage.EXT;
            }
            else if(mStateHRM == HRMStatePage.TOGGLE1)
            {
               if((ANTRxMessage[3] & (byte) 0x80) == 0)
                  mStateHRM = HRMStatePage.EXT;
            }
         }
         
         // Heart rate available in all pages and regardless of toggle bit            
         mBPM = ANTRxMessage[10] & 0xFF;
         if(mCallbackSink != null)
             mCallbackSink.notifyChannelDataChanged(HRM_CHANNEL);
             
          Log.d(TAG, "antDecodeHRM end");
       }
    };
    
    /**
     * ANT Channel Configuration.
     *
     * @param networkNumber the network number
     * @param channelNumber the channel number
     * @param deviceNumber the device number
     * @param deviceType the device type
     * @param txType the tx type
     * @param channelPeriod the channel period
     * @param radioFreq the radio freq
     * @param proxSearch the prox search
     * @return true, if successfully configured and opened channel
     */   
    private void antChannelSetup(byte networkNumber, byte channel)
    {
       try
       {
           channelConfig[channel].isInitializing = true;
           channelConfig[channel].isDeinitializing = false;

           mAntReceiver.ANTAssignChannel(channel, AntDefine.PARAMETER_RX_NOT_TX, networkNumber);  // Assign as slave channel on selected network (0 = public, 1 = ANT+, 2 = ANTFS)
           // The rest of the channel configuration will occur after the response is received (in responseEventHandler)
       }
       catch(AntInterfaceException aie)
       {
           antError();
       }
    }
    
    /**
     * Enable/disable receiving ANT Rx messages.
     *
     * @param register If want to register to receive the ANT Rx Messages
     */
    private void receiveAntRxMessages(boolean register)
    {
        if(register)
        {
            Log.i(TAG, "receiveAntRxMessages: START");
            mContext.registerReceiver(mAntMessageReceiver, new IntentFilter(AntInterfaceIntent.ANT_RX_MESSAGE_ACTION));
        }
        else
        {
            try
            {
                mContext.unregisterReceiver(mAntMessageReceiver);
            }
            catch(IllegalArgumentException e)
            {
                // Receiver wasn't registered, ignore as that's what we wanted anyway
            }

            Log.i(TAG, "receiveAntRxMessages: STOP");
        }
    }
    
    /**
     * Checks if ANT is sensitive to airplane mode, if airplane mode is on and if ANT is not toggleable in airplane
     * mode. Only returns true if all 3 criteria are met.
     * @return True if airplane mode is stopping ANT from being enabled, false otherwise.
     */
    private boolean isAirPlaneModeOn()
    {
        if(!Settings.System.getString(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_RADIOS).contains(RADIO_ANT))
            return false;
        if(Settings.System.getInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 0)
            return false;
        
        try
        {
            Field field = Settings.System.class.getField("AIRPLANE_MODE_TOGGLEABLE_RADIOS");
            if(Settings.System.getString(mContext.getContentResolver(),
                    (String) field.get(null)).contains(RADIO_ANT))
                return false;
            else
                return true;
        } catch(Exception e)
        {
            return true; //This is expected if the list does not yet exist so we just assume we would not be on it.
        }
    }
}
