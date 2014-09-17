using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;

using org.xmlvm;

namespace TrackingApp
{
    public partial class Form : PhoneApplicationPage, javax.microedition.lcdui.Form_2Peer
    {
        const string KEY_MIDP_INSTANCE = "MIDP_instance";
        const string KEY_MIDP_GROUPINDEX = "MIDP_group_index";

        const string JUREK_FAKE_TECHNICAL_LABEL = "...";

        private javax.microedition.lcdui.Form MIDP_form;

        public Form()
        {
            InitializeComponent();
            MIDP_Init();
        }

        private void MIDP_Init()
        {
            System.Object[] args = PhoneApplicationService.Current.State["MIDP.Args"] as System.Object[];
            MIDP_form = (javax.microedition.lcdui.Form)args[0];
            MIDP_form.MIDP_1setPeer(this);
            title.Text = ((java.lang.String)MIDP_form.getTitle()).toCSharp();
            AddItems((java.util.List)MIDP_form.MIDP_1getItems());
            AddCommands((java.util.List)MIDP_form.MIDP_1getCommands());
        }

        internal bool IsStateListener
        {
            get
            {
                return MIDP_form.MIDP_1getItemStateListener() != null;
            }
        }

        internal javax.microedition.lcdui.ItemStateListener ItemStateListener
        {
            get
            {
                return MIDP_form.MIDP_1getItemStateListener() as javax.microedition.lcdui.ItemStateListener;
            }
        }

        #region Peer contract

        public void append(global::javax.microedition.lcdui.Item n1)
        {
            UserControl uc = ToUserControl(n1);
            if (uc != null)
            {
                content.Children.Add(uc);
            }
            else
            {
                System.Diagnostics.Debug.WriteLine("Form unsupported item: {0}", n1);
            }
        }

        public void delete(int n1)
        {
            content.Children.RemoveAt(n1);
        }

        public void deleteAll()
        {
            content.Children.Clear();
        }

        public void insert(int n1, global::javax.microedition.lcdui.Item n2)
        {
            UserControl uc = ToUserControl(n2);
            if (uc != null)
            {
                content.Children[n1] = uc;
            }
            else
            {
                System.Diagnostics.Debug.WriteLine("Form unsupported item: {0}", n1);
            }
        }

        #endregion

        private void AddItems(java.util.List items)
        {
            for (int N = items.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Item MIDP_item = (javax.microedition.lcdui.Item)items.get(i);
                append(MIDP_item);
            }
        }

        private void AddCommands(java.util.List cmds)
        {
            int active = 0;
            for (int N = cmds.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                if (cmd.getCommandType() == javax.microedition.lcdui.Command._fBACK || cmd.getCommandType() == javax.microedition.lcdui.Command._fCANCEL)
                    continue;
                /*if (active < 4) // 4 is limit for buttons
                {
                    ApplicationBarIconButton btn = new ApplicationBarIconButton();
                    btn.IconUri = new Uri("/Assets/basecircle.png", UriKind.Relative);
                    btn.Text = ((java.lang.String)cmd.getLabel()).toCSharp();
                    ApplicationBar.Buttons.Add(btn);
                }
                else*/
                {
                    string label = ((java.lang.String)cmd.getLabel()).toCSharp();
                    if (string.Empty.Equals(label)) // Jurek's PL localization :-(
                        label = JUREK_FAKE_TECHNICAL_LABEL;
                    ApplicationBarMenuItem item = new ApplicationBarMenuItem(label);
                    item.Click += item_Click;
                    ApplicationBar.MenuItems.Add(item);
                }
                active++;
            }
            if (active > 0)
            {
                ApplicationBar.IsVisible = true;
                //ApplicationBar.Mode = ApplicationBarMode.Minimized;
            }
        }

        private Uri uri;

        protected override void OnNavigatingFrom(NavigatingCancelEventArgs e)
        {
#if LOG
            CN1Extensions.Log("Form.OnNavigatingFrom {0}", uri);
#endif
            ApplicationBar.IsVisible = false;
            base.OnNavigatingFrom(e);
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
#if LOG
            CN1Extensions.Log("Form.OnNavigatedFrom {0}", uri);
#endif
            base.OnNavigatedFrom(e);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
#if LOG
            CN1Extensions.Log("Form.OnNavigatedTo {0} {1}", uri = NavigationService.CurrentSource, e.NavigationMode);
#endif
            PhoneApplicationService.Current.State.Remove("MIDP.Args");
            base.OnNavigatedTo(e);
        }

