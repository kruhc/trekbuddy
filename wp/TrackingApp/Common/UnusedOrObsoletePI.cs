using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Tasks;

using org.xmlvm;
using net.trekbuddy.wp8.ui;

namespace com.codename1.impl
{
    internal partial class SilverlightImplementation : CodenameOneImplementation //, IServiceProvider
    {
#if __UNUSED
        private com.codename1.ui.Component currentlyPainting;
        private com.codename1.ui.TextArea currentlyEditing;
        internal static TextBox textInputInstance;
        internal static LinkedList<UIElement> currentPaintDestination, currentPaintContainer;
#endif

#if __UNUSED
        void Touch_FrameReported(object sender, TouchFrameEventArgs args)
        {
            TouchPointCollection col = args.GetTouchPoints(cl);
            TouchAction act = args.GetPrimaryTouchPoint(cl).Action;
#if LOG
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.input touch reported; count: " + col.Count + "; act: " + act));
#endif
            if (col.Count == 1)
            {
                if (instance.currentlyEditing != null)
                {
                    com.codename1.ui.Form f = (com.codename1.ui.Form)instance.currentlyEditing.getComponentForm();
                    if (f.getComponentAt((int)col[0].Position.X, (int)col[0].Position.Y) == instance.currentlyEditing) {
                        return;
                    }
                }
                if (act == TouchAction.Down)
                {
                    pointerPressed((int)col[0].Position.X, (int)col[0].Position.Y);
                    return;
                }
                if (act == TouchAction.Up)
                {
                    if (instance.currentlyEditing != null)
                    {
                        com.codename1.ui.Form f = (com.codename1.ui.Form)instance.currentlyEditing.getComponentForm();
                        if (f.getComponentAt((int)col[0].Position.X, (int)col[0].Position.Y) != instance.currentlyEditing)
                        {
                            commitEditing();
                        }
                    }

                    pointerReleased((int)col[0].Position.X, (int)col[0].Position.Y);
                    return;
                }
                if (act == TouchAction.Move)
                {
                    pointerDragged((int)col[0].Position.X, (int)col[0].Position.Y);
                    return;
                }
                return;
            }

            int[] x = new int[col.Count];
            int[] y = new int[x.Length];
            for(int iter = 0 ; iter < col.Count ; iter++) {
                x[iter] = (int)col[iter].Position.X;
                x[iter] = (int)col[iter].Position.Y;
            }
            _nArrayAdapter<int> xarr = new _nArrayAdapter<int>(x);
            _nArrayAdapter<int> yarr = new _nArrayAdapter<int>(y);
            if (act == TouchAction.Down)
            {
                pointerPressed(xarr, yarr);
                return;
            }
            if (act == TouchAction.Up)
            {
                pointerReleased(xarr, yarr);
                return;
            }
            if (act == TouchAction.Move)
            {
                pointerDragged(xarr, yarr);
                return;
            }
        }
#endif

        #region CN1 Text handling (OBSOLETE)

        public override void editString(global::com.codename1.ui.Component n1, int n2, int n3, global::java.lang.String n4, int n5) {
            throw new NotSupportedException();
        }
#if __UNUSED
        public override bool isNativeInputSupported()
        {
            return true;
        }

        public override bool isNativeInputImmediate()
        {
            return true;
        }

        public static void commitEditing()
        {
            instance.currentlyEditing = null;
        }

        public override void editString(global::com.codename1.ui.Component n1, int n2, int n3, global::java.lang.String n4, int n5)
        {
            com.codename1.ui.Display d = (com.codename1.ui.Display)com.codename1.ui.Display.getInstance();
            if (textInputInstance != null)
            {
                commitEditing();
                d.callSerially(new EditString(n1, n2, n3, n4, n5));
                return;
            }
            currentlyEditing = (com.codename1.ui.TextArea)n1;
//            using (AutoResetEvent are = new AutoResetEvent(false))
//            {
//                System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
                {
                    textInputInstance = new TextBox();
                    textInputInstance.TextChanged += textChangedEvent;
                    cl.Children.Add(textInputInstance);
                    Canvas.SetZIndex(textInputInstance, 50000);
                    textInputInstance.Text = toCSharp(n4);
                    textInputInstance.IsEnabled = true;
                    com.codename1.ui.Font fnt = (com.codename1.ui.Font)((com.codename1.ui.plaf.Style)currentlyEditing.getStyle()).getFont();
                    NativeFont font = f((java.lang.Object)fnt.getNativeFont());

                    // workaround forsome weird unspecified margin that appears around the text box
                    Canvas.SetTop(textInputInstance, currentlyEditing.getAbsoluteY() - 10);
                    Canvas.SetLeft(textInputInstance, currentlyEditing.getAbsoluteX() - 10);
                    textInputInstance.Height = currentlyEditing.getHeight() + 20;
                    textInputInstance.Width = currentlyEditing.getWidth() + 20;
                    textInputInstance.BorderThickness = new Thickness(0);
                    textInputInstance.FontSize = font.height;
                    textInputInstance.Margin = new Thickness(0);
                    textInputInstance.Padding = new Thickness(0);
                    textInputInstance.Clip = null;
                    textInputInstance.AcceptsReturn = !currentlyEditing.isSingleLineTextArea();
                    textInputInstance.Focus();
//                    are.Set();
                });
//                are.WaitOne();
//            }
            d.invokeAndBlock(new WaitForEdit());
//            using (AutoResetEvent are = new AutoResetEvent(false))
//            {
//              System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
                {
                    cl.Children.Remove(textInputInstance);
                    textInputInstance = null;
                    //cl.Focus();
                });
//            }
        }
