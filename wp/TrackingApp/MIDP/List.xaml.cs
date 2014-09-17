using System;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Navigation;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;

using org.xmlvm;
using AppResources = TrackingApp.Resources.AppResources;

namespace TrackingApp
{
    public partial class List : PhoneApplicationPage, javax.microedition.lcdui.List_2Peer
    {
        private javax.microedition.lcdui.List MIDP_list;
        private java.lang.Object contextObject;

        public List()
        {
            InitializeComponent();
            MIDP_Init();
        }

        private void MIDP_Init()
        {
            System.Object[] args = PhoneApplicationService.Current.State["MIDP.Args"] as System.Object[];
            MIDP_list = (javax.microedition.lcdui.List)args[0];
            MIDP_list.MIDP_1setPeer(this);
            title.Text = ((java.lang.String)MIDP_list.getTitle()).toCSharp();
            list.ItemsSource = ToModel(((java.util.List)MIDP_list.MIDP_1getStringElements()),
                                       ((java.util.List)MIDP_list.MIDP_1getImageElements()));
            list.SelectionChanged += list_SelectionChanged;
//            list.Tap += list_Tap;
            AddCommands(((java.util.List)MIDP_list.MIDP_1getCommands()));
            contextObject = MIDP_list.getContextObject() as java.lang.Object;
        }

        #region Peer contract

        public void append(global::java.lang.String n1, global::javax.microedition.lcdui.Image n2)
        {
            (list.ItemsSource as ObservableCollection<ListItem>).Add(new ListItem(n1.toCSharp(), GetNativeImage(n2)));
        }

        public void delete(int n1)
        {
            (list.ItemsSource as ObservableCollection<ListItem>).RemoveAt(n1);
        }

        public void deleteAll()
        {
            (list.ItemsSource as ObservableCollection<ListItem>).Clear();
        }

        public void set(int n1, global::java.lang.String n2, global::javax.microedition.lcdui.Image n3)
        {
            (list.ItemsSource as ObservableCollection<ListItem>)[n1] = new ListItem(n2.toCSharp(), GetNativeImage(n3));
        }

        #endregion

