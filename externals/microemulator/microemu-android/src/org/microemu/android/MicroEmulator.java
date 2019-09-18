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
 *  @version $Id: MicroEmulator.java 2328 2010-03-03 18:45:34Z barteo@gmail.com $
 */

package org.microemu.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import org.microemu.DisplayAccess;
import org.microemu.MIDletAccess;
import org.microemu.MIDletBridge;
import org.microemu.android.device.AndroidDevice;
import org.microemu.android.device.AndroidInputMethod;
import org.microemu.android.device.ui.AndroidCanvasUI;
import org.microemu.android.device.ui.AndroidCommandUI;
import org.microemu.android.device.ui.AndroidDisplayableUI;
import org.microemu.android.util.AndroidLoggerAppender;
import org.microemu.android.util.AndroidRecordStoreManager;
import org.microemu.android.util.AndroidRepaintListener;
import org.microemu.app.Common;
import org.microemu.app.util.MIDletSystemProperties;
import org.microemu.device.Device;
import org.microemu.device.DeviceFactory;
import org.microemu.device.ui.CommandUI;
import org.microemu.log.Logger;
import org.microemu.util.JadProperties;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.Window;

public class MicroEmulator extends MicroEmulatorActivity {
	
	public static final String LOG_TAG = "TrekBuddy/ME";
		
	public Common common;
	
	private MIDlet midlet;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        Logger.debug("onCreate");
        super.onCreate(icicle);

        Logger.removeAllAppenders();
        Logger.setLocationEnabled(false);
        Logger.addAppender(new AndroidLoggerAppender());
        
        System.setOut(new PrintStream(new OutputStream() {
        	
        	StringBuffer line = new StringBuffer();

			@Override
			public void write(int oneByte) throws IOException {
				if (((char) oneByte) == '\n') {
					if (line.length() > 0) {
						Logger.debug(line.toString());
						line.delete(0, line.length());
					}
				} else {
					line.append((char) oneByte);
				}
			}
        	
        }));
        
        System.setErr(new PrintStream(new OutputStream() {

        	StringBuffer line = new StringBuffer();

			@Override
			public void write(int oneByte) throws IOException {
				if (((char) oneByte) == '\n') {
					if (line.length() > 0) {
						Logger.debug(line.toString());
						line.delete(0, line.length());
					}
				} else {
					line.append((char) oneByte);
				}
			}
        	
        }));

        android.content.SharedPreferences settings = getPreferences(0);
        windowFullscreen = settings.getBoolean("fullscreen", false);
        Logger.info("use fullscreen? " + windowFullscreen);
        windowLandscape = settings.getBoolean("landscape", false);
        Logger.info("force landscape? " + windowLandscape);
        if (windowFullscreen) {
            this.getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        }
        if (windowLandscape) {
            this.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        hasPermanentMenuKey = AndroidRuntimeMethods.hasPermanentMenuKey(this);
        Logger.info("hasPermanentMenuKey? " + hasPermanentMenuKey);

//	if (android.os.Build.VERSION.SDK_INT >= 14) {
//		Logger.info("set device default theme");
//		setTheme(0x01030128); // id of Theme.DeviceDefault 
//		getTheme().applyStyle(net.trekbuddy.midlet.R.style._AppThemeTitle, true);
//		getTheme().applyStyle(net.trekbuddy.midlet.R.style._AppThemeActionBar, true);
//	}
//	if (windowFullscreen) {
//		Logger.info("apply fullscreen style");
//		getTheme().applyStyle(net.trekbuddy.midlet.R.style._AppThemeFullscreen, true);
//	}
//
//	if (android.os.Build.VERSION.SDK_INT >= 14) {
//		hasPermanentMenuKey = android.view.ViewConfiguration.get(this).hasPermanentMenuKey();
//		Logger.info("hasPermanentMenuKey? " + hasPermanentMenuKey);	
//	}

        if (android.os.Build.VERSION.SDK_INT >= 14 && android.os.Build.VERSION.SDK_INT < 21) {
            actionBar = AndroidRuntimeMethods.getActionBar(this); // actionBar presence controlled by XML layout (displayable.xml)
            if (actionBar != null) {
                AndroidRuntimeMethods.setActionBarVisibility(this, actionBar, android.view.View.GONE); // initially hidden (map screen is current)
            }
            Logger.info("actionBar = " + actionBar);
        }

        if (android.os.Build.VERSION.SDK_INT >= 23) { // 6.0+
            final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
            final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
            final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
            Logger.info("re-acquire dangerous permissions (6.0+)");
            requestPermissions(
                    new String[] {
                            READ_EXTERNAL_STORAGE,
                            WRITE_EXTERNAL_STORAGE,
                            ACCESS_FINE_LOCATION
                    }, 112); // 112 is request code... wtf
        }

        java.util.List params = new ArrayList();
        params.add("--usesystemclassloader");
        params.add("--quit");
        
        String midletClassName;
        String jadName = null;
		try {
			final Class r = Class.forName(getComponentName().getPackageName() + ".R$string");
			final Field[] fields = r.getFields();
			final Class[] classes = r.getClasses();
	        midletClassName = getResources().getString(r.getField("class_name").getInt(null));
            try {
                jadName = getResources().getString(r.getField("jad_name").getInt(null));
            } catch (NoSuchFieldException e) {
            }

	        params.add(midletClassName);	       
		} catch (Exception e) {
			Logger.error(e);
			return;
		}

        common = new Common(emulatorContext);
        common.setRecordStoreManager(new AndroidRecordStoreManager(this));
        common.setDevice(new AndroidDevice(emulatorContext, this));        
        common.initParams(params, null, AndroidDevice.class);
               
        System.setProperty("microedition.platform", "microemu-android");
        System.setProperty("microedition.configuration", "CLDC-1.1");
        System.setProperty("microedition.profiles", "MIDP-2.0");
        System.setProperty("microedition.locale", Locale.getDefault().toString());

        try { // TODO JadProperties
            final Bundle meta = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA).metaData;
            System.setProperty("MIDlet-Version", meta.getString("app_version"));
        } catch (PackageManager.NameNotFoundException e) {
            // ignore
        }
        
