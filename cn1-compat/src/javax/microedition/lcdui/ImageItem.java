package javax.microedition.lcdui;

import com.codename1.ui.Label;

public class ImageItem extends Item {

    public ImageItem(String label, Image img, int layout, String altText) {
        this(label, img, layout, altText, PLAIN);
    }

    public ImageItem(String label, Image img, int layout, String altText, int appearanceMode) {
        super(label);
        setContent(new Label(img.getImage()));
    }

    public void setImage(Image img) {
        throw new Error("ImageItem.setImage not implemented");
    }
}
