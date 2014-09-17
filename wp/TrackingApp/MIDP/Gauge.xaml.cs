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
    public partial class Gauge : UserControl
    {
        private javax.microedition.lcdui.Gauge MIDP_gauge;
        private Form parent;

        public Gauge()
        {
            InitializeComponent();
        }

        public Gauge(javax.microedition.lcdui.Gauge MIDP_gauge, Form parent = null) : this()
        {
            this.MIDP_gauge = MIDP_gauge;
            this.parent = parent;
            this.label.Text = Form.toString((java.lang.String)MIDP_gauge.getLabel());
            this.slider.Minimum = 0;
            this.slider.Maximum = MIDP_gauge.MIDP_1getMaxValue();
            this.slider.Value = MIDP_gauge.MIDP_1getInitialValue();
            this.slider.ValueChanged += slider_ValueChanged;
        }

        void slider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            Slider slider = sender as Slider;
            MIDP_gauge.MIDP_1setValue((int)slider.Value);
            if (parent.IsStateListener)
            {
                parent.ItemStateListener.itemStateChanged(MIDP_gauge);
            }
        }
    }
}
