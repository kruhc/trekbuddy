using System;
using System.Windows;
using System.Windows.Controls;

using org.xmlvm;

namespace TrackingApp
{
    public partial class TextField : UserControl
    {
        private javax.microedition.lcdui.TextField MIDP_textField;

        public TextField()
        {
            InitializeComponent();
        }

        public TextField(javax.microedition.lcdui.TextField MIDP_textField) : this()
        {
            this.MIDP_textField = MIDP_textField;
            this.label.Text = Form.toString((java.lang.String)MIDP_textField.getLabel());
            string text = Form.toString((java.lang.String)MIDP_textField.getString());
            if (text == null)
            {
                text = "";
                MIDP_textField.setString("".toJava());
            }
            this.text.Text = text;
            this.text.TextChanged += text_TextChanged;
        }

        void text_TextChanged(object sender, TextChangedEventArgs e)
        {
            TextBox textBox = sender as TextBox;
            MIDP_textField.setString(textBox.Text.toJava());
        }
    }
}
