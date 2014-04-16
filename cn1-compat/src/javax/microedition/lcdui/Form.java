package javax.microedition.lcdui;

//#define __XAML__

import java.util.Vector;

public class Form extends Screen {

    private Vector<Item> items;
    private ItemStateListener itemStateListener;

//#ifdef __XAML__
    private Peer peer;
//#else
    private com.codename1.ui.Form cn1Form;
//#endif

    public Form(String title) {
        super.setTitle(title);
        this.items = new Vector<Item>();
//#ifndef __XAML__
        this.cn1Form = new com.codename1.ui.Form(title);
        this.cn1Form.setLayout(new com.codename1.ui.layouts.BoxLayout(com.codename1.ui.layouts.BoxLayout.Y_AXIS));
        super.setTitle(title);
        super.setDisplayable(cn1Form);
//#endif
    }

//#ifdef __XAML__

    public void show() {
        com.codename1.ui.FriendlyAccess.execute("show-form", new Object[]{
            this
        });
    }

    public Vector<Item> MIDP_getItems() {
        return items;
    }

    public ItemStateListener MIDP_getItemStateListener() {
        return itemStateListener;
    }

    public void MIDP_setPeer(Peer peer) {
        this.peer = peer;
    }

//#endif

    public int append(Image img) {
        return append(new ImageItem(null, img, ImageItem.LAYOUT_DEFAULT, null));
    }

    public int append(Item item) {
        items.add(item);
//#ifdef __XAML__
        if (peer != null)
            peer.append(item);
//#else
        cn1Form.addComponent(item.getComponent());
        cn1Form.repaint();
//#endif
        return items.size() - 1;
    }

    public int append(String str) {
        return append(new StringItem(null, str));
    }

    public void delete(int itemNum) {
        items.remove(itemNum);
//#ifdef __XAML__
        if (peer != null)
            peer.delete(itemNum);
//#else
        com.codename1.io.Log.p("Form.delete not implemented", com.codename1.io.Log.ERROR);
        throw new Error("Form.delete not implemented");
//#endif
    }

    public void deleteAll() {
        items.clear();
//#ifdef __XAML__
        if (peer != null)
            peer.deleteAll();
//#else
        cn1Form.removeAll();
        cn1Form.repaint();
//#endif
    }

    public Item get(int itemNum) {
        return items.get(itemNum);
    }

    public void insert(int itemNum, Item item) {
        com.codename1.io.Log.p("Form.insert not implemented", com.codename1.io.Log.ERROR);
        throw new Error("Form.insert not implemented");
    }

    public int size() {
        return items.size();
    }

    public void setItemStateListener(ItemStateListener l) {
        this.itemStateListener = l;
    }

//#ifdef __XAML__

    public interface Peer {
        void append(Item item);
        void delete(int itemNum);
        void deleteAll();
        void insert(int itemNum, Item item);
    }

//#endif
    
}
