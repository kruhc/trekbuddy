using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;

namespace TrackingApp
{
    public partial class LogLevelSetting : UserControl
    {
        public LogLevelSetting()
        {
            InitializeComponent();
            this.button.Click += button_Click;
            setLevel(CN1Extensions.GetLevel());
        }

        void setLevel(int level)
        {
            (this.selection.Children[level - 1] as RadioButton).IsChecked = true;
        }

        void changeLevel()
        {
            for (int i = 0; i < this.selection.Children.Count; i++)
            {
                if ((bool)(this.selection.Children[i] as RadioButton).IsChecked)
                {
                    CN1Extensions.SetLevel(i + 1);
                    break;
                }
            }
        }

        void button_Click(object sender, RoutedEventArgs e)
        {
            changeLevel();
            (this.Parent as System.Windows.Controls.Primitives.Popup).IsOpen = false;
        }
    }
}