#endif // __UNUSED

#if __UNUSED
        void textChangedEvent(object sender, EventArgs e)
        {
            com.codename1.ui.Display disp = (com.codename1.ui.Display)com.codename1.ui.Display.getInstance();
            Tchange t = new Tchange();
            t.currentlyEditing = currentlyEditing;
            t.text = toJava(textInputInstance.Text);
            disp.callSerially(t);
        }

        class Tchange : java.lang.Object, java.lang.Runnable
        {
            public com.codename1.ui.TextArea currentlyEditing;
            public java.lang.String text;
            public virtual void run()
            {
                if (currentlyEditing != null)
                {
                    currentlyEditing.setText(text);
                }
            }
        }
#endif

        #endregion

        #region CN1 Photo capture implementation

#if __UNUSED
        public static bool exitLock;
        private global::com.codename1.ui.events.ActionListener pendingCaptureCallback;

        public override void capturePhoto(global::com.codename1.ui.events.ActionListener n1)
        {
            exitLock = true;
            pendingCaptureCallback = n1;
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                CameraCaptureTask t = new CameraCaptureTask();
                t.Completed += t_Completed;
                t.Show();
            });
        }

        private void fireCapture(com.codename1.ui.events.ActionEvent ev)
        {
            com.codename1.ui.util.EventDispatcher ed = new com.codename1.ui.util.EventDispatcher();
            ed.@this();
            ed.addListener((java.lang.Object)pendingCaptureCallback);
            ed.fireActionEvent(ev);
            pendingCaptureCallback = null;
            exitLock = false;
        }

        void t_Completed(object sender, PhotoResult e)
        {
            if (e.OriginalFileName != null)
            {
                IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
                string file = e.OriginalFileName.Substring(e.OriginalFileName.LastIndexOf(NATIVE_FSSEP) + 1);
                e.ChosenPhoto.CopyTo(store.OpenFile(file, FileMode.OpenOrCreate));
                com.codename1.ui.events.ActionEvent ac = new com.codename1.ui.events.ActionEvent();
                ac.@this(toJava("file:/" + file));
                fireCapture(ac);
            }
            else
            {
                fireCapture(null);
            }
        }
#endif

        #endregion

        #region CN1 Browser methods

