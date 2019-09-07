package org.microemu.android.device;

import android.graphics.Bitmap;

public interface ImageAccessor
{
	Bitmap getBitmap();
  
	void setBitmap(Bitmap bitmap);
}
