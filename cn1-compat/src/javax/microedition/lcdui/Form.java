package javax.microedition.lcdui;

import com.codename1.ui.layouts.BoxLayout;

import java.util.Vector;

public class Form extends Screen {

    private Vector<Item> items;

    private com.codename1.ui.Form cn1Form;

    public Form(String title) {
        this.cn1Form = new com.codename1.ui.Form(title);
        this.cn1Form.setLayout(new BoxLayout(BoxLayout.Y_AXIS));
        this.items = new Vector<Item>();
        super.setTitle(title);
        super.setDisplayable(cn1Form);
    }

    public int append(Image img) {
        return append(new ImageItem(null, img, ImageItem.LAYOUT_DEFAULT, null));
    }

    public int append(Item item) {
        items.add(item);
        cn1Form.addComponent(item.getComponent());
        cn1Form.repaint();
        return items.size() - 1;
    }

    public int append(String str) {
        return append(new StringItem(null, str));
    }

    public void delete(int itemNum) {
        throw new Error("Form.delete not implemented");
    }

    public void deleteAll() {
        items.clear();
        cn1Form.removeAll();
        cn1Form.repaint();
    }

    public Item get(int itemNum) {
        return (Item) items.get(itemNum);
    }

    public void insert(int itemNum, Item item) {
        throw new Error("Form.insert not implemented");
    }

    public void setItemStateListener(ItemStateListener iListener) {
        System.err.println("ERROR Form.setItemStateListener not implemented");
//        throw new Error("Form.setItemStateListener not implemented");
    }

    public int size() {
        return items.size();
    }
}
