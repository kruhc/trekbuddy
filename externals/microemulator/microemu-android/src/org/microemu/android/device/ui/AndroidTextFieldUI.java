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
 *  @version $Id: AndroidTextFieldUI.java 1931 2009-02-05 21:00:52Z barteo $
 */

package org.microemu.android.device.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.TextField;

import org.microemu.android.MicroEmulatorActivity;
import org.microemu.android.util.WaitableRunnable;
import org.microemu.device.InputMethod;
import org.microemu.device.ui.TextFieldUI;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AndroidTextFieldUI extends LinearLayout implements TextFieldUI {
	
	private MicroEmulatorActivity activity;
	
	private TextView labelView; 
	
	private EditText editView;
  
	private TextField textField;
	
	public AndroidTextFieldUI(final MicroEmulatorActivity activity, final TextField textField) {
		super(activity);
    
		this.activity = activity;

		this.textField = textField;
		
		activity.post(new Runnable() {
			public void run() {
				setOrientation(LinearLayout.VERTICAL);
//				setFocusable(false);
//				setFocusableInTouchMode(false);
//				setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
				
				labelView = new TextView(activity);
//				labelView.setFocusable(false);
//				labelView.setFocusableInTouchMode(false);
				labelView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
				labelView.setTextAppearance(labelView.getContext(), android.R.style.TextAppearance_Medium);
				addView(labelView);
				
				editView = new EditText(activity) {
					protected void onDraw(android.graphics.Canvas canvas) {
						super.onDraw(canvas);
						if (activity.config.ignoreTextFieldFocus) {
							return;
						}
						if (AndroidTextFieldUI.this.textField != null) {
							if (AndroidTextFieldUI.this.textField.hasFocus()) {
								postDelayed(new Runnable() {
									public void run() {
										final long uptime = android.os.SystemClock.uptimeMillis();
										dispatchTouchEvent(android.view.MotionEvent.obtain(uptime, uptime, android.view.MotionEvent.ACTION_DOWN , 0, 0, 0));
										dispatchTouchEvent(android.view.MotionEvent.obtain(uptime, uptime, android.view.MotionEvent.ACTION_UP , 0, 0, 0));
										setSelection(length());
									}
								}, 50);
							}
							AndroidTextFieldUI.this.textField = null; // to have this called just first time
						}	
					}
				};
				editView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
				editView.setLineSpacing(0F, 1F);
				editView.addTextChangedListener(new TextWatcher() {

					private String previousText;
					
					public void afterTextChanged(Editable s) {
					}

					public void beforeTextChanged(CharSequence s, int start, int count, int after) {
						previousText = s.toString();
					}

					public void onTextChanged(CharSequence s, int start, int before, int count) {
						if (s.toString().length() <= textField.getMaxSize()
								&& InputMethod.validate(s.toString(), textField.getConstraints())) {
							if (textField != null) {
								AndroidFormUI.AndroidListView formList = (AndroidFormUI.AndroidListView) getParent();
								if (formList != null) {
									ItemStateListener listener = formList.getUI().getItemStateListener();
									if (listener != null) {
										listener.itemStateChanged(textField);
									}
								}
							}
						} else {
							editView.setText(previousText);
							editView.setSelection( start );
						}
					}

				});
				addView(editView);
				
				setLabel(textField.getLabel());
			}
		});
	}

	public void setDefaultCommand(Command cmd) {
	}

	public void setLabel(final String label) {
		activity.post(new Runnable() {
			public void run() {
				labelView.setText(label);
			}
		});
	}

	public void setConstraints(final int constraints) {
		activity.post(new Runnable() {
			public void run() {
          if ((constraints & TextField.CONSTRAINT_MASK) == TextField.URL) {
              editView.setSingleLine(true);
          } else if ((constraints & TextField.CONSTRAINT_MASK) == TextField.NUMERIC) {
              editView.setSingleLine(true);
              editView.setInputType(InputType.TYPE_CLASS_NUMBER);
          } else if ((constraints & TextField.CONSTRAINT_MASK) == TextField.DECIMAL) {
              editView.setSingleLine(true);
              editView.setInputType(
                      InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
          } else if ((constraints & TextField.CONSTRAINT_MASK) == TextField.PHONENUMBER) {
              editView.setSingleLine(true);
              editView.setInputType(InputType.TYPE_CLASS_PHONE);
          }
          if ((constraints & TextField.PASSWORD) != 0) {
              editView.setTransformationMethod(PasswordTransformationMethod.getInstance());
              editView.setTypeface(Typeface.MONOSPACE);
          }
          if ((constraints & TextField.UNEDITABLE) != 0) {
              editView.setEnabled(false);
          }
			}
		});
	}

	public void setString(final String text) {
		activity.post(new Runnable() {
			public void run() {
				editView.setText(text);
			}
		});
	}

	public String getString() {
		final WaitableRunnable<String> wr = new WaitableRunnable<String>(new WaitableRunnable.Getter<String>() {
			public String execute() {
				return editView.getText().toString();
			}
		}, AndroidTextFieldUI.this);

		activity.post(wr);
		final String text = wr.getValue();
    
		return text;
	}

}
