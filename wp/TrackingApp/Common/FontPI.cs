using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;

using org.xmlvm;
using net.trekbuddy.wp8.ui;

namespace com.codename1.impl
{
    internal partial class SilverlightImplementation : CodenameOneImplementation //, IServiceProvider
    {
//        private Dictionary<StringFontPair, Int32> stringWidthCache = new Dictionary<StringFontPair, Int32>();

        public override int charWidth(java.lang.Object n1, char n2)
        {
            return stringWidth(n1, (new string(n2, 1)).toJava());
        }

        public override int charsWidth(java.lang.Object n1, _nArrayAdapter<char> n2, int n3, int n4)
        {
            global::java.lang.String s = new global::java.lang.String();
            s.@this(n2, n3, n4);
            return stringWidth(n1, s);
        }

        public override int stringWidth(java.lang.Object n1, java.lang.String n2)
        {
            int result = 0;
            NativeFont font = f(n1);
            string str = toCSharp(n2);
//            StringFontPair sfp = new StringFontPair(str, font);
//            lock (stringWidthCache)
//            {
//                if (stringWidthCache.ContainsKey(sfp))
//                {
//                    return stringWidthCache[sfp];
//                }
                UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
                {
                    TextBlock tb = new TextBlock();
                    tb.FontSize = font.height;
                    tb.Text = str;
                    tb.Measure(new Size(1000000, 1000000));
                    result = (int)tb.ActualWidth;
                });
//                stringWidthCache.Add(sfp, result);
//            }
            return result;
        }

        public override int getFace(global::java.lang.Object n1)
        {
            return f(n1).systemFace;
        }

        public override int getSize(global::java.lang.Object n1)
        {
            return f(n1).systemSize;
        }

        public override int getStyle(global::java.lang.Object n1)
        {
            return f(n1).systemStyle;
        }

        public override int getHeight(java.lang.Object n1)
        {
            return f(n1).actualHeight;
        }

        public override object getDefaultFont()
        {
            if (defaultFont == null)
            {
                defaultFont = (NativeFont)createFont(com.codename1.ui.Font._fFACE_1SYSTEM, com.codename1.ui.Font._fSTYLE_1PLAIN, com.codename1.ui.Font._fSIZE_1MEDIUM);
            }
            return defaultFont;
        }

        private Dictionary<int, object> fontCache = new Dictionary<int, object>();

        public override object createFont(int face, int style, int size)
        {
            if (fontCache.ContainsKey(face | style | size))
            {
                return fontCache[face | style | size];
            }

            //int a = 24;
            double a = 25.333; // PhoneFontSizeMediumLarge
            switch (size)
            {
                case 8: //com.codename1.ui.Font._fSIZE_1SMALL:
                    //a = 15;
                    a = 18.667; // PhoneFontSizeSmall
                    break;
                case 16: //com.codename1.ui.Font._fSIZE_1LARGE:
                    //a = 56;
                    a = 42.667; // PhoneFontSizeExtraLarge
                    break;
            }

            NativeFont nf = new NativeFont();
            nf.height = (int)Math.Round(a);
            nf.systemFace = face;
            nf.systemSize = size;
            nf.systemStyle = style;
            UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
            {
                TextBlock tb = new TextBlock();
                tb.FontSize = nf.height;
                tb.Text = "Xp°";
                tb.Measure(new Size(1000000, 1000000));
                nf.actualHeight = (int)tb.ActualHeight;
            });

            if ((style & com.codename1.ui.Font._fSTYLE_1BOLD) == com.codename1.ui.Font._fSTYLE_1BOLD)
            {
                nf.bold = true;
            }
            if ((style & com.codename1.ui.Font._fSTYLE_1ITALIC) == com.codename1.ui.Font._fSTYLE_1ITALIC)
            {
                nf.italic = true;
            }

            fontCache[face | style | size] = nf;
            return nf;
        }

        private NativeFont f(java.lang.Object fnt)
        {
            if (fnt == null) return (NativeFont)getDefaultFont();
            return (NativeFont)fnt;
        }
    }

    internal class NativeFont : global::java.lang.Object
    {
        public int height;
        public int systemSize;
        public int systemFace;
        public int systemStyle;
        public bool bold;
        public bool italic;
        public int actualHeight;

        public override bool Equals(object o)
        {
            NativeFont f = (NativeFont)o;
            return f.height == height && f.systemFace == systemFace && f.systemSize == systemSize && f.systemStyle == systemStyle;
        }

        public override int GetHashCode()
        {
            return height;
        }
    }

    internal class StringFontPair
    {
        public string str;
        public NativeFont font;

        public StringFontPair(string str, NativeFont font)
        {
            this.str = str;
            this.font = font;
        }

        public override bool Equals(Object o)
        {
            StringFontPair sp = (StringFontPair)o;
            return str.Equals(sp.str) && font.Equals(sp.font);
        }

        public override int GetHashCode()
        {
            return str.GetHashCode();
        }
    }
}
