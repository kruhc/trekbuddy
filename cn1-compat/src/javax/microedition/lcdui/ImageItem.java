package javax.microedition.lcdui;

//#define __XAML__

public class ImageItem extends Item {

//#ifdef __XAML__
    private Image image;
    private int appearanceMode;
    private Peer peer;
//#endif

    public ImageItem(String label, Image img, int layout, String altText) {
        this(label, img, layout, altText, PLAIN);
    }

    public ImageItem(String label, Image img, int layout, String altText, int appearanceMode) {
        super(label);
//#ifdef __XAML__
        this.image = img;
        this.appearanceMode = appearanceMode;
//#else
        setContent(new com.codename1.ui.Label(img.getNativeImage()));
//#endif
    }

//#ifdef __XAML__

    public Image MIDP_getImage() {
        return image;
    }

    public int MIDP_getAppearanceMode() {
        return appearanceMode;
    }

    public void MIDP_setPeer(Peer peer) {
        this.peer = peer;
    }
    
//#endif

    public void setImage(Image img) {
        this.image = img;
//#ifdef __XAML__
        if (peer != null)
            peer.setImage(img);
//#endif
    }

//#ifdef __XAML__

    public interface Peer {
        void setImage(Image img);
    }

//#endif
    
}
