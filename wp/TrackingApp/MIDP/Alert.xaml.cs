using System;
using System.Windows;
using System.Windows.Controls;

using org.xmlvm;

namespace TrackingApp
{
    public partial class Alert : UserControl
    {
        private javax.microedition.lcdui.Alert MIDP_alert;

        public Alert(javax.microedition.lcdui.Alert alert)
        {
            InitializeComponent();
            MIDP_Init(alert);
        }

        private void MIDP_Init(javax.microedition.lcdui.Alert alert)
        {
            MIDP_alert = alert;
            title.Text = ((java.lang.String)MIDP_alert.getTitle()).toCSharp();
            if (MIDP_alert.MIDP_1getType() == javax.microedition.lcdui.AlertType._fERROR)
            {
                text.FontSize = 18.667; // PhoneFontSizeSmall
                text.HorizontalAlignment = System.Windows.HorizontalAlignment.Left;
            }
            text.Text = ((java.lang.String)alert.getString()).toCSharp();
            if (ShouldShowBar((java.util.List)MIDP_alert.MIDP_1getCommands(), MIDP_alert.MIDP_1getTimeout()))
                AddCommands((java.util.List)MIDP_alert.MIDP_1getCommands());
            AddTimeout(MIDP_alert.MIDP_1getTimeout());
        }

        private bool ShouldShowBar(java.util.List cmds, int timeout)
        {
            return timeout == javax.microedition.lcdui.Alert._fFOREVER || cmds.size() != 1; // || cmds.get(0) != DISMISS_COMMAND
        }

        private void AddCommands(java.util.List cmds)
        {
            for (int N = cmds.size(), i = 0; i < N; i++)
            {
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                Button button = new Button();
                TextBlock label = new TextBlock();
                label.Text = ((java.lang.String)cmd.getLabel()).toCSharp();
                label.FontSize = (double)Application.Current.Resources["PhoneFontSizeMediumLarge"];
                label.FontFamily = (System.Windows.Media.FontFamily)Application.Current.Resources["PhoneFontFamilySemiLight"];
                button.Content = label;
                button.MinWidth = 192;
                button.MinHeight = 64;
                button.HorizontalContentAlignment = HorizontalAlignment.Center;
                button.Tap += button_Tap;
                buttons.Children.Add(button);
            }
        }

        private void AddTimeout(int timeout)
        {
            if (timeout != javax.microedition.lcdui.Alert._fFOREVER)
            {
                System.Windows.Threading.DispatcherTimer timer = new System.Windows.Threading.DispatcherTimer();
                timer.Interval = new TimeSpan(0, 0, 0, 0, timeout + 25); // 25 ms delay for geeting alert display TODO use some event
                timer.Tick += timer_Tick;
                timer.Start();
            }
        }

        private void onTimeout()
        {
            System.Diagnostics.Debug.WriteLine("Alert.onTimeout");
            (Parent as System.Windows.Controls.Primitives.Popup).IsOpen = false;
            if (MIDP_alert.getListener() != null)
            {
                java.util.List cmds = (java.util.List)MIDP_alert.MIDP_1getCommands();
                javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(0);
                System.Diagnostics.Debug.WriteLine("Alert.onTimeout; fire cmd {0}", ((java.lang.String)cmd.getLabel()).toCSharp());
                ((javax.microedition.lcdui.CommandListener)MIDP_alert.getListener()).commandAction(cmd, MIDP_alert);
            }
            else
            {
                onDismiss();
            }
        }

        private void onDismiss()
        {
            System.Diagnostics.Debug.WriteLine("Alert.onDismiss");
            if (MIDP_alert._fnextDisplayable != null)
                ((javax.microedition.lcdui.Display)javax.microedition.lcdui.Display.getDisplay(null)).setCurrent((javax.microedition.lcdui.Displayable)MIDP_alert._fnextDisplayable);
        }

        private void timer_Tick(object sender, EventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("Alert.timer_Tick " + sender);
            (sender as System.Windows.Threading.DispatcherTimer).Stop();
            onTimeout();
        }

        private void button_Tap(object sender, System.Windows.Input.GestureEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("Alert.button_Tap " + sender);
            (Parent as System.Windows.Controls.Primitives.Popup).IsOpen = false;
            if (MIDP_alert.getListener() != null)
            {
                Button button = sender as Button;
                java.util.List cmds = (java.util.List)MIDP_alert.MIDP_1getCommands();
                for (int N = cmds.size(), i = 0; i < N; i++)
                {
                    javax.microedition.lcdui.Command cmd = (javax.microedition.lcdui.Command)cmds.get(i);
                    if (((TextBlock)button.Content).Text.Equals(((java.lang.String)cmd.getLabel()).toCSharp()))
                    {
                        ((javax.microedition.lcdui.CommandListener)MIDP_alert.getListener()).commandAction(cmd, MIDP_alert);
                        break;
                    }
                }
            }
            else
            {
                onDismiss();
            }
        }
    }
}