#if __UNUSED
        public override bool isNativeBrowserComponentSupported()
        {
            return true;
        }

        com.codename1.ui.BrowserComponent currentBrowser;
        public override global::System.Object createBrowserComponent(global::java.lang.Object n1)
        {
            SilverlightPeer sp = null;
            UISynchronizationContext.Dispatcher.InvokeSync(() =>
            {
                Microsoft.Phone.Controls.WebBrowser wb = new WebBrowser();
                currentBrowser = (com.codename1.ui.BrowserComponent)n1;
                wb.Navigating += wb_Navigating;
                wb.Navigated += wb_Navigated;
                wb.NavigationFailed += wb_NavigationFailed;
                sp = new SilverlightPeer(wb);
            });
            return sp;
        }

        void wb_NavigationFailed(object sender, System.Windows.Navigation.NavigationFailedEventArgs e)
        {
            com.codename1.ui.events.BrowserNavigationCallback bn = (com.codename1.ui.events.BrowserNavigationCallback)currentBrowser.getBrowserNavigationCallback();
            com.codename1.ui.events.ActionEvent ev = new com.codename1.ui.events.ActionEvent();
            ev.@this(toJava(e.Uri.OriginalString));
            currentBrowser.fireWebEvent(toJava("onError"), ev);
        }

        void wb_Navigated(object sender, System.Windows.Navigation.NavigationEventArgs e)
        {
            com.codename1.ui.events.BrowserNavigationCallback bn = (com.codename1.ui.events.BrowserNavigationCallback)currentBrowser.getBrowserNavigationCallback();
            com.codename1.ui.events.ActionEvent ev = new com.codename1.ui.events.ActionEvent();
            ev.@this(toJava(e.Uri.OriginalString));
            currentBrowser.fireWebEvent(toJava("onLoad"), ev);
        }

        void wb_Navigating(object sender, NavigatingEventArgs e)
        {
            com.codename1.ui.events.BrowserNavigationCallback bn = (com.codename1.ui.events.BrowserNavigationCallback)currentBrowser.getBrowserNavigationCallback();
            if (!bn.shouldNavigate(toJava(e.Uri.OriginalString)))
            {
                e.Cancel = true;
            }
            com.codename1.ui.events.ActionEvent ev = new com.codename1.ui.events.ActionEvent();
            ev.@this(toJava(e.Uri.OriginalString));
            currentBrowser.fireWebEvent(toJava("onStart"), ev);
        }

        public override global::System.Object getBrowserTitle(global::com.codename1.ui.PeerComponent n1)
        {
            WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
            return toJava((string)s.InvokeScript("eval", "document.title.toString()"));
        }

        public override global::System.Object getBrowserURL(global::com.codename1.ui.PeerComponent n1)
        {
            WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
            return toJava(s.Source.OriginalString);
        }

        public override void setBrowserURL(global::com.codename1.ui.PeerComponent n1, global::java.lang.String n2)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
                string uri = toCSharp(n2);
                if (uri.StartsWith("jar:/"))
                {
                    uri = uri.Substring(5);
                    while (uri[0] == '/')
                    {
                        uri = uri.Substring(1);
                    }
                    uri = "res/" + uri;
                    s.Source = new Uri(uri, UriKind.Relative);
                    return;
                }
                s.Source = new Uri(uri);
            });
        }

        public override void browserReload(global::com.codename1.ui.PeerComponent n1)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
                s.Source = s.Source;
            });
        }

        public override bool browserHasBack(global::com.codename1.ui.PeerComponent n1)
        {
            WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
            return s.CanGoBack;
        }

        public override bool browserHasForward(global::com.codename1.ui.PeerComponent n1)
        {
            WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
            return s.CanGoForward;
        }

        public override void browserBack(global::com.codename1.ui.PeerComponent n1)
        {
            WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
            s.GoBack();
        }

        public override void browserStop(global::com.codename1.ui.PeerComponent n1)
        {
        }

        public override void browserDestroy(global::com.codename1.ui.PeerComponent n1)
        {
        }

        public override void browserForward(global::com.codename1.ui.PeerComponent n1)
        {
            WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
            s.GoForward();
        }

        public override void browserClearHistory(global::com.codename1.ui.PeerComponent n1)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
                s.ClearCookiesAsync();
                s.ClearInternetCacheAsync();
            });
        }

        public override void setBrowserPage(global::com.codename1.ui.PeerComponent n1, global::java.lang.String n2, global::java.lang.String n3)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
                s.NavigateToString(toCSharp(n2));
            });
        }

        public override void execute(global::java.lang.String n1)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                Microsoft.Phone.Tasks.WebBrowserTask t = new Microsoft.Phone.Tasks.WebBrowserTask();
                t.Uri = new Uri(toCSharp(n1), UriKind.RelativeOrAbsolute);
                t.Show();
            });
        }

        public override void browserExecute(global::com.codename1.ui.PeerComponent n1, global::java.lang.String n2)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
                s.InvokeScript(toCSharp(n2));
            });
        }

        public override global::System.Object browserExecuteAndReturnString(global::com.codename1.ui.PeerComponent n1, global::java.lang.String n2)
        {
            string st = null;
            using (AutoResetEvent are = new AutoResetEvent(false))
            {
                UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                {
                    WebBrowser s = (WebBrowser)((SilverlightPeer)n1).element;
                    st = (string)s.InvokeScript(toCSharp(n2));
                    are.Set();
                });
                are.WaitOne();
            }
            return toJava(st);
        }
#endif

        #endregion

        #region CN1 Media methods

#if __UINUSED
        public override global::System.Object createMedia(global::java.lang.String uri, bool video, global::java.lang.Runnable onComplete)
        {
            return new CN1Media(toCSharp(uri), video, onComplete);
        }

        public override global::System.Object createMedia(global::java.io.InputStream n1, global::java.lang.String n2, global::java.lang.Runnable n3)
        {
            java.io.OutputStream os = (java.io.OutputStream)createStorageOutputStream(toJava("CN1TempVideodu73aFljhuiw3yrindo87.mp4"));
            com.codename1.io.Util.copy(n1, os);
            IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
            Stream s = store.OpenFile("CN1TempVideodu73aFljhuiw3yrindo87.mp4", FileMode.Open);
            return new CN1Media(s, toCSharp(n2), n3);
        }

        /*public virtual global::System.Object createMediaRecorder(global::java.lang.String n1, global::java.lang.String n2)
        {
        }*/
#endif

        #endregion

        #region CN1 component UI framework methods (OBSOLETE)

        // hack
        public readonly WipeComponent pendingWipe = null;

