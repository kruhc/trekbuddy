/**
 *  MicroEmulator
 *  Copyright (C) 2009 Bartek Teodorczyk <barteo@barteo.net>
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
 *  @version $Id: AndroidListUI.java 2498 2011-05-09 13:47:27Z barteo@gmail.com $
 */

package org.microemu.android.device.ui;

import java.util.ArrayList;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;

import org.microemu.MIDletBridge;
import org.microemu.android.MicroEmulatorActivity;
import org.microemu.android.device.AndroidFont;
import org.microemu.android.device.AndroidFontManager;
import org.microemu.android.device.AndroidImmutableImage;
import org.microemu.android.device.AndroidMutableImage;
import org.microemu.android.util.WaitableRunnable;
import org.microemu.device.ui.ListUI;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ImageView;

public class AndroidListUI extends AndroidDisplayableUI implements ListUI {

	private Command selectCommand;
	
	private AndroidListAdapter listAdapter;
	
	private AndroidListView listView;

	private int selectedPosition;
	
	public AndroidListUI(final MicroEmulatorActivity activity, List list) {
		super(activity, list, true);
		
		this.selectCommand = List.SELECT_COMMAND;
		this.selectedPosition = AdapterView.INVALID_POSITION;
		this.listAdapter = new AndroidListAdapter();
			
		activity.post(new Runnable() {
			public void run() {
				AndroidListView listView = new AndroidListView(activity);
				listView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
				listView.setAdapter(listAdapter);
				((LinearLayout) AndroidListUI.this.view).addView(listView);		
				AndroidListUI.this.listView = listView;

				invalidate();
			}
		});		
	}
	
	//
	// ListUI
	//

	public int append(final String stringPart, final Image imagePart) {
		final WaitableRunnable<Integer> wr = new WaitableRunnable<Integer>(new WaitableRunnable.Getter<Integer>() {
			public Integer execute() {
				return listAdapter.append(stringPart, imagePart);
			}
		}, AndroidListUI.this);
    
		activity.post(wr);
		final int index = wr.getValue();
  
		return index;
	}

	public int appendAll(final String[] stringParts, final Image[] imageParts) {
		final WaitableRunnable<Integer> wr = new WaitableRunnable<Integer>(new WaitableRunnable.Getter<Integer>() {
			public Integer execute() {
				return listAdapter.appendAll(stringParts, imageParts);
			}
		}, AndroidListUI.this);

		activity.post(wr);
		final int unknown = wr.getValue();
  
		return unknown;
	}

	public int getSelectedIndex() {
		final WaitableRunnable<Integer> wr = new WaitableRunnable<Integer>(new WaitableRunnable.Getter<Integer>() {
			public Integer execute() {
	        		int index = selectedPosition;
	        		if (index == AdapterView.INVALID_POSITION) {
	        			index = listView.getSelectedItemPosition();
			        	if (index == AdapterView.INVALID_POSITION) {
	        				index = -1;
	        			}
				}
				return index;
			}
		}, AndroidListUI.this);

		activity.post(wr);
		final int index = wr.getValue();
  
		return index;
	}

	public String getString(final int elementNum) {
		final WaitableRunnable<String> wr = new WaitableRunnable<String>(new WaitableRunnable.Getter<String>() {
			public String execute() {
				return listAdapter.getItem(elementNum).text;
			}
		}, AndroidListUI.this);

		activity.post(wr);
		final String text = wr.getValue();
  
		return text;
	}

	public void setSelectCommand(Command command) {
		this.selectCommand = command;		
	}
	
	public void delete(final int elementNum) {
		activity.post(new Runnable() {
			public void run() {
				/*synchronized (AndroidListUI.this)*/ {
					try {
						listAdapter.delete(elementNum);
					} catch (IndexOutOfBoundsException e) {
						// TODO
					}
				}
			}
		});
	}

	public void deleteAll() {
		activity.post(new Runnable() {
			public void run() {
				/*synchronized (AndroidListUI.this)*/ {
					listAdapter.deleteAll();
				}
			}
		});
	}

	public void setSelectedIndex(final int elementNum, final boolean selected) {
		if (selected) { // TODO if not???
			activity.post(new Runnable() {
				public void run() {
					listView.setSelection(selectedPosition = elementNum);
				}
			});
		}
	}
	
	public void insert(final int elementNum, final String stringPart, final Image imagePart) {
		activity.post(new Runnable() {
			public void run() {
				/*synchronized (AndroidListUI.this)*/ {
					listAdapter.insert(elementNum, stringPart, imagePart);
				}
			}
		});
	}

	public void set(final int elementNum, final String stringPart, final Image imagePart) {
		activity.post(new Runnable() {
			public void run() {
				/*synchronized (AndroidListUI.this)*/ {
					listAdapter.set(elementNum, stringPart, imagePart);
				}
			}
		});
	}

	public void setFont(final int itemNum, final Font font) {
		activity.post(new Runnable() {
			public void run() {
				/*synchronized (AndroidListUI.this)*/ {
					final View child = listView.getChildAt(itemNum);
					if (child instanceof TextView) { // TODO child is LinearLayout, see getView	
						AndroidFont androidFont = AndroidFontManager.getFont(font);
						((TextView) child).setTypeface(androidFont.paint.getTypeface(),
										androidFont.paint.getStyle().ordinal());
					}
				}
			}
		});
	}
	
	public int size() {
		final WaitableRunnable<Integer> wr = new WaitableRunnable<Integer>(new WaitableRunnable.Getter<Integer>() {
			public Integer execute() {
				return listAdapter.getCount();
			}
		}, AndroidListUI.this);

		activity.post(wr);
		final int size = wr.getValue();
    
		return size;
	}

	private static class ViewHolder	{
		Image image;
		String text;

		ViewHolder() {
		}

		ViewHolder(String text, Image image) {
			this.image = image;
			this.text = text;
		}      
	}

	private class AndroidListAdapter extends BaseAdapter {

		private ArrayList<ViewHolder> objects = new ArrayList<ViewHolder>(16);

		public int append(final String stringPart, final Image imagePart) {
			final ViewHolder vh = new ViewHolder(stringPart, imagePart);
			objects.add(vh);
			
			notifyChanged();			
			
			return objects.lastIndexOf(vh);
		}

		public int appendAll(final String[] stringParts, final Image[] imageParts) {
			objects.ensureCapacity(stringParts.length);
			for (int i = 0, N = stringParts.length; i < N; i++) {
				final ViewHolder vh = new ViewHolder();
	 			if (imageParts != null) {
					vh.image = imageParts[i];
				}
				vh.text = stringParts[i];
				objects.add(vh);
			}

			notifyChanged();			

			return -1; 
		}
    		
		public void insert(final int elementNum, final String stringPart, final Image imagePart) {
			final ViewHolder vh = new ViewHolder(stringPart, imagePart);
			objects.add(elementNum, vh);
			
			notifyChanged();			
		}

		public void set(final int elementNum, final String stringPart, final Image imagePart) {
			final ViewHolder vh = getItem(elementNum);
			vh.image = imagePart;
			vh.text = stringPart;

			notifyChanged();			
		}

		public int getCount() {
			return objects.size();
		}

		public ViewHolder getItem(int position) {
			return objects.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				//TODO figure out a better layout - see example of different image and text sizes.
				LinearLayout layout = new LinearLayout(activity);
				layout.setGravity(Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
				android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
				activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
				layout.setMinimumHeight(Math.round(dm.ydpi * (0.5f / 2.54F))); 

				ImageView iv = new ImageView(activity);
				TextView tv = new TextView(activity);

				layout.addView(iv);
				layout.addView(tv);

				convertView = layout;

				tv.setTextAppearance(convertView.getContext(), android.R.style.TextAppearance_Large);
				tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
				if (((List) displayable).getFitPolicy() == List.TEXT_WRAP_OFF) {
					tv.setSingleLine();
				}

				iv.setPadding(iv.getPaddingLeft(), iv.getPaddingTop(), 2, iv.getPaddingBottom());
			}

			final LinearLayout ll = (LinearLayout)convertView;
			final ViewHolder vh = (ViewHolder) getItem(position);

			TextView tv = (TextView) ll.getChildAt(1);
			tv.setText(vh.text);
			
			if (vh.image != null) {
				ImageView iv = (ImageView) ll.getChildAt(0);
				Image img = (Image) vh.image;
				if (img.isMutable())
					iv.setImageBitmap(((AndroidMutableImage) img).getBitmap());
				else
					iv.setImageBitmap(((AndroidImmutableImage) img).getBitmap());
			} else {
				ImageView iv = (ImageView) ll.getChildAt(0);
				iv.setImageBitmap(null); 
			}                         

			return convertView;
		}
		
		public void delete(int elementNum) {
			objects.remove(elementNum);		

			notifyChanged();			
		}

		public void deleteAll() {
			objects.clear();
			
			notifyChanged();
		}

		private void notifyChanged() {
			activity.post(new Runnable() {
				public void run() {
					notifyDataSetChanged();
				}
			});
		}
	}
	
	private class AndroidListView extends ListView
			implements AdapterView.OnItemClickListener, AdapterView.OnItemSelectedListener  {

		public AndroidListView(Context context) {
			super(context);
			super.setClickable(true);
			super.setOnItemClickListener(this);
			super.setOnItemSelectedListener(this);
			setChoiceMode(CHOICE_MODE_SINGLE);
		}

		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
				if (getCommandListener() != null) {
					MIDletBridge.getMIDletAccess().getDisplayAccess().commandAction(selectCommand, displayable);
					return true;
				} else {				
					return false;
				}
			} else {
				return super.onKeyDown(keyCode, event);
			}
		}

		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			selectedPosition = position;
			MIDletBridge.getMIDletAccess().getDisplayAccess().commandAction(selectCommand, displayable);
		}

		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			selectedPosition = position;
		}

		public void onNothingSelected(AdapterView<?> parent) {
			selectedPosition = AdapterView.INVALID_POSITION;
		}
	}
	
}
