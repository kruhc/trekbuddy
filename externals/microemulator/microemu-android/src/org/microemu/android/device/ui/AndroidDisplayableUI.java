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
 *  @version $Id: AndroidDisplayableUI.java 2365 2010-04-12 20:18:01Z barteo@gmail.com $
 */

package org.microemu.android.device.ui;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

import org.microemu.android.AndroidRuntimeMethods;
import org.microemu.android.MicroEmulatorActivity;
import org.microemu.device.ui.CommandUI;
import org.microemu.device.ui.DisplayableUI;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public abstract class AndroidDisplayableUI implements DisplayableUI {
	
	protected MicroEmulatorActivity activity;
	
	protected Displayable displayable;
	
	protected volatile View view;
	
	protected TextView titleView;

	private View toolbarView;

	private boolean hasActionBar;

	private static Comparator<CommandUI> commandsPriorityComparator = new Comparator<CommandUI>() {

		public int compare(CommandUI first, CommandUI second) {
			if (first.getCommand().getPriority() == second.getCommand().getPriority()) {
				return 0;
			} else if (first.getCommand().getPriority() < second.getCommand().getPriority()) {
				return -1;
			} else {
				return 1;
			}
		}
		
	};
	
	private Vector<AndroidCommandUI> commands = new Vector<AndroidCommandUI>();
	
	private CommandListener commandListener;
	
	protected AndroidDisplayableUI(final MicroEmulatorActivity activity, final Displayable displayable, final boolean initView) {
		this.activity = activity;
		this.displayable = displayable;
		
		if (initView) {
			activity.post(new Runnable() {
				public void run() {

			try {
				view = activity.getLayoutInflater().inflate(net.trekbuddy.midlet.R.layout.displayable, null);
				if (android.os.Build.VERSION.SDK_INT >= 21) {
					toolbarView = view.findViewById(net.trekbuddy.midlet.R.id.toolbarView);
				} else {
					titleView = (TextView) view.findViewById(net.trekbuddy.midlet.R.id.titleView);
				}
				if (android.os.Build.VERSION.SDK_INT >= 14 && activity.actionBar != null) {
					hasActionBar = true;
					titleView.setVisibility(View.GONE);
				}
			} catch (android.view.InflateException e) {
				e.printStackTrace();
				throw e;
			}

				}
			});
		}
	}
	
	public Vector<AndroidCommandUI> getCommandsUI() {
		return commands;
	}
	
	public CommandListener getCommandListener() {
		return commandListener;
	}
	
	//
	// DisplayableUI
	//

	public void addCommandUI(CommandUI cmd) {
		synchronized (this) {
			if (!commands.contains(cmd)) {
				commands.add((AndroidCommandUI) cmd);
			}
			// TODO decide whether this is the best way for keeping sorted commands
			Collections.sort(commands, commandsPriorityComparator);
		}
	}

	public void removeCommandUI(CommandUI cmd) {
		synchronized (this) {
			commands.remove(cmd);
		}
	}

	public void setCommandListener(CommandListener l) {
		this.commandListener = l;
	}

	public void invalidate() {
		activity.post(new Runnable() {
			public void run() {
				if (toolbarView != null) {
					AndroidRuntimeMethods.setSubtitle(toolbarView, displayable.getTitle());
				} else if (activity.actionBar != null && hasActionBar) {
					AndroidRuntimeMethods.setSubtitle(activity.actionBar, displayable.getTitle());
				} else if (titleView != null) { // can be null for CanvasUI
					titleView.setText(displayable.getTitle());
				}
			}
		});
	}

	public void showNotify() {
		activity.post(new Runnable() {
			public void run() {
				if (android.os.Build.VERSION.SDK_INT >= 21) {
					AndroidRuntimeMethods.setActionBar(activity, toolbarView);
					// setActionBar(toolbar) also invalidates menu
				} else if (android.os.Build.VERSION.SDK_INT >= 14) {
					if (activity.actionBar != null) {
						if (hasActionBar) {
							AndroidRuntimeMethods.setActionBarVisibility(activity, activity.actionBar, View.VISIBLE);
						} else {
							AndroidRuntimeMethods.setActionBarVisibility(activity, activity.actionBar, View.GONE);
						}
					}
					AndroidRuntimeMethods.invalidateOptionsMenu(activity);
				}
				activity.setContentView(view);
				view.requestFocus();
			}
		});
	}

	public void hideNotify() {
		activity.post(new Runnable() {
			public void run() {
				view.clearFocus();
			}
		});
	}

}