        private void AddCommands(java.util.List cmds)
        {
            int active = 0;
            for (int N = cmds.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                if (cmd.getCommandType() == javax.microedition.lcdui.Command._fBACK || cmd.getCommandType() == javax.microedition.lcdui.Command._fCANCEL)
                    continue;
                /*if (i < 4) // 4 is limit for buttons
                {
                    ApplicationBarIconButton btn = new ApplicationBarIconButton();
                    btn.IconUri = new Uri("/Assets/basecircle.png", UriKind.Relative);
                    btn.Text = ((java.lang.String)cmd.getLabel()).toCSharp();
                    ApplicationBar.Buttons.Add(btn);
                }
                else*/
                {
                    ApplicationBarMenuItem item = new ApplicationBarMenuItem(((java.lang.String)cmd.getLabel()).toCSharp());
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

//        private Brush itemBackgroundBrush = null;

//        void list_Tap(object sender, System.Windows.Input.GestureEventArgs e)
//        {
//            itemBackgroundBrush = ((e.OriginalSource as FrameworkElement).Parent as StackPanel).Background;
//            ((e.OriginalSource as FrameworkElement).Parent as StackPanel).Background = Application.Current.Resources["PhoneSubtleBrush"] as SolidColorBrush;
//        }

//        void item_Tap(object sender, System.Windows.Input.GestureEventArgs e)
//        {
//            StackPanel item = sender as StackPanel;
//            item = null;
//        }

        void list_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            object selectedItem = list.SelectedItem;
            int idx = list.ItemsSource.IndexOf(selectedItem);
            MIDP_list.setSelectedIndex(idx, true);
            javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)MIDP_list.MIDP_1getSelectCommand() ?? javax.microedition.lcdui.List._fSELECT_1COMMAND;
            ((javax.microedition.lcdui.CommandListener)MIDP_list.getListener()).commandAction(cmd, MIDP_list);
        }

        private void item_Loaded(object sender, RoutedEventArgs e)
        {
            if (FileBrowserHelper.IsActive(contextObject))
            {
                if (FileBrowserHelper.IsFile((((sender as StackPanel).Children[1] as TextBlock).Text)))
                {
                    ContextMenu ctxMenu = new ContextMenu();
                    ctxMenu.IsFadeEnabled = false;
                    ctxMenu.IsZoomEnabled = false;
                    foreach (string cmd in FileBrowserHelper.GetContextCommands(contextObject))
                    {
                        MenuItem ctxMenuItem = new MenuItem()
                        {
                            Header = cmd,
                            Tag = cmd
                        };
                        ctxMenuItem.Click += context_Click;
                        ctxMenu.Items.Add(ctxMenuItem);
                    }
                    ContextMenuService.SetContextMenu((StackPanel)sender, ctxMenu);
                }
            }
        }

        void item_Click(object sender, EventArgs e)
        {
            IApplicationBarMenuItem item = sender as IApplicationBarMenuItem;
            java.util.List cmds = (java.util.List)MIDP_list.MIDP_1getCommands();
            for (int N = cmds.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                if (item.Text.Equals(((java.lang.String)cmd.getLabel()).toCSharp()))
                {
                    ((javax.microedition.lcdui.CommandListener)MIDP_list.getListener()).commandAction(cmd, MIDP_list);
                    break;
                }
            }
        }

        void context_Click(object sender, System.Windows.RoutedEventArgs e)
        {
            string name = ((sender as MenuItem).DataContext as ListItem).Label;
            string cmd = (string)(sender as MenuItem).Header;
            System.Diagnostics.Debug.WriteLine("List.context_Click on " + name + " with " + cmd);
            bool doIt = MessageBox.Show(FileBrowserHelper.GetQuestion(contextObject, cmd, name), AppResources.ApplicationTitle, 
                    MessageBoxButton.OKCancel) == MessageBoxResult.OK;
            if (doIt)
            {
                FileBrowserHelper.Action(contextObject, cmd, name).ConfigureAwait(false);
            }
            //FileBrowserHelper.Close(contextObject);
        }

        private static ObservableCollection<ListItem> ToModel(java.util.List stringElements, java.util.List imageElements)
        {
            int N = stringElements.size();
            ObservableCollection<ListItem> resp = new ObservableCollection<ListItem>();
            for (int iter = 0; iter < N; iter++)
            {
                string label = ((java.lang.String) stringElements.get(iter)).toCSharp();
                ImageSource image = GetNativeImage((javax.microedition.lcdui.Image)imageElements.get(iter));
                resp.Add(new ListItem(label, image));
            }
            return resp;
        }

        private static ImageSource GetNativeImage(javax.microedition.lcdui.Image MIDP_image) {
            ImageSource image = null;
            if (MIDP_image != null)
            {
                com.codename1.ui.Image CN1_image = (com.codename1.ui.Image)MIDP_image.getNativeImage();
                com.codename1.impl.CodenameOneImage SL_image = (com.codename1.impl.CodenameOneImage)CN1_image.getImage();
                image = SL_image.img;
            }
            return image;
        }

        private Uri uri;

        protected override void OnNavigatingFrom(NavigatingCancelEventArgs e)
        {
#if LOG
            CN1Extensions.Log("List.OnNavigatingFrom {0}", uri);
#endif
            ApplicationBar.IsVisible = false;
            base.OnNavigatingFrom(e);
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
#if LOG
            CN1Extensions.Log("List.OnNavigatedFrom {0}", uri);
#endif
            list.SelectionChanged -= list_SelectionChanged;
            list.SelectedItem = null; // reset selection to enable selecting the same item on navigation back
            list.SelectionChanged += list_SelectionChanged;
            base.OnNavigatedFrom(e);
        }
        
        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
#if LOG
            CN1Extensions.Log("List.OnNavigatedTo {0} ({1})", uri = NavigationService.CurrentSource, e.NavigationMode);
#endif
            PhoneApplicationService.Current.State.Remove("MIDP.Args");
            base.OnNavigatedTo(e);
        }

        protected override void OnBackKeyPress(System.ComponentModel.CancelEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("List.OnBackKeyPress");

            //base.OnBackKeyPress(e);
            /*if (NavigationService.CanGoBack)
            {
                e.Cancel = true;
                NavigationService.GoBack();
            }*/
            e.Cancel = true;

            java.util.List cmds = (java.util.List)MIDP_list.MIDP_1getCommands();
            javax.microedition.lcdui.CommandListener listener = ((javax.microedition.lcdui.CommandListener)MIDP_list.getListener());
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
                    listener.commandAction(cmdBack, MIDP_list);
                }
                else if (cmdCancel != null)
                {
                    listener.commandAction(cmdCancel, MIDP_list);
                }
                else
                {
                    // should never happen
                    System.Diagnostics.Debug.WriteLine("List.OnBackKeyPress no command to invoke");
                }
            });
        }
    }

    class ListItem
    {
        public string Label { get; private set; }
        public ImageSource Image { get; private set; }

        public ListItem(string label, ImageSource image)
        {
            this.Label = label;
            if (image != null)
            {
                this.Image = image;
            }
        }
    }
}