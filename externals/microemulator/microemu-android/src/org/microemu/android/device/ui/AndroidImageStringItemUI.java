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
 *  @version $Id: AndroidImageStringItemUI.java 1931 2009-02-05 21:00:52Z barteo $
 */

package org.microemu.android.device.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.StringItem;

import org.microemu.MIDletBridge;
import org.microemu.android.MicroEmulatorActivity;
import org.microemu.android.device.AndroidFont;
import org.microemu.android.device.AndroidFontManager;
import org.microemu.android.device.AndroidImmutableImage;
import org.microemu.android.device.AndroidMutableImage;
import org.microemu.device.ui.ImageStringItemUI;

import android.text.util.Linkify;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.view.View;

public class AndroidImageStringItemUI extends LinearLayout implements ImageStringItemUI {

	private MicroEmulatorActivity activity;
	
	private TextView labelView;
	
	private ImageView imageView;

	private TextView textView;
	
	private Command defaultCommand;
	
	public AndroidImageStringItemUI(final MicroEmulatorActivity activity, final Item item) {
		super(activity);
		
		this.activity = activity;

		setOrientation(LinearLayout.VERTICAL);
	//	setFocusable(false);
	//	setFocusableInTouchMode(false);

		labelView = new TextView(activity);
	//	labelView.setFocusable(false);
	//	labelView.setFocusableInTouchMode(false);
		labelView.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		labelView.setTextAppearance(labelView.getContext(),
				android.R.style.TextAppearance_Medium);
  //  labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
		labelView.setVisibility(GONE);
		addView(labelView);

		if (item instanceof StringItem) {
			final StringItem stringitem = (StringItem) item;
			if (stringitem.getAppearanceMode() == Item.BUTTON || stringitem.getAppearanceMode() == Item.HYPERLINK) {
				if (stringitem.getAppearanceMode() == Item.BUTTON) { 
					setOrientation(LinearLayout.HORIZONTAL);
					textView = new Button(activity);
				} else {
					textView = new TextView(activity);
				}
				textView.setClickable(true);
				textView.setOnClickListener(new View.OnClickListener() {

					public void onClick(View v) {
						if (defaultCommand != null) {
							MIDletBridge.getMIDletAccess().getDisplayAccess().commandAction(defaultCommand, item);
						}
					}

				});
			} else {
				textView = new TextView(activity);
			}
			textView.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));
			textView.setPadding(10, textView.getPaddingTop(), textView.getPaddingRight(), textView.getPaddingBottom());
			addView(textView);
		} else if (item instanceof ImageItem) {
			final ImageItem imageitem = (ImageItem) item;
			if (imageitem.getAppearanceMode() == Item.BUTTON || imageitem.getAppearanceMode() == Item.HYPERLINK) {
				imageView = new ImageButton(activity);
				imageView.setClickable(true);
				imageView.setOnClickListener(new View.OnClickListener() {

					public void onClick(View v) {
						if (defaultCommand != null) {
							MIDletBridge.getMIDletAccess().getDisplayAccess().commandAction(defaultCommand, item);
						}
					}

				});
			} else {
				imageView = new ImageView(activity);
			}
			imageView.setVisibility(GONE);
			imageView.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));
			imageView.setPadding(10, imageView.getPaddingTop(), imageView.getPaddingRight(), imageView.getPaddingBottom());
			addView(imageView);
		}

		setLabel(item.getLabel());
	}
	
	public void setDefaultCommand(Command cmd) {
		this.defaultCommand = cmd;
	}

	public void setLabel(final String label) {
		activity.post(new Runnable() {
			public void run() {
				if (label == null) {
					labelView.setVisibility(GONE);
				} else {
					labelView.setVisibility(VISIBLE);
					labelView.setText(label);
				}
			}
		});
	}

	public void setImage(final Image image) {
		activity.post(new Runnable() {
			public void run() {
				if (image == null) {
					imageView.setVisibility(GONE);
					imageView.setImageBitmap(null);
				} else {
					imageView.setVisibility(VISIBLE);
					if (image.isMutable()) {
						imageView.setImageBitmap(((AndroidMutableImage) image).getBitmap());
					} else {
						imageView.setImageBitmap(((AndroidImmutableImage) image).getBitmap());
					}
				}
			}
		});
	}

	public void setText(final String text) {
		activity.post(new Runnable() {
			public void run() {
				textView.setText(text);
				Linkify.addLinks(textView, Linkify.WEB_URLS);
			}
		});
	}

	public void setFont(final Font font) {
		activity.post(new Runnable() {
			public void run() {
				AndroidFont androidFont = AndroidFontManager.getFont(font);
				textView.setTypeface(androidFont.paint.getTypeface(), androidFont.paint.getStyle().ordinal());
				textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, androidFont.paint.getTextSize());
			}
		});
	}

}
