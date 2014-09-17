using System;
using System.Windows;
using System.Windows.Controls;

namespace TrackingApp
{
    public partial class StringItem : UserControl
    {
        public StringItem()
        {
            InitializeComponent();
        }

        public StringItem(javax.microedition.lcdui.StringItem MIDP_stringItem) : this()
        {
            this.label.Text = Form.toString((java.lang.String)MIDP_stringItem.getLabel());
            string text = Form.toString((java.lang.String)MIDP_stringItem.getText());
            if (text != null)
            {
                while (text.StartsWith("\n") || text.StartsWith(" "))
                {
                    text = text.Substring(1);
                }
                this.text.Text = text;
            }
        }
    }
}
