using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;

namespace TrackingApp
{
    public partial class ProgressBarWithText : UserControl
    {
        public ProgressBarWithText()
        {
            InitializeComponent();
        }

        public ProgressBarWithText(string text) : this()
        {
            this.label.Text = text;
        }

        public string Text
        {
            set { this.label.Text = value; }
        }

        public int DesignWidth
        {
            get
            {
                return (int)panel.Width;
            }
        }
    }
}
