package javax.microedition.lcdui;

//#define __XAML__

//#ifdef __XAML__
//#else
import com.codename1.ui.Component;
import com.codename1.ui.Container;
import com.codename1.ui.Label;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.ui.layouts.FlowLayout;
//#endif

public abstract class Item {
    public static final int BUTTON = 2;
    public static final int HYPERLINK = 1;
    public static final int LAYOUT_2 = 16384;
    public static final int LAYOUT_BOTTOM = 32;
    public static final int LAYOUT_CENTER = 3;
    public static final int LAYOUT_DEFAULT = 0;
    public static final int LAYOUT_EXPAND = 2048;
    public static final int LAYOUT_LEFT = 1;
    public static final int LAYOUT_NEWLINE_AFTER = 512;
    public static final int LAYOUT_NEWLINE_BEFORE = 256;
    public static final int LAYOUT_RIGHT = 2;
    public static final int LAYOUT_SHRINK = 1024;
    public static final int LAYOUT_TOP = 16;
    public static final int LAYOUT_VCENTER = 48;
    public static final int LAYOUT_VEXPAND = 8192;
    public static final int LAYOUT_VSHRINK = 4096;
    public static final int PLAIN = 0;

    private String label;
    private Command defaultCommand;
    private ItemCommandListener itemCommandListener;

//#ifdef __XAML__
//#else
    private Component cn1Component;
//#endif

    protected Item(String label) {
//        this(label, BoxLayout.X_AXIS);
        this.label = label;
//#ifdef __XAML__
//#else
        if (label != null) {
            this.cn1Component = new Container();
            ((Container) this.cn1Component).setLayout(new FlowLayout(Component.LEFT));
            ((Container) this.cn1Component).addComponent(new Label(label));
        }
//#endif
    }

    protected Item(String label, int layout) {
        this.label = label;
//#ifdef __XAML__
//#else
        if (label != null) {
            this.cn1Component = new Container();
            ((Container) this.cn1Component).setLayout(new BoxLayout(layout));
            ((Container) this.cn1Component).addComponent(new Label(label));
        }
//#endif
    }

//#ifdef __XAML__

    public Command MIDP_getDefaultCommand() {
        return defaultCommand;
    }

    public ItemCommandListener MIDP_getItemCommandListener() {
        return itemCommandListener;
    }

//#else

    Component getComponent() {
        return cn1Component;
    }

    protected Component getContent() {
        if (cn1Component instanceof Container) {
            return ((Container) cn1Component).getComponentAt(1);
        }
        return cn1Component;  
    }

    protected void setContent(Component component) {
        if (cn1Component != null) {
            if (cn1Component instanceof Container) {
                ((Container) cn1Component).addComponent(component);
            } else {
                throw new IllegalStateException("no container");
            }
        } else {
            cn1Component = component;
        }
    }

//#endif

    public String getLabel() {
        return label;
    }

    public void setDefaultCommand(Command cmd) {
        com.codename1.io.Log.p("Item.setDefaultCommand not implemented properly", com.codename1.io.Log.ERROR);
//        throw new Error("Item.setDefaultCommand not implemented");
        this.defaultCommand = cmd;
    }

    public void setItemCommandListener(ItemCommandListener l) {
        com.codename1.io.Log.p("Item.setItemCommandListener not implemented properly", com.codename1.io.Log.ERROR);
//        throw new Error("Item.setItemCommandListener not implemented");
        this.itemCommandListener = l;
    }

    public void setLayout(int layout) {
        com.codename1.io.Log.p("Item.setLayout not implemented", com.codename1.io.Log.ERROR);
//        throw new Error("Item.setLayout not implemented");
    }
}
