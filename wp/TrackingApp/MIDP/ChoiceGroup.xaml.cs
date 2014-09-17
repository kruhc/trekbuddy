using System;
using System.Windows;
using System.Windows.Controls;

namespace TrackingApp
{
    public partial class ChoiceGroup : UserControl
    {
        public ChoiceGroup()
        {
            InitializeComponent();
        }

        public ChoiceGroup(string label) : this()
        {
            if (label != null)
                this.label.Text = label;
        }

        public void Add(UIElement e)
        {
            panel.Children.Add(e);
        }
    }
}