#if __UNUSED

        private static readonly java.lang.String componentKey = toJava("$$wpt");
        private static readonly java.lang.String containerKey = toJava("$$cpt");
        public WipeComponent pendingWipe;

        public override void beforeComponentPaint(global::com.codename1.ui.Component n1, global::com.codename1.ui.Graphics n2)
        {
            if (n2.getGraphics() != globalGraphics)
            {
                return;
            }
            currentlyPainting = n1;
            CSharpListHolder cl = (CSharpListHolder)n1.getClientProperty(componentKey);
            if (cl != null)
            {
                if (cl.ll.Count > 0)
                {
                    //System.Diagnostics.Debug.WriteLine("Painting component with " + cl.ll.Count + " elements");
                    //System.Diagnostics.Debug.WriteLine(toCSharp((java.lang.String)n1.toString()));
                    pendingWipe = new WipeComponent(globalGraphics, cl.ll, toCSharp((java.lang.String)n1.toString()));
                    cl.ll.Clear();
                }
            }
            else
            {
                //System.Diagnostics.Debug.WriteLine("Painting component with no previous elements");
                //System.Diagnostics.Debug.WriteLine(toCSharp((java.lang.String)n1.toString()));
                cl = new CSharpListHolder();
                currentlyPainting.putClientProperty(componentKey, cl);
            }
            currentPaintDestination = cl.ll;
            /*if (n1 is global::com.codename1.ui.Form)
            {
                pendingPaints.Clear();
                pendingPaints.Add(new WipeScreen(globalGraphics));
            }*/
            if (getCurrentForm() != null && n1 is global::com.codename1.ui.Container && n1 == ((global::com.codename1.ui.Form)getCurrentForm()).getContentPane())
            {
#if LOG
                com.codename1.io.Log.p(SilverlightImplementation.toJava("FORM CONTENT PANE"));
#endif
                CSharpListHolder cl2 = (CSharpListHolder)n1.getClientProperty(containerKey);
                if (cl2 != null)
                {
                    if (cl2.ll.Count > 0)
                    {
                        // TODO lock
                        globalGraphics.pendingPaintsList.Add(new WipeComponent(globalGraphics, cl2.ll, toCSharp((java.lang.String)n1.toString())));
                        cl2.ll.Clear();
                    }
                }
                else
                {
                    cl2 = new CSharpListHolder();
                    n1.putClientProperty(containerKey, cl2);
                }
                currentPaintContainer = cl2.ll;
            }
        }

        public override void componentRemoved(global::com.codename1.ui.Component c)
        {
            beforeComponentPaint(c, (global::com.codename1.ui.Graphics)getCodenameOneGraphics());
            afterComponentPaint(c, (global::com.codename1.ui.Graphics)getCodenameOneGraphics());
        }

        public override void afterComponentPaint(global::com.codename1.ui.Component n1, global::com.codename1.ui.Graphics n2)
        {
            if (n2.getGraphics() != globalGraphics)
            {
                return;
            }
            if (pendingWipe != null)
            {
                globalGraphics.paint(pendingWipe);
                pendingWipe = null;
            }
            currentlyPainting = null;
            currentPaintDestination = null;
            /*if (n1 is global::com.codename1.ui.Form)
            {
                pendingPaints.Clear();
                pendingPaints.Add(new WipeScreen(globalGraphics));
            }*/
            if (getCurrentForm() != null && n1 is global::com.codename1.ui.Container && n1 == ((global::com.codename1.ui.Form)getCurrentForm()).getContentPane())
            {
#if LOG
                com.codename1.io.Log.p(("~FORM CONTENT PANE; paints: " + currentPaintContainer.Count).toJava());
#endif
                currentPaintContainer = null;
            }
        }

        public override void repaint(ui.animations.Animation n1)
        {
            if (n1 is global::com.codename1.ui.Canvas)
            {
                goto repaint;
            }
            if (n1 is global::com.codename1.ui.Form)
            {
                wipe();
            }
        repaint:
            base.repaint(n1);
        }

#endif

#if false // called from flushGraphics(int,int,int,int)

        private void paintCurrent(List<OperationPending> currentPaints, int x, int y, int h, int w)
        {
            using (AutoResetEvent are = new AutoResetEvent(false))
            {
                System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                {
                    Microsoft.Xna.Framework.Rectangle a = new Microsoft.Xna.Framework.Rectangle(x, y, w, h);
                    int count = currentPaints.Count;
                    //System.Diagnostics.Debug.WriteLine("Drawing " + count + " elements on " + cl.Children.Count + " that are on the screen");
                    for (int iter = 0; iter < count; iter++)
                    {
                        //currentPaints[iter].printLogging();
                        if (!currentPaints[iter].clipSet)
                        {
                            currentPaints[iter].clipSet = true;
                            currentPaints[iter].clipX = x;
                            currentPaints[iter].clipY = y;
                            currentPaints[iter].clipW = w;
                            currentPaints[iter].clipH = h;
                        }
                        else
                        {
                            Microsoft.Xna.Framework.Rectangle b = new Microsoft.Xna.Framework.Rectangle(currentPaints[iter].clipX, currentPaints[iter].clipY, currentPaints[iter].clipW, currentPaints[iter].clipH);
                            Microsoft.Xna.Framework.Rectangle r = Microsoft.Xna.Framework.Rectangle.Intersect(a, b);
                            currentPaints[iter].clipX = r.Left;
                            currentPaints[iter].clipY = r.Top;
                            currentPaints[iter].clipW = r.Width;
                            currentPaints[iter].clipH = r.Height;
                        }
                        currentPaints[iter].perform(cl);
                    }
                });
                are.WaitOne();
            }
        }