        protected override void OnBackKeyPress(System.ComponentModel.CancelEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("Form.OnBackKeyPress");

            //base.OnBackKeyPress(e);
            /*if (NavigationService.CanGoBack)
            {
                e.Cancel = true;
                NavigationService.GoBack();
            }*/
            e.Cancel = true;

            java.util.List cmds = (java.util.List)MIDP_form.MIDP_1getCommands();
            javax.microedition.lcdui.CommandListener listener = ((javax.microedition.lcdui.CommandListener)MIDP_form.getListener());
            javax.microedition.lcdui.Command cmdBack = null;
            javax.microedition.lcdui.Command cmdCancel = null;
            for (int N = cmds.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                int type = cmd.getCommandType();
                if (type == javax.microedition.lcdui.Command._fBACK)
                {
                    cmdBack = cmd;
                }
                else if (type == javax.microedition.lcdui.Command._fCANCEL)
                {
                    cmdCancel = cmd;
                }
            }
            net.trekbuddy.wp8.ui.UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                if (cmdBack != null)
                {
                    listener.commandAction(cmdBack, MIDP_form);
                }
                else if (cmdCancel != null)
                {
                    listener.commandAction(cmdCancel, MIDP_form);
                }
                else
                {
                    // should never happen
                    System.Diagnostics.Debug.WriteLine("Form.OnBackKeyPress no command to invoke");
                }
            });
        }

        void button_Checked(object sender, RoutedEventArgs e)
        {
            RadioButton button = sender as RadioButton;
            javax.microedition.lcdui.ChoiceGroup MIDP_choiceGroup = getControlParent(button).Resources[KEY_MIDP_INSTANCE] as javax.microedition.lcdui.ChoiceGroup;
            MIDP_choiceGroup.setSelectedIndex((int)button.Resources[KEY_MIDP_GROUPINDEX], true);
        }

        void checkBox_CheckChanged(object sender, RoutedEventArgs e)
        {
            CheckBox checkBox = sender as CheckBox;
            javax.microedition.lcdui.ChoiceGroup MIDP_choiceGroup = getControlParent(checkBox).Resources[KEY_MIDP_INSTANCE] as javax.microedition.lcdui.ChoiceGroup;
            java.util.List selectedFlags = (java.util.List)MIDP_choiceGroup.MIDP_1getSelectedFlags();
            selectedFlags.set((int)checkBox.Resources[KEY_MIDP_GROUPINDEX], (bool)checkBox.IsChecked ? java.lang.Boolean._fTRUE : java.lang.Boolean._fFALSE);
        }

        void dropDown_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            ListPicker dropDown = sender as ListPicker;
            javax.microedition.lcdui.ChoiceGroup MIDP_choiceGroup = getControlParent(dropDown).Resources[KEY_MIDP_INSTANCE] as javax.microedition.lcdui.ChoiceGroup;
            MIDP_choiceGroup.setSelectedIndex(dropDown.SelectedIndex, true);
        }

        void item_Click(object sender, EventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("Form.item_Click " + sender);
            IApplicationBarMenuItem item = sender as IApplicationBarMenuItem;
            java.util.List cmds = (java.util.List)MIDP_form.MIDP_1getCommands();
            for (int N = cmds.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                string label = item.Text;
                if (label == JUREK_FAKE_TECHNICAL_LABEL)
                {
                    label = string.Empty;
                }
                if (label.Equals(((java.lang.String)cmd.getLabel()).toCSharp()))
                {
                    ((javax.microedition.lcdui.CommandListener)MIDP_form.getListener()).commandAction(cmd, MIDP_form);
                    break;
                }
            }
        }

        private UserControl ToUserControl(javax.microedition.lcdui.Item MIDP_item)
        {
            javax.microedition.lcdui.StringItem MIDP_stringItem = (MIDP_item as javax.microedition.lcdui.StringItem);
            if (MIDP_stringItem != null)
            {
                return new StringItem(MIDP_stringItem);
            }
            javax.microedition.lcdui.ImageItem MIDP_imageItem = (MIDP_item as javax.microedition.lcdui.ImageItem);
            if (MIDP_imageItem != null)
            {
                return new ImageItem(MIDP_imageItem);
            }
            javax.microedition.lcdui.TextField MIDP_textFieldItem = (MIDP_item as javax.microedition.lcdui.TextField);
            if (MIDP_textFieldItem != null)
            {
                return new TextField(MIDP_textFieldItem);
            }
            javax.microedition.lcdui.Gauge MIDP_gauge = (MIDP_item as javax.microedition.lcdui.Gauge);
            if (MIDP_gauge != null)
            {
                return new Gauge(MIDP_gauge, this);
            }
            javax.microedition.lcdui.ChoiceGroup MIDP_choiceGroup = (MIDP_item as javax.microedition.lcdui.ChoiceGroup);
            if (MIDP_choiceGroup != null)
            {
                // TODO MIDP child as other controls
                ChoiceGroup group = new ChoiceGroup(toString((java.lang.String)MIDP_choiceGroup.getLabel()));
                group.Resources.Add(KEY_MIDP_INSTANCE, MIDP_choiceGroup);
                double fontSize = (double)Application.Current.Resources["PhoneFontSizeMediumLarge"];
                switch (MIDP_choiceGroup.MIDP_1getChoiceType())
                {
                    case 1: // javax.microedition.lcdui.ChoiceGroup._fEXCLUSIVE: // Radio
                        {
                            java.util.List parts = (java.util.List)MIDP_choiceGroup.MIDP_1getStringParts();
                            int selectedIndex = MIDP_choiceGroup.getSelectedIndex();
                            for (int M = parts.size(), j = 0; j < M; j++)
                            {
                                RadioButton button = new RadioButton();
                                button.Content = ((java.lang.String)parts.get(j)).toCSharp();
                                button.FontSize = fontSize;
                                button.IsChecked = j == selectedIndex;
                                button.Checked += button_Checked;
                                button.Resources.Add(KEY_MIDP_GROUPINDEX, j);
                                group.Add(button);
                            }
                        }
                        break;
                    case 2: // javax.microedition.lcdui.ChoiceGroup._fMULTIPLE: // Checkboxes
                        {
                            java.util.List parts = (java.util.List)MIDP_choiceGroup.MIDP_1getStringParts();
                            java.util.List flags = (java.util.List)MIDP_choiceGroup.MIDP_1getSelectedFlags();
                            for (int M = parts.size(), j = 0; j < M; j++)
                            {
                                CheckBox checkBox = new CheckBox();
                                TextBlock block = new TextBlock();
                                block.FontSize = fontSize;
                                block.Text = ((java.lang.String)parts.get(j)).toCSharp();
                                block.HorizontalAlignment = HorizontalAlignment.Stretch;
                                block.VerticalAlignment = VerticalAlignment.Center;
                                checkBox.Content = block;
                                checkBox.VerticalContentAlignment = VerticalAlignment.Center;
                                checkBox.VerticalAlignment = VerticalAlignment.Center;
                                checkBox.IsChecked = ((java.lang.Boolean)flags.get(j)).booleanValue();
                                checkBox.Checked += checkBox_CheckChanged;
                                checkBox.Unchecked += checkBox_CheckChanged;
                                checkBox.Resources.Add(KEY_MIDP_GROUPINDEX, j);
                                group.Add(checkBox);
                            }
                        }
                        break;
                    case 4: // javax.microedition.lcdui.ChoiceGroup._fPOPUP: // Combo
                        {
                            ListPicker dropDown = new ListPicker();
                            dropDown.FontSize = fontSize;
                            dropDown.FullModeHeader = ((java.lang.String)MIDP_choiceGroup.getLabel()).toCSharp();
                            dropDown.ItemsSource = ToDropDownModel((java.util.List)MIDP_choiceGroup.MIDP_1getStringParts());
                            dropDown.HorizontalAlignment = HorizontalAlignment.Stretch;
                            if (MIDP_choiceGroup.getSelectedIndex() > -1)
                            {
                                dropDown.SelectedIndex = MIDP_choiceGroup.getSelectedIndex();
                            }
                            dropDown.SelectionChanged += dropDown_SelectionChanged;
                            group.Add(dropDown);
                        }
                        break;
                    default:
                        {
                            System.Diagnostics.Debug.WriteLine("Form unsupported choicegroup: {0}", MIDP_item);
                        }
                        break;
                }
                return group;
            }

            return null;
        }

        private static UserControl getControlParent(FrameworkElement e)
        {
            FrameworkElement uc = e.Parent as FrameworkElement;
            while (!(uc is UserControl))
            {
                uc = uc.Parent as FrameworkElement;
            }
            return uc as UserControl;
        }

        internal static string toString(java.lang.String javaString)
        {
            if (javaString == null)
            {
                return null;
            }
            return javaString.toCSharp();
        }

        private static List<string> ToDropDownModel(java.util.List arr)
        {
            List<string> resp = new List<string>(arr.size());
            for (int N = arr.size(), iter = 0; iter < N; iter++)
            {
                resp.Add(((java.lang.String)arr.get(iter)).toCSharp());
            }
            return resp;
        }
    }
}