        /* extra */
        final android.util.DisplayMetrics dm = getResources().getDisplayMetrics(); 
        System.setProperty("microemu.display.density", Float.toString(dm.density));
        System.setProperty("microemu.display.densityDpi", Float.toString(dm.densityDpi));
        System.setProperty("microemu.display.xdpi", Float.toString(dm.xdpi));
        System.setProperty("microemu.display.ydpi", Float.toString(dm.ydpi));

//        context = this;

        /* JSR-75 */
        final Map properties = new HashMap();
        properties.put("fsRoot", "/");
//        properties.put("fsSingle", "sdcard");
        common.registerImplementation("org.microemu.cldc.file.FileSystem", properties, false);
        MIDletSystemProperties.setPermission("javax.microedition.io.Connector.file.read", 1);
        MIDletSystemProperties.setPermission("javax.microedition.io.Connector.file.write", 1);
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            final String path = Environment.getExternalStorageDirectory().getAbsolutePath(); 
            final String url = "file://" + path;
            Logger.info("SD card is mounted at " + path);
            System.setProperty("fileconn.dir.memorycard", url);
        } else {
            Logger.warn("SD card is not mounted");
        }
        final java.io.File folder = android.os.Environment.getExternalStorageDirectory();
        if (folder != null) {
            try {
                System.setProperty("fileconn.dir.public", "file://" + folder.getCanonicalPath());
                System.setProperty("fileconn.dir.downloads", "file://" + folder.getCanonicalPath() + java.io.File.separator + "Download"); // value of DIRECTORY_DOWNLOADS since API 8
            } catch (Exception e) {
                Logger.error(e);
            }
        }

        if (jadName != null) {
            try {
    	        InputStream is = getAssets().open(jadName);
    	        common.jad = new JadProperties();
    	        common.jad.read(is);
            } catch (Exception e) {
            	Logger.error(e);
            }
        }
        
        initializeExtensions();
        
        common.setSuiteName(midletClassName);
        midlet = common.initMIDlet(false);
    }

    @Override
    protected void onPause() {
        Logger.debug("onPause; isFinishing? " + isFinishing());
        
	if (!isFinishing()) {
/* NOT NEEDED FOR TB???
		if (contentView != null) {
			if (contentView instanceof AndroidRepaintListener) {
				((AndroidRepaintListener) contentView).onPause();
			}
		}
*/
		/*
		 * see onResume - no deadlock danger here??
		 */
		final MIDletAccess ma = MIDletBridge.getMIDletAccess(midlet);
		if (ma != null) {
			Logger.debug("pause app and hide notify");
			ma.pauseApp();
			ma.getDisplayAccess().hideNotify();
		}
	}
      
	super.onPause();      
    }

    @Override
    protected void onResume() {
        Logger.debug("onResume");
        super.onResume();

	(new android.os.AsyncTask<Void,Void,Void>() {
		protected Void doInBackground(Void... params) {
			/*
			 * locks when not run in background (deadlock??)
			 */
			final MIDletAccess ma = MIDletBridge.getMIDletAccess(midlet);
			if (ma != null) {
				try {
					ma.startApp();
					ma.getDisplayAccess().showNotify();
				} catch (MIDletStateChangeException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
        }).execute();

/* original code:
        new Thread(new Runnable() {

            public void run()
            {
                MIDletAccess ma = MIDletBridge.getMIDletAccess(midlet);
                if (ma != null) {
                    try {
                        ma.startApp();
                    } catch (MIDletStateChangeException e) {
                        e.printStackTrace();
                    }
                }

                if (contentView != null) {
                    if (contentView instanceof AndroidRepaintListener) {
                        ((AndroidRepaintListener) contentView).onResume();
                    }
                    post(new Runnable() {
                        public void run() {
                            contentView.invalidate();
                        }
                    });
                 }
            }

        }).start();
*/
    }
    
	protected void initializeExtensions() {
	}

	private boolean ignoreBackKeyUp = false;
    
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		final MIDletAccess ma = MIDletBridge.getMIDletAccess();
		if (ma == null) {
			return false;
		}
		final DisplayAccess da = ma.getDisplayAccess();
		if (da == null) {
			return false;
		}
		final Displayable de = da.getCurrent();  
		if (de == null) {
			return false;
		}
		final AndroidDisplayableUI ui = (AndroidDisplayableUI) da.getDisplayableUI(de);
		if (ui == null) {
			return false;
		}		

		if (keyCode == KeyEvent.KEYCODE_BACK) {
			List<AndroidCommandUI> commands = ui.getCommandsUI();
			
			CommandUI cmd = getFirstCommandOfType(commands, Command.BACK);
			if (cmd != null) {
				if (ui.getCommandListener() != null) {
					ignoreBackKeyUp = true;
					da.commandAction(cmd.getCommand(), da.getCurrent());
				}
				return true;
			}

			cmd = getFirstCommandOfType(commands, Command.EXIT);
			if (cmd != null) {
				if (ui.getCommandListener() != null) {
					ignoreBackKeyUp = true;
					da.commandAction(cmd.getCommand(), da.getCurrent());
				}
				return true;
			}
			
			cmd = getFirstCommandOfType(commands, Command.CANCEL);
			if (cmd != null) {
				if (ui.getCommandListener() != null) {
					ignoreBackKeyUp = true;
					da.commandAction(cmd.getCommand(), da.getCurrent());
				}
				return true;
			}
			
/*
			android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
			intent.addCategory(android.content.Intent.CATEGORY_HOME);
			intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
*/
			moveTaskToBack(true);
			return true;
		}
					
		if (ui instanceof AndroidCanvasUI) {
			if (ignoreKey(keyCode)) {
				return false;    
			}

			final Device device = DeviceFactory.getDevice();
			((AndroidInputMethod) device.getInputMethod()).buttonPressed(event);

			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && ignoreBackKeyUp) {
			ignoreBackKeyUp = false;
			return true;
		}
		
		final MIDletAccess ma = MIDletBridge.getMIDletAccess();
		if (ma == null) {
			return false;
		}
		final DisplayAccess da = ma.getDisplayAccess();
		if (da == null) {
			return false;
		}
		final Displayable de = da.getCurrent();  
		if (de == null) {
			return false;
		}
		final AndroidDisplayableUI ui = (AndroidDisplayableUI) da.getDisplayableUI(de);
		if (ui == null) {
			return false;
		}		

		if (ui instanceof AndroidCanvasUI) {
			if (ignoreKey(keyCode)) {
				return false;    
			}
	
			final Device device = DeviceFactory.getDevice();
			((AndroidInputMethod) device.getInputMethod()).buttonReleased(event);
	
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}
	
	private CommandUI getFirstCommandOfType(final List<AndroidCommandUI> commands, final int commandType) {
		for (int i = 0, N = commands.size(); i < N; i++) {
			final CommandUI cmd = commands.get(i);
			if (cmd.getCommand().getCommandType() == commandType) {
				return cmd;
			}
		}	
		
		return null;
	}
	
    private boolean ignoreKey(int keyCode) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_MENU:
            return true;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
        case KeyEvent.KEYCODE_VOLUME_UP:
            return config.ignoreVolumeKeys;
        case KeyEvent.KEYCODE_HEADSETHOOK: 
            return true;
        default:
            return false;
        }    
    }
	
    private final static KeyEvent KEY_RIGHT_DOWN_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
    
    private final static KeyEvent KEY_RIGHT_UP_EVENT = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT);
	
    private final static KeyEvent KEY_LEFT_DOWN_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT);
    	
    private final static KeyEvent KEY_LEFT_UP_EVENT = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT);

    private final static KeyEvent KEY_DOWN_DOWN_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
    
    private final static KeyEvent KEY_DOWN_UP_EVENT = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN);
    	
    private final static KeyEvent KEY_UP_DOWN_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP);
    	
    private final static KeyEvent KEY_UP_UP_EVENT = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP);

    private final static float TRACKBALL_THRESHOLD = 0.4f; 
	
	private float accumulatedTrackballX = 0;
	
	private float accumulatedTrackballY = 0;
	
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE) {
			final MIDletAccess ma = MIDletBridge.getMIDletAccess();
			if (ma == null) {
				return false;
			}
			final DisplayAccess da = ma.getDisplayAccess();
			if (da == null) {
				return false;
			}
			final Displayable de = da.getCurrent();  
			if (de == null) {
				return false;
			}
			final AndroidDisplayableUI ui = (AndroidDisplayableUI) da.getDisplayableUI(de);
			if (ui instanceof AndroidCanvasUI) {
				float x = event.getX();
				float y = event.getY();
				if ((x > 0 && accumulatedTrackballX < 0) || (x < 0 && accumulatedTrackballX > 0)) {
					accumulatedTrackballX = 0;
				}
				if ((y > 0 && accumulatedTrackballY < 0) || (y < 0 && accumulatedTrackballY > 0)) {
					accumulatedTrackballY = 0;
				}
				if (accumulatedTrackballX + x > TRACKBALL_THRESHOLD) {
					accumulatedTrackballX -= TRACKBALL_THRESHOLD;
					KEY_RIGHT_DOWN_EVENT.dispatch(this);
					KEY_RIGHT_UP_EVENT.dispatch(this);
				} else if (accumulatedTrackballX + x < -TRACKBALL_THRESHOLD) {
					accumulatedTrackballX += TRACKBALL_THRESHOLD;
					KEY_LEFT_DOWN_EVENT.dispatch(this);
					KEY_LEFT_UP_EVENT.dispatch(this);
				}
				if (accumulatedTrackballY + y > TRACKBALL_THRESHOLD) {
					accumulatedTrackballY -= TRACKBALL_THRESHOLD;
					KEY_DOWN_DOWN_EVENT.dispatch(this);
					KEY_DOWN_UP_EVENT.dispatch(this);
				} else if (accumulatedTrackballY + y < -TRACKBALL_THRESHOLD) {
					accumulatedTrackballY += TRACKBALL_THRESHOLD;
					KEY_UP_DOWN_EVENT.dispatch(this);
					KEY_UP_UP_EVENT.dispatch(this);
				}
				accumulatedTrackballX += x;
				accumulatedTrackballY += y;
				
				return true;
			}
		}
		
		return super.onTrackballEvent(event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Logger.debug("onCreateOptionsMenu");

		getMenuInflater().inflate(net.trekbuddy.midlet.R.menu.menu, menu);
    
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
 		Logger.debug("onPrepareOptionsMenu");
   
		final MIDletAccess ma = MIDletBridge.getMIDletAccess();
		if (ma == null) {
			return false;
		}
		final DisplayAccess da = ma.getDisplayAccess();
		if (da == null) {
			return false;
		}
		final Displayable de = da.getCurrent();  
		if (de == null) {
			return false;
		}
		final AndroidDisplayableUI ui = (AndroidDisplayableUI) da.getDisplayableUI(de);
		if (ui == null) {
			return false;
		}		
		
		menu.clear();	

		boolean result = false;
		final List<AndroidCommandUI> commands = ui.getCommandsUI();
		for (int i = 0, N = commands.size(); i < N; i++) {
			final AndroidCommandUI cmd = commands.get(i);
			if (cmd.getCommand().getCommandType() != Command.BACK && cmd.getCommand().getCommandType() != Command.EXIT) {
				result = true;
				final MenuItem item = menu.add(Menu.NONE, i + Menu.FIRST, Menu.NONE, cmd.getCommand().getLabel());
				item.setIcon(cmd.getDrawable());
				if (android.os.Build.VERSION.SDK_INT >= 14) {
					AndroidRuntimeMethods.setShowAsAction(item);
				}
			}
		}

		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
//		Logger.debug("onOptionsItemSelected");
    
		final MIDletAccess ma = MIDletBridge.getMIDletAccess();
		if (ma == null) {
			return false;
		}
		final DisplayAccess da = ma.getDisplayAccess();
		if (da == null) {
			return false;
		}
		final Displayable de = da.getCurrent();  
		if (de == null) {
			return false;
		}
		final AndroidDisplayableUI ui = (AndroidDisplayableUI) da.getDisplayableUI(de);
		if (ui == null) {
			return false;
		}

		final int commandIndex = item.getItemId() - Menu.FIRST;
		final List<AndroidCommandUI> commands = ui.getCommandsUI();
		final CommandUI c = commands.get(commandIndex);
		if (c != null) {
			da.commandAction(c.getCommand(), da.getCurrent());
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

}