#endif

        #endregion

        #region Text editing helper classes (OBSOLETE?) INNER CLASSES!!!

#if __UNUSED
        class WaitForEdit : java.lang.Object, java.lang.Runnable
        {
            public virtual void run()
            {
                while (SilverlightImplementation.instance.currentlyEditing != null)
                {
                    global::System.Threading.Thread.Sleep(1);
                }
            }
        }

        class EditString : java.lang.Object, java.lang.Runnable
        {
            private global::com.codename1.ui.Component n1;
            private int n2;
            private int n3;
            private global::java.lang.String n4;
            private int n5;

            public EditString(global::com.codename1.ui.Component n1, int n2, int n3, global::java.lang.String n4, int n5)
            {
                this.n1 = n1;
                this.n2 = n2;
                this.n3 = n3;
                this.n4 = n4;
                this.n5 = n5;
            }

            public virtual void run()
            {
                com.codename1.ui.Display d = (com.codename1.ui.Display)com.codename1.ui.Display.getInstance();
                d.editString(n1, n2, n3, n4, n5);
            }
        }

        class WaitForNativeEdit : java.lang.Object, java.lang.Runnable
        {
            public virtual void run()
            {
                while (SilverlightImplementation.textInputInstance != null)
                {
                    global::System.Threading.Thread.Sleep(1);
                }
            }
        }
#endif

        #endregion INNER!!!

        #region CN1 Soft/Hard references factory methods implementation (OBSOLETE?)

        public override global::System.Object createSoftWeakRef(global::java.lang.Object n1)
        {
            return new SoftRef(n1);
        }

        public override global::System.Object extractHardRef(global::java.lang.Object n1)
        {
            if (n1 != null)
            {
                return ((SoftRef)n1).get();
            }
            return null;
        }

        #endregion

        #region CN1 Network operations methods

        public override global::System.Object connect(global::java.lang.String n1, bool read, bool write)
        {
            NetworkOperation n = new NetworkOperation();
            string s = toCSharp(n1);
            n.request = (HttpWebRequest)WebRequest.Create(new Uri(s));
            n.request.AllowAutoRedirect = false;
            return n;
        }

        public override void setHeader(global::java.lang.Object n1, global::java.lang.String n2, global::java.lang.String n3)
        {
            NetworkOperation n = (NetworkOperation)n1;
            string key = toCSharp(n2);
            string value = toCSharp(n3);
            if (key.ToLower().Equals("accept"))
            {
                n.request.Accept = value;
                return;
            }
            if (key.ToLower().Equals("connection") || key.ToLower().Equals("keepalive") ||
                key.ToLower().Equals("expect") || key.ToLower().Equals("date") || key.ToLower().Equals("host") ||
                key.ToLower().Equals("if-modified-since") || key.ToLower().Equals("range") ||
                key.ToLower().Equals("referer") || key.ToLower().Equals("transfer-encoding") ||
                key.ToLower().Equals("user-agent"))
            {
                return;
            }
            if (key.ToLower().Equals("content-length"))
            {
                n.request.ContentLength = Int32.Parse(value);
                return;
            }
            if (key.ToLower().Equals("content-type"))
            {
                if (n.request.Method.ToLower().Equals("get"))
                {
                    // if content type is set on a get request silverlight throws an exception, correct but a
                    // common bug!
                    return;
                }
                n.request.ContentType = value;
                return;
            }
            n.request.Headers[key] = value;
        }

        public override int getContentLength(global::java.lang.Object n1)
        {
            NetworkOperation n = (NetworkOperation)n1;
            return (int)n.response.ContentLength;
        }

        public override void setPostRequest(global::java.lang.Object n1, bool n2)
        {
            NetworkOperation n = (NetworkOperation)n1;
            if (n2)
            {
                n.request.Method = "POST";
            }
            else
            {
                n.request.Method = "GET";
            }
        }

        public override int getResponseCode(global::java.lang.Object n1)
        {
            NetworkOperation n = (NetworkOperation)n1;
            HttpWebResponse res = n.response;
            int i = 0;
            UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
            {
                i = (int)res.StatusCode;
            });
            return i;
        }

        public override global::System.Object getResponseMessage(global::java.lang.Object n1)
        {
            return null;
        }

        public override global::System.Object getHeaderField(global::java.lang.String n1, global::java.lang.Object n2)
        {
            NetworkOperation n = (NetworkOperation)n2;
            return toJava(n.response.Headers[toCSharp(n1)]);
        }

        public override global::System.Object getHeaderFieldNames(global::java.lang.Object n1)
        {
            NetworkOperation n = (NetworkOperation)n1;
            int i = n.response.Headers.Count;
            java.lang.String[] arr = new java.lang.String[i];
            _nArrayAdapter<global::System.Object> r = new _nArrayAdapter<global::System.Object>(arr);
            string[] keys = n.response.Headers.AllKeys;
            for (int iter = 0; iter < i; iter++)
            {
                arr[iter] = toJava(keys[iter]);
            }
            return r;
        }

        public override global::System.Object getHeaderFields(global::java.lang.String n1, global::java.lang.Object n2)
        {
            NetworkOperation n = (NetworkOperation)n2;
            String s = n.response.Headers[toCSharp(n1)];
            if (s == null)
            {
                return null;
            }
            return new _nArrayAdapter<global::System.Object>(new java.lang.String[] { toJava(s) });
        }

        #endregion

        /*public override global::System.Object createNativePeer(global::java.lang.Object n1)
        {
        }*/

        #region CN1 UI/Display methods (OBSOLETE)

