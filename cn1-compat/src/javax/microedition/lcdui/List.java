package javax.microedition.lcdui;

import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.list.DefaultListModel;
import com.codename1.ui.list.DefaultListCellRenderer;

public class List extends Screen implements Choice {
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);

    private Command selectCommand;

    private com.codename1.ui.List cn1List;
    private DefaultListModel cn1Model;

    public List(String title, int listType) {
        this(title, listType, null, null);
    }

    public List(String title, int listType, String[] stringElements, Image[] imageElements) {
        System.out.println("INFO List.ctor of type " + listType);
        if (stringElements != null) {
            this.cn1Model = new DefaultListModel(stringElements);
        } else {
            this.cn1Model = new DefaultListModel();
        }
        this.cn1List = new com.codename1.ui.List(this.cn1Model);
        this.cn1List.addActionListener(this);
        ((DefaultListCellRenderer) this.cn1List.getRenderer()).setShowNumbers(false);
        com.codename1.ui.Form cn1Form = new com.codename1.ui.Form(title);
        cn1Form.addComponent(this.cn1List);
        super.setDisplayable(cn1Form);
    }

    public void actionPerformed(ActionEvent actionEvent) {
        System.out.println("WARN List.actionPerformed; " + actionEvent.getComponent() + "; " + actionEvent.getCommand() + "; " + actionEvent.getSource());
        if (actionEvent.getCommand() == null) { // TODO or actionEvent.getSource() instanceof cn1List???
            System.out.println("WARN List.actionPerformed - SELECT");
            getListener().commandAction(selectCommand == null ? SELECT_COMMAND : selectCommand, this);
        } else {
            super.actionPerformed(actionEvent);
        }
    }

    public int append(String stringPart, Image imagePart) {
        cn1Model.addItem(stringPart);
        return cn1Model.getSize() - 1;
    }

    public void delete(int elementNum) {
        cn1Model.removeItem(elementNum);
    }

    public void deleteAll() {
        cn1Model.removeAll();
    }

    public int getSelectedFlags(boolean[] array) {
        throw new Error("List.getSelectedFlags not implemented");
    }

    public void setSelectedFlags(boolean[] array) {
        throw new Error("List.setSelectedFlags not implemented");
    }
    
    public int getSelectedIndex() {
        System.out.println("INFO List.getSelectIndex; " + cn1List.getSelectedIndex());
        return cn1List.getSelectedIndex();
    }

    public void setSelectedIndex(int elementNum, boolean selected) {
        if (selected) {
            cn1List.setSelectedIndex(elementNum, true); // true to scroll
        }
        // suppose we never call selSelectedIndex with false
    }
    
    public String getString(int elementNum) {
        System.out.println("INFO List.getString; " + elementNum);
        return cn1Model.getItemAt(elementNum).toString();
    }

    public void set(int elementNum, String stringPart, Image imagePart) {
        throw new Error("List.set not implemented");
    }

    public void setFitPolicy(int fitPolicy) {
        System.err.println("ERROR List.setFitPolicy not implemented");
    }

    public Font getFont(int elementNum) {
        throw new Error("List.getFont not implemented");
    }

    public void setFont(int elementNum, Font font) {
        System.err.println("ERROR List.setFont not implemented");
    }

    public void setSelectCommand(Command command) {
        selectCommand = command;
    }

    public int size() {
        return cn1Model.getSize();
    }
}
