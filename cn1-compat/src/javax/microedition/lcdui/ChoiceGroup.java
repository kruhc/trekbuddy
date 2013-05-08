package javax.microedition.lcdui;

import com.codename1.ui.Container;
import com.codename1.ui.ButtonGroup;
import com.codename1.ui.RadioButton;
import com.codename1.ui.CheckBox;
import com.codename1.ui.Component;
import com.codename1.ui.ComboBox;
import com.codename1.ui.list.DefaultListModel;
import com.codename1.ui.layouts.BoxLayout;

public class ChoiceGroup extends Item implements Choice {

    private int choiceType;
    private int count;

    private Component cn1Holder;
    private ButtonGroup cn1Group;

    public ChoiceGroup(String label, int choiceType) {
        this(label, choiceType, null, null);
    }

    public ChoiceGroup(String label, int choiceType, String[] stringElements, Image[] imageElements) {
        super(label, BoxLayout.Y_AXIS);
        this.choiceType = choiceType;
        if (choiceType == EXCLUSIVE) {
            this.cn1Group = new ButtonGroup();
            this.cn1Holder = new Container();
            asContainer().setLayout(new BoxLayout(BoxLayout.Y_AXIS));
            // TODO append stringElements; but not used by TB
        } else if (choiceType == POPUP) {
            this.cn1Holder = new ComboBox(stringElements == null ? new DefaultListModel() : new DefaultListModel(stringElements));
        } else if (choiceType == MULTIPLE) {
            this.cn1Holder = new Container();
            asContainer().setLayout(new BoxLayout(BoxLayout.Y_AXIS));
            // TODO append stringElements; but not used by TB
        } else {
            throw new IllegalArgumentException(Integer.toString(choiceType));
        }
        setContent(this.cn1Holder);
    }

    public int append(String stringPart, Image imagePart) {
        if (choiceType == EXCLUSIVE) {
            RadioButton radioButton = new RadioButton(stringPart);
            cn1Group.add(radioButton);
            asContainer().addComponent(radioButton);
        } else if (choiceType == POPUP) {
            asPopup().addItem(stringPart);
        } else { // MULTIPLE
            asContainer().addComponent(new CheckBox(stringPart));
        }
        return count++;
    }

    public void delete(int elementNum) {
        throw new Error("ChoiceGroup.delete not implemented");
    }

    public void deleteAll() {
        throw new Error("ChoiceGroup.deleteAll not implemented");
    }

    public int getSelectedFlags(boolean[] array) {
        if (choiceType == MULTIPLE) {
            int count = 0;
            for (int N = array.length, i = 0; i < N; i++) {
                array[i] = ((CheckBox) asContainer().getComponentAt(i)).isSelected();
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
                ((CheckBox) asContainer().getComponentAt(i)).setSelected(array[i]);
            }
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public boolean isSelected(int elementNum) {
        if (choiceType == MULTIPLE) {
            return ((CheckBox) asContainer().getComponentAt(elementNum)).isSelected();
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public int getSelectedIndex() {
        if (choiceType == EXCLUSIVE) {
            return cn1Group.getSelectedIndex();
        } else if (choiceType == POPUP) {
            return asPopup().getSelectedIndex();
        } else {
            throw new IllegalStateException("illegal Choice type");
        }
    }

    public void setSelectedIndex(int elementNum, boolean selected) {
        if (selected) {
            if (choiceType == EXCLUSIVE) {
                cn1Group.setSelected(elementNum);
            } else if (choiceType == POPUP) {
                asPopup().setSelectedIndex(elementNum, true); // true to scroll
            } else {
                ((CheckBox) asContainer().getComponentAt(elementNum)).setSelected(selected);
            }
        }
        // suppose we never call selSelectedIndex with false
    }

    public String getString(int elementNum) {
        if (choiceType == EXCLUSIVE) {
            return cn1Group.getRadioButton(cn1Group.getSelectedIndex()).getText();
        } else if (choiceType == POPUP) {
            return asPopup().getModel().getItemAt(elementNum).toString();
        } else {
            return ((CheckBox) asContainer().getComponentAt(elementNum)).getText();
        }
    }

    public void set(int elementNum, String stringPart, Image imagePart) {
        throw new Error("ChoiceGroup.set not implemented");
    }

    public void setFitPolicy(int fitPolicy) {
        System.err.println("ERROR setFitPolicy not implemented");
//        throw new Error("not implemented");
    }

    public Font getFont(int elementNum) {
        throw new Error("ChoiceGroup.getFont not implemented");
    }

    public void setFont(int elementNum, Font font) {
        System.err.println("ERROR setFont not implemented");
//        throw new Error("not implemented");
    }

    public int size() {
        return count;
    }

    private Container asContainer() {
        return (Container) cn1Holder;
    }
    private ComboBox asPopup() {
        return (ComboBox) cn1Holder;
    }
}