#if __UNUSED
        public override void setCurrentForm(ui.Form n1)
        {
            wipe();
            base.setCurrentForm(n1);
        }

        public override void tileImage(global::java.lang.Object n1, global::java.lang.Object n2, int x, int y, int w, int h)
        {
            NativeGraphics ng = (NativeGraphics)n1;

            // wipe the screen if the operation will clear the whole screen
            int dw = getDisplayWidth();
            int dh = getDisplayHeight();
            if (ng == globalGraphics && x == 0 && y == 0 && w == dw && h == dh && ng.clipX == 0 && ng.clipY == 0 && ng.clipW == w &&
                ng.clipH == h && ng.alpha == 255)
            {
                wipe();
            }
            base.tileImage(n1, n2, x, y, w, h);
        }
#endif

        #endregion

        public static java.lang.String toJava(System.String str)
        {
            return global::org.xmlvm._nUtil.toJavaString(str);
        }

#if __UNUSED
        public override bool hasNativeTheme()
        {
            return true;
        }

        public override void installNativeTheme()
        {
            com.codename1.ui.util.Resources r = (com.codename1.ui.util.Resources)com.codename1.ui.util.Resources.open("/winTheme.res".toJava());
            com.codename1.ui.plaf.UIManager uim = (com.codename1.ui.plaf.UIManager)com.codename1.ui.plaf.UIManager.getInstance();
            global::System.Object[] themeNames = ((_nArrayAdapter<global::System.Object>)r.getThemeResourceNames()).getCSharpArray();
            uim.setThemeProps((java.util.Hashtable)r.getTheme((java.lang.String)themeNames[0]));
            com.codename1.ui.plaf.DefaultLookAndFeel dl = (com.codename1.ui.plaf.DefaultLookAndFeel)uim.getLookAndFeel();
            dl.setDefaultEndsWith3Points(false);
        }

        public override int getCommandBehavior()
        {
            // COMMAND_BEHAVIOR_BUTTON_BAR
            return 4;
        }

        public override bool canForceOrientation()
        {
            return true;
        }

        public override void lockOrientation(bool n1)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                if (n1)
                {
                    app.SupportedOrientations = SupportedPageOrientation.Portrait;
                }
                else
                {
                    app.SupportedOrientations = SupportedPageOrientation.Landscape;
                }
            });
        }

        public override void unlockOrientation()
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                app.SupportedOrientations = SupportedPageOrientation.PortraitOrLandscape;
            });
        }
#endif


#if __UNUSED
        // TODO use Buffer.BlockCopy
        public static sbyte[] toSByteArray(byte[] byteArray)
        {
            sbyte[] sbyteArray = null;
            if (byteArray != null)
            {
                sbyteArray = new sbyte[byteArray.Length];
                for (int index = 0; index < byteArray.Length; index++)
                    sbyteArray[index] = (sbyte)byteArray[index];
            }
            return sbyteArray;
        }
#endif
    }

    public class SoftRef : java.lang.Object
    {
        global::System.WeakReference w;
        //java.lang.Object o;

        public SoftRef(java.lang.Object obj)
        {
            w = new WeakReference(obj);
            //o = obj;
        }

        public java.lang.Object get()
        {
            java.lang.Object o = (java.lang.Object)w.Target;
            return o;
            //return o;
        }
    }

