package javax.microedition.lcdui;

//#define __XAML__

//#ifdef __XAML__
//#else
import com.codename1.ui.Container;
import com.codename1.ui.ButtonGroup;
import com.codename1.ui.RadioButton;
import com.codename1.ui.CheckBox;
import com.codename1.ui.Component;
import com.codename1.ui.ComboBox;
import com.codename1.ui.list.DefaultListModel;
import com.codename1.ui.layouts.BoxLayout;
//#endif

import java.util.Vector;

public class ChoiceGroup extends Item implements Choice {

    private int choiceType;
    private int count;

//#ifdef __XAML__
    private Vector<String> stringParts;
    private Vector<Boolean> selectedFlags;
    private int selectedIndex = -1;
//#else
    private Component cn1Holder;
    private ButtonGroup cn1Group;
//#endif

    public ChoiceGroup(String label, int choiceType) {
        this(label, choiceType, null, null);
    }

    public ChoiceGroup(String label, int choiceType, String[] stringElements, Image[] imageElements) {
//#ifdef __XAML__
        super(label, Item.LAYOUT_DEFAULT);
//#else
        super(label, com.codename1.ui.layouts.BoxLayout.Y_AXIS);
//#endif
        this.choiceType = choiceType;
//#ifdef __XAML__
        final int size = stringElements == null ? 0 : stringElements.length;
        this.stringParts = arrayToList(stringElements, size);
        this.selectedFlags = new Vector<Boolean>(size);
//#else
        if (choiceType == EXCLUSIVE) {
            this.cn1Group = new ButtonGroup();
            this.cn1Holder = new Container();
            asContainer().setLayout(new BoxLayout(BoxLayout.Y_AXIS));
            // TODO append stringElements; but not used by TB
        } else if (choiceType == POPUP) {
            this.cn1Holder = new ComboBox(stringElements);
        } else if (choiceType == MULTIPLE) {
            this.cn1Holder = new Container();
            asContainer().setLayout(new BoxLayout(BoxLayout.Y_AXIS));
            // TODO append stringElements; but not used by TB
        } else {
            throw new IllegalArgumentException(Integer.toString(choiceType));
        }
        setContent(this.cn1Holder);
//#endif
    }

//#ifdef __XAML__

    public int MIDP_getChoiceType() {
        return choiceType;
    }

    public java.util.List<String> MIDP_getStringParts() {
        return stringParts;
    }

    public java.util.List<Boolean> MIDP_getSelectedFlags() {
        return selectedFlags;
    }

//#endif

    public int append(String stringPart, Image imagePart) {
//#ifdef __XAML__
        stringParts.add(stringPart);
        selectedFlags.add(Boolean.FALSE);
//#else
        if (choiceType == EXCLUSIVE) {
            RadioButton radioButton = new RadioButton(stringPart);
            cn1Group.add(radioButton);
            asContainer().addComponent(radioButton);
        } else if (choiceType == POPUP) {
            asPopup().addItem(stringPart);
        } else { // MULTIPLE
            asContainer().addComponent(new CheckBox(stringPart));
        }
//#endif
        return count++;
    }

    public void delete(int elementNum) {
        com.codename1.io.Log.p("ChoiceGroup.delete not implemented", com.codename1.io.Log.ERROR);
        throw new Error("ChoiceGroup.delete not implemented");
    }

    public void deleteAll() {
        com.codename1.io.Log.p("ChoiceGroup.deleteAll not implemented", com.codename1.io.Log.ERROR);
        throw new Error("ChoiceGroup.deleteAll not implemented");
    }

    public int getSelectedFlags(boolean[] array) {
        if (choiceType == MULTIPLE) {
            int count = 0;
            for (int N = array.length, i = 0; i < N; i++) {
//#ifdef __XAML__
                array[i] = selectedFlags.get(i);
//#else
                array[i] = ((CheckBox) asContainer().getComponentAt(i)).isSelected();
//#endif
                if (array[i]) {
                    count++;
                }
            }
            return count;
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public void setSelectedFlags(boolean[] array) {
        if (choiceType == MULTIPLE) {
            for (int N = array.length, i = 0; i < N; i++) {
//#ifdef __XAML__
                selectedFlags.set(i, array[i]);
//#else
                ((CheckBox) asContainer().getComponentAt(i)).setSelected(array[i]);
//#endif
            }
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public boolean isSelected(int elementNum) {
        if (choiceType == MULTIPLE) {
//#ifdef __XAML__
            return selectedFlags.get(elementNum);
//#else
            return ((CheckBox) asContainer().getComponentAt(elementNum)).isSelected();
//#endif
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public int getSelectedIndex() {
        if (choiceType == EXCLUSIVE) {
//#ifdef __XAML__
            return selectedIndex;
//#else
            return cn1Group.getSelectedIndex();
//#endif
        } else if (choiceType == POPUP) {
//#ifdef __XAML__
            return selectedIndex;
//#else
            return asPopup().getSelectedIndex();
//#endif
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public void setSelectedIndex(int elementNum, boolean selected) {
        if (selected) {
//#ifdef __XAML__
            this.selectedIndex = elementNum;
            this.selectedFlags.set(elementNum, true);
//#else
            if (choiceType == EXCLUSIVE) {
                cn1Group.setSelected(elementNum);
            } else if (choiceType == POPUP) {
                asPopup().setSelectedIndex(elementNum, true); // true to scroll
            } else {
                ((CheckBox) asContainer().getComponentAt(elementNum)).setSelected(selected);
            }
//#endif
        } else {
//#ifdef __XAML__
            this.selectedFlags.set(elementNum, false);            
//#endif
        }
    }

    public String getString(int elementNum) {
//#ifdef __XAML__
        return stringParts.get(elementNum);
//#else
        if (choiceType == EXCLUSIVE) {
            return cn1Group.getRadioButton(cn1Group.getSelectedIndex()).getText();
        } else if (choiceType == POPUP) {
            return asPopup().getModel().getItemAt(elementNum).toString();
        } else {
            return ((CheckBox) asContainer().getComponentAt(elementNum)).getText();
        }
//#endif
    }

    public void set(int elementNum, String stringPart, Image imagePart) {
        com.codename1.io.Log.p("ChoiceGroup.set not implemented", com.codename1.io.Log.ERROR);
        throw new Error("ChoiceGroup.set not implemented");
    }

    public void setFitPolicy(int fitPolicy) {
        com.codename1.io.Log.p("setFitPolicy not implemented", com.codename1.io.Log.WARNING);
//        throw new Error("not implemented");
    }

    public Font getFont(int elementNum) {
        com.codename1.io.Log.p("ChoiceGroup.getFont not implemented", com.codename1.io.Log.ERROR);
        throw new Error("ChoiceGroup.getFont not implemented");
    }

    public void setFont(int elementNum, Font font) {
        com.codename1.io.Log.p("ChoiceGroup.setFont not implemented", com.codename1.io.Log.WARNING);
//        throw new Error("not implemented");
    }

    public int size() {
        return count;
    }

//#ifndef __XAML__

    private Container asContainer() {
        return (Container) cn1Holder;
    }

    private ComboBox asPopup() {
        return (ComboBox) cn1Holder;
    }

//#else

    private static <T> Vector<T> arrayToList(final T[] array, final int size) {
        final Vector<T> result = new Vector<T>(size);
        if (array != null) {
            for (T item : array) {
                result.add(item);
            }
        }
        return result;
    }

//#endif

}
