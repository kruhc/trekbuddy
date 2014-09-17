using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace TrackingApp
{
    public partial class ImageItem : UserControl, javax.microedition.lcdui.ImageItem_2Peer
    {
        private javax.microedition.lcdui.ImageItem MIDP_imageItem;

        public ImageItem()
        {
            InitializeComponent();
        }

        public ImageItem(javax.microedition.lcdui.ImageItem MIDP_imageItem) : this()
        {
            this.MIDP_imageItem = MIDP_imageItem;
            MIDP_imageItem.MIDP_1setPeer(this);
            string label = Form.toString((java.lang.String)MIDP_imageItem.getLabel());
            if (label == null)
            {
                //this.label.Visibility = System.Windows.Visibility.Collapsed;
                this.label.Text = "*";
            }
            else
            {
                this.label.Text = label;
            }
            ImageSource img = getImageSource((javax.microedition.lcdui.Image)MIDP_imageItem.MIDP_1getImage());
            if (MIDP_imageItem.MIDP_1getAppearanceMode() == javax.microedition.lcdui.ImageItem._fBUTTON)
            {
                this.image.Visibility = Visibility.Collapsed;
                this.buttonimage.Source = img;
                this.button.Click += button_Click;
            }
            else
            {
                this.image.Source = img;
                this.button.Visibility = Visibility.Collapsed;
            }
        }

        #region Peer contract

        public void setImage(global::javax.microedition.lcdui.Image n1)
        {
            ImageSource img = getImageSource(n1);
            if (MIDP_imageItem.MIDP_1getAppearanceMode() == javax.microedition.lcdui.ImageItem._fBUTTON)
            {
                this.buttonimage.Source = img;
            }
            else
            {
                this.image.Source = img;
            }
        }

        #endregion

        void button_Click(object sender, RoutedEventArgs e)
        {
            if (MIDP_imageItem.MIDP_1getItemCommandListener() != null)
            {
                javax.microedition.lcdui.Command cmd = MIDP_imageItem.MIDP_1getDefaultCommand() as javax.microedition.lcdui.Command;
                ((javax.microedition.lcdui.ItemCommandListener)MIDP_imageItem.MIDP_1getItemCommandListener()).commandAction(cmd, MIDP_imageItem);
            }
        }

        private ImageSource getImageSource(javax.microedition.lcdui.Image MIDP_image)
        {
            com.codename1.ui.Image CN1_image = (com.codename1.ui.Image)MIDP_image.getNativeImage();
            com.codename1.impl.CodenameOneImage SL_image = (com.codename1.impl.CodenameOneImage)CN1_image.getImage();
            return SL_image.img;
        }
    }
}