#if __UNUSED

    class PlacePeer : OperationPending
    {
        int x;
        int y;
        int w;
        int h;
        FrameworkElement elem;

        public PlacePeer(NativeGraphics ng, WipeComponent pendingWipe, int x, int y, int w, int h, FrameworkElement elem)
            : base(ng, pendingWipe)
        {
            this.elem = elem;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return null;
        }

        public override void perform(Canvas cl)
        {
            if (elem.Parent == null)
            {
                cl.Children.Add(elem);
            }
            if (pendingWipe != null && pendingWipe.ll != null)
            {
                pendingWipe.ll.Remove(elem);
            }
            Canvas.SetLeft(elem, x);
            Canvas.SetTop(elem, y);
            elem.Width = w;
            elem.Height = h;
            updateClip(elem, x, y);
            add(elem);
        }

        public override void printLogging()
        {
            log("Painting native peer");
        }
    }

    public class SilverlightPeer : com.codename1.ui.PeerComponent
    {
        public FrameworkElement element;
        public SilverlightPeer(FrameworkElement element)
        {
            this.element = element;
            @this(null);
        }

        public override void paint(com.codename1.ui.Graphics g)
        {
#if LOG
            TrackingApp.CN1Extensions.Log("SilverlightPeer.paint; avoid using this method!!!", TrackingApp.CN1Extensions.Level.WARNING);
#endif
            ((NativeGraphics)g.getGraphics()).paint(new PlacePeer((NativeGraphics)g.getGraphics(), SilverlightImplementation.instance.pendingWipe, getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight(), element));
        }

        public override global::System.Object calcPreferredSize()
        {
            int w = 0;
            int h = 0;
            UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
            {
                element.Measure(new Size(1000000, 1000000));
                w = (int)element.DesiredSize.Width;
                h = (int)element.DesiredSize.Height;
            });
            com.codename1.ui.geom.Dimension d = new com.codename1.ui.geom.Dimension();
            d.@this(Math.Max(2, w), Math.Max(2, h));
            return d;
        }
    }

#endif

    class NetworkOperation : java.lang.Object
    {
        private bool responseCompleted;
        private bool postCompleted;
        public HttpWebRequest request;

        public Stream requestStream
        {
            get
            {
                if (postData == null)
                {
                    request.BeginGetRequestStream(PostCallback, request);
                    while (!postCompleted)
                    {
                        System.Threading.Thread.Sleep(5);
                    }
                }
                return postData;
            }
        }

        private Stream postData;

        public HttpWebResponse response
        {
            get
            {
                if (resp == null)
                {
                    if (postData != null)
                    {
                        UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
                        {
                            try
                            {
                                postData.Close();
                            }
                            catch (Exception e) { }
                        });
                    }
                    request.BeginGetResponse(ResponseCallback, request);
                    while (!responseCompleted)
                    {
                        System.Threading.Thread.Sleep(5);
                    }
                    if (resp == null)
                    {
                        global::java.io.IOException io = new global::java.io.IOException();
                        if (error != null)
                        {
                            io.@this(SilverlightImplementation.toJava(error.Message));
                        }
                        else
                        {
                            io.@this(SilverlightImplementation.toJava("Null response"));
                        }
                        throw new global::org.xmlvm._nExceptionAdapter(io);
                    }
                }
                return resp;
            }
        }
        private HttpWebResponse resp;
        private WebException error;

        private void ResponseCallback(IAsyncResult asyncResult)
        {
            try
            {
                resp = (HttpWebResponse)request.EndGetResponse(asyncResult);
            }
            catch (WebException we)
            {
                error = we;
                if (we.Response != null)
                {
                    resp = (HttpWebResponse)we.Response;
                }
            }
            responseCompleted = true;
        }

        private void PostCallback(IAsyncResult asyncResult)
        {
            postData = request.EndGetRequestStream(asyncResult);
            postCompleted = true;
        }
    }

#if __UNUSED

    public class CN1Media : com.codename1.media.Media
    {
        private MediaElement elem;
        private SilverlightPeer peer;
        private bool video;
        private java.lang.Runnable onComplete;

        public CN1Media(string uri, bool video, java.lang.Runnable onComplete)
        {
            System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
            {
                elem = new MediaElement();
                elem.Source = new Uri(uri, UriKind.RelativeOrAbsolute);
                this.video = video;
                this.onComplete = onComplete;
                elem.MediaEnded += elem_MediaEnded;
            });
        }

        public CN1Media(Stream s, string mime, java.lang.Runnable onComplete)
        {
            System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
            {
                elem = new MediaElement();
                elem.SetSource(s);
                video = true;
                this.onComplete = onComplete;
                elem.MediaEnded += elem_MediaEnded;
            });
        }

        void elem_MediaEnded(object sender, RoutedEventArgs e)
        {
            if (onComplete != null)
            {
                com.codename1.ui.Display disp = (com.codename1.ui.Display)com.codename1.ui.Display.getInstance();
                disp.callSerially(onComplete);
            }
        }

        public virtual void play()
        {
            elem.Play();
        }

        public virtual void pause()
        {
            elem.Pause();
        }

        public virtual void cleanup()
        {
            elem = null;
        }

        public virtual int getTime()
        {
            int v = 0;
            using (AutoResetEvent are = new AutoResetEvent(false))
            {
                System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                {
                    v = (int)elem.Position.TotalMilliseconds;
                    are.Set();
                });
                are.WaitOne();
            }
            return v;
        }

        public virtual void setTime(int n1)
        {
            elem.Position = TimeSpan.FromMilliseconds(n1);
        }

        public virtual int getDuration()
        {
            int v = 0;
            using (AutoResetEvent are = new AutoResetEvent(false))
            {
                System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                {
                    v = (int)elem.NaturalDuration.TimeSpan.TotalMilliseconds;
                    are.Set();
                });
                are.WaitOne();
            }
            return v;
        }

        public virtual void setVolume(int n1)
        {
            System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
            {
                elem.Volume = ((double)n1) / 100.0;
            });
        }

        public virtual int getVolume()
        {
            int v = 0;
            using (AutoResetEvent are = new AutoResetEvent(false))
            {
                System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                {
                    v = (int)(elem.Volume * 100.0);
                    are.Set();
                });
                are.WaitOne();
            }
            return v;
        }

        public virtual bool isPlaying()
        {
            bool b = false;
            using (AutoResetEvent are = new AutoResetEvent(false))
            {
                System.Windows.Deployment.Current.Dispatcher.BeginInvoke(() =>
                {
                    b = elem.CurrentState == MediaElementState.Playing || elem.CurrentState == MediaElementState.Buffering;
                    are.Set();
                });
                are.WaitOne();
            }
            return b;
        }

        public virtual global::System.Object getVideoComponent()
        {
            if (peer == null)
            {
                peer = new SilverlightPeer(elem);
            }
            return peer;
        }

        public virtual bool isVideo()
        {
            return video;
        }

        public virtual bool isFullScreen()
        {
            return false;
        }

        public virtual void setFullScreen(bool n1)
        {
        }

        public virtual void setNativePlayerMode(bool n1)
        {
        }

        public virtual bool isNativePlayerMode()
        {
            return false;
        }
    }

    // wipes the content of the canvas to preserve memory. We do this when we know data was removed
    class WipeScreen : OperationPending
    {
        public WipeScreen(NativeGraphics ng)
            : base(ng, null)
        {
        }

        public override void perform(Canvas cl)
        {
            cl.Children.Clear();
            if (SilverlightImplementation.textInputInstance != null)
            {
                cl.Children.Add(SilverlightImplementation.textInputInstance);
            }
        }

        public override void printLogging()
        {
            log("Wiping screen");
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return null;
        }
    }

    class CSharpListHolder : global::java.lang.Object
    {
        public LinkedList<UIElement> ll = new LinkedList<UIElement>();
    }

#endif

    internal class WipeComponent : OperationPending
    {
        public LinkedList<UIElement> ll;
        string componentName;
        public WipeComponent(NativeGraphics ng, LinkedList<UIElement> ll, string componentName)
            : base(ng, null)
        {
            this.ll = new LinkedList<UIElement>();
            foreach (UIElement e in ll)
            {
                this.ll.AddLast(e);
            }
            this.componentName = componentName;
        }

        public override void perform(Canvas cl)
        {
            foreach (UIElement v in ll)
            {
                cl.Children.Remove(v);
            }
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.WipeComponent perform; removed {0} children", ll.Count);
#endif
            //log("Wiped " + ll.Count + " components from " + componentName);
        }

        public Image getExistingImage(CodenameOneImage source)
        {
            if (ll != null)
            {
                //log("Checking image existance for: " + source.name);
                foreach (UIElement uim in ll)
                {
                    if (uim is Image)
                    {
                        Image im = (Image)uim;
                        if (im.Source == source.img)
                        {
                            ll.Remove(im);
                            //log("Image found for: " + source.name);
                            return im;
                        }
                    }
                }
            }
            if (source.imageCache != null && source.imageCache.Parent == null)
            {
                //log("Returning cached image for: " + source.name);
                return source.imageCache;
            }
            else
            {
                //log("Image not found for: " + source.name);
            }
            return null;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return null;
        }

        public override void printLogging()
        {
            string s = "";
            foreach (UIElement uim in ll)
            {
                if (uim is TextBlock)
                {
                    s += "Text:'" + ((TextBlock)uim).Text + "',";
                }
                else
                {
                    s += uim.GetType().Name + ", ";
                }
            }
            log("Wiping component, removing " + ll.Count + " from " + componentName + " containing: [" + s + "]");
        }
    }
}