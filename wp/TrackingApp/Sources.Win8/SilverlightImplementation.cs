//#define __CT_RENDER       // use per-frame rendering; for us probably not necessary and less performant (???)
#define __RE_SPIN           // use spin lock for painst lock instead of normal lock
#define __MI_FIX            // alternate mutable image handling using flushing
#define __MI_SPIN           // use spin lock for flushing mutable image paints instead of normal lock
#define __PIXEL_ITERCOPY    // use int-by-int copy in WriteableBitmap pixel manipulation
//#define __USE_WBMP          // use WritableBitmap as Canvas backend
//#define __CACHE_IMAGE_CONTROL

#region Framework imports
using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Linq;
using System.Net;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Windows;
using System.Windows.Resources;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Media;
using System.Windows.Shapes;
using Microsoft.Devices;
using Microsoft.Phone.Controls;
#endregion

using org.xmlvm;
using net.trekbuddy.wp8;
using InputStreamProxy = net.trekbuddy.wp8.InputStreamWrapper;
using UISynchronizationContext = net.trekbuddy.wp8.ui.UISynchronizationContext;

namespace com.codename1.impl
{
    internal partial class SilverlightImplementation : CodenameOneImplementation //, IServiceProvider
    {
#if __UNUSED
        private static Object PAINT_LOCK = new Object();
#endif
        public static SilverlightImplementation instance;
        public static Canvas cl;
        public static PhoneApplicationPage app;
        private readonly NativeGraphics globalGraphics = new NativeGraphics();
        private NativeFont defaultFont;
        private LocationManager locationManager;
        private int displayWidth, displayHeight/*, fpX, fpY*/;
        private bool isPinch;
#if __CT_RENDER
        private List<OperationPending> ctPaints;
#endif

        public static void setCanvas(PhoneApplicationPage page, Canvas layoutRoot)
        {
            cl = layoutRoot;
            app = page;
        }

#if __UNUSED

        void page_BackKeyPress(object sender, System.ComponentModel.CancelEventArgs e)
        {
            if (getCurrentForm() is global::com.codename1.ui.Canvas) 
                return;
            keyPressed(getBackKeyCode());
            keyReleased(getBackKeyCode());
            e.Cancel = true;
        }

        public new void @this()
        {
            base.@this();
            instance = this;
        }

#endif

        public override bool shouldWriteUTFAsGetBytes()
        {
            return true;
        }

        public override global::System.Object getResourceAsStream(global::java.lang.Class n1, global::java.lang.String n2)
        {
            try
            {
                String uri = toCSharp(n2);
                if (uri.StartsWith("/resources/"))
                {
                    uri = "res/" + uri.Substring("/resources/".Length);
                }
                var uriResource = new Uri(uri, UriKind.Relative);
                var si = Application.GetResourceStream(uriResource);
                return new InputStreamProxy(si.Stream);
            }
            catch (System.Exception err)
            {
                return null;
            }
        }

        public override void init(java.lang.Object n1)
        {
            instance = this;
            UISynchronizationContext.Dispatcher.InvokeSync(() => // justified; was Dispatcher.BeginInvoke(()
            {
                //app.BackKeyPress += page_BackKeyPress;
                //Touch.FrameReported += new TouchFrameEventHandler(Touch_FrameReported); // replaced with page mouse events
                //app.MouseLeftButtonDown += app_MouseLeftButtonDown;
                //app.MouseLeftButtonUp += app_MouseLeftButtonUp;
                //app.MouseMove += app_MouseMove;
                app.ManipulationStarted += app_ManipulationStarted;
                app.ManipulationDelta += app_ManipulationDelta;
                app.ManipulationCompleted += app_ManipulationCompleted;
                //app.SupportedOrientations = SupportedPageOrientation.PortraitOrLandscape; // moved to MainPage.xaml
                app.OrientationChanged += app_OrientationChanged;
                app.SizeChanged += app_SizeChanged;        
#if __CT_RENDER
                CompositionTarget.Rendering += CompositionTarget_Rendering;
#endif
            });
        }

        #region Canvas events handling (mouse, orientation, composite rendering, ...)

#if __CT_RENDER
        void CompositionTarget_Rendering(object sender, EventArgs e)
        {
#if __RE_SPIN
            bool lockTaken = false;
            try
            {
                globalGraphics.nextPaintsLock.TryEnter(ref lockTaken);
                if (lockTaken)
                {
                    if (ctPaints != null)
                    {
                        paintCurrentImpl(ctPaints);
                        ctPaints = null;
#if LOG
                        TrackingApp.CN1Extensions.Log("Impl.rendering done");
#endif
                    }
                }
            }
            finally
            {
                if (lockTaken) globalGraphics.nextPaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else // !__RE_SPIN
            lock (globalGraphics.pendingPaintsList)
            {
                if (ctPaints != null)
                {
                    paintCurrentImpl(ctPaints);
                    ctPaints = null;
                }
            }
#endif // !__RE_SPIN
        }
#endif // __CT_RENDER

#if __OBSOLETE

        void app_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            Point p = e.GetPosition(cl/*TrackingApp.App.RootFrame*/);
            System.Diagnostics.Debug.WriteLine("Impl.mouseDown; {0}", p);
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mouse down; {0}", p);
#endif
            cz.kruch.track.ui.Desktop._fscreen.pointerPressed(fpX = (int)p.X, fpY = (int)p.Y);
            e.Handled = true;
        }

        void app_MouseMove(object sender, MouseEventArgs e)
        {
            Point p = e.GetPosition(cl/*TrackingApp.App.RootFrame*/);
            System.Diagnostics.Debug.WriteLine("Impl.mouseMove; {0}", p);
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mouse move; {0}", p);
#endif
            if ((int)p.X != fpX || (int)p.Y != fpY)
            {
                cz.kruch.track.ui.Desktop._fscreen.pointerDragged((int)p.X, (int)p.Y);
            }
        }

        void app_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            Point p = e.GetPosition(cl/*TrackingApp.App.RootFrame*/);
            System.Diagnostics.Debug.WriteLine("Impl.mouseUp; {0}", p);
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mouse up; {0}", p);
#endif
            cz.kruch.track.ui.Desktop._fscreen.pointerReleased((int)p.X, (int)p.Y);
            e.Handled = true;
        }

#endif // __OBSOLETE

        void app_ManipulationStarted(object sender, ManipulationStartedEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("Impl.manipulationStarted; {0}", e.ManipulationOrigin);
            cz.kruch.track.ui.Desktop._fscreen.pointerPressed((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
            e.Handled = true;
        }

        void app_ManipulationDelta(object sender, ManipulationDeltaEventArgs e)
        {
            bool isPinch = e.PinchManipulation is PinchManipulation;
            System.Diagnostics.Debug.WriteLine("Impl.manipulationDelta; {0}/{1}, origin {2}, pinch? {3}", 
                e.CumulativeManipulation.Scale, e.DeltaManipulation.Translation, e.ManipulationOrigin, isPinch);
            if (isPinch && !this.isPinch)
            {
                this.isPinch = true;
                cz.kruch.track.ui.Desktop._fscreen.pointerPressed((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
            } 
            else if (!isPinch && this.isPinch) 
            {
                this.isPinch = false;
                cz.kruch.track.ui.Desktop._fscreen.pointerReleased((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
            }
            else if (isPinch && this.isPinch) 
            {
                float scaleFactor = (float)Math.Max(0.1, Math.Min((e.CumulativeManipulation.Scale.X + e.CumulativeManipulation.Scale.Y) / 2, 5.0));
                cz.kruch.track.ui.Desktop._fscreen.pointerScaled((int)(scaleFactor * 1000), 0);
            }
            else
            {
                cz.kruch.track.ui.Desktop._fscreen.pointerDragged((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
            }
            e.Handled = true;
        }

        void app_ManipulationCompleted(object sender, ManipulationCompletedEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("Impl.manipulationCompleted; {0}/{1}, origin {2}, pinch? {3}",
                e.TotalManipulation.Scale, e.TotalManipulation.Translation, e.ManipulationOrigin, isPinch);
            if (this.isPinch)
            {
                this.isPinch = false;
                // HACK!!! release two pointers
                cz.kruch.track.ui.Desktop._fscreen.pointerReleased((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
                cz.kruch.track.ui.Desktop._fscreen.pointerReleased((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
            }
            else
            {
                cz.kruch.track.ui.Desktop._fscreen.pointerReleased((int)e.ManipulationOrigin.X, (int)e.ManipulationOrigin.Y);
            }
            e.Handled = true;
        }

        void app_OrientationChanged(object sender, OrientationChangedEventArgs e)
        {
            displayHeight = (int)cl.ActualHeight;
            displayWidth = (int)cl.ActualWidth;
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.app_OrientationChanged; {0}x{1}", cl.ActualWidth, cl.ActualHeight);
#endif
            if (cz.kruch.track.ui.Desktop._fscreen != null) 
            {
                cz.kruch.track.ui.Desktop._fscreen.sizeChanged(displayWidth, displayHeight);
            }
        }

        void app_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            displayHeight = (int)cl.ActualHeight;
            displayWidth = (int)cl.ActualWidth;
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.app_SizeChanged; {0}x{1}", cl.ActualWidth, cl.ActualHeight);
#endif
            if (cz.kruch.track.ui.Desktop._fscreen != null)
            {
                cz.kruch.track.ui.Desktop._fscreen.sizeChanged(displayWidth, displayHeight);
            }
        }

        #endregion

        public override global::System.Object getProperty(global::java.lang.String n1, global::java.lang.String n2)
        {
            // TODO
            return base.getProperty(n1, n2);
        }

        public override void exitApplication()
        {
            Application.Current.Terminate();
        }

        public override global::System.Object execute(java.lang.String n1, global::org.xmlvm._nArrayAdapter<global::System.Object> n2)
        {
            return TrekbuddyExtensions.execute(this, n1, n2 == null ? null : n2.getCSharpArray());
        }

        public override void callSerially(java.lang.Runnable n1)
        {
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.callSerially; {0}", n1);
#endif
            UISynchronizationContext.Dispatcher.InvokeAsync(n1.run);
            /*
             * why not run directly? how should it really behave?
             */
        }

        public override bool hasExternalCard()
        {
            return CardStorage.Available;
        }

        public override int getDisplayWidth()
        {
            updateDimensions();
            return displayWidth;
        }

        public override int getDisplayHeight()
        {
            updateDimensions();
            return displayHeight;
        }

        private void updateDimensions()
        {
            if (displayWidth == 0 || displayHeight == 0)
            {
                UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
                {
                    displayWidth = (int)cl.ActualWidth;
                    displayHeight = (int)cl.ActualHeight;
                });
#if LOG
                TrackingApp.CN1Extensions.Log("Impl.updateDimensions; {0}x{1}", displayWidth, displayHeight);
#endif
            }
        }

        public override void vibrate(int n1)
        {
            VibrateController vc = VibrateController.Default;
            vc.Start(TimeSpan.FromMilliseconds(n1));
        }

        public override void systemOut(global::java.lang.String n1)
        {
            System.Diagnostics.Debug.WriteLine(toCSharp(n1));
        }

        public override void flushGraphics(int n1, int n2, int n3, int n4)
        {
            // TODO
/*
            if (n1 == 0 && n2 == 0 && n3 == getDisplayWidth() && n4 == getDisplayHeight())
            {
                flushGraphics();
                return;
            }
            List<OperationPending> currentPaints = new List<OperationPending>();
            currentPaints.AddRange(globalGraphics.pendingPaints);
            globalGraphics.pendingPaints.Clear();
            paintCurrent(currentPaints, n1, n2, n3, n4);
*/
            flushGraphics();
        }

        public override void flushGraphics()
        {
#if LOG
            long t0 = System.Environment.TickCount;
            TrackingApp.CN1Extensions.Log("Impl.flushGraphics; paints: {0}", globalGraphics.pendingPaintsList.Count);
#endif
            if (globalGraphics.pendingPaintsList.Count > 0)
            {
                List<OperationPending> currentPaints = new List<OperationPending>();
#if __RE_SPIN
                bool lockTaken = false;
                try
                {
                    globalGraphics.nextPaintsLock.Enter(ref lockTaken);
                    currentPaints.AddRange(globalGraphics.pendingPaintsList);
                    globalGraphics.pendingPaintsList.Clear();
#if __CT_RENDER
                    ctPaints = currentPaints;
#endif
                }
                finally
                {
                    if (lockTaken) globalGraphics.nextPaintsLock.Exit(false);
                    System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
                }
#else
                lock (globalGraphics.pendingPaintsList)
                {
                    currentPaints.AddRange(globalGraphics.pendingPaintsList);
                    globalGraphics.pendingPaintsList.Clear();
#if __CT_RENDER
                    ctPaints = currentPaints;
#endif
                }
#endif
#if !__CT_RENDER
                if (UISynchronizationContext.Dispatcher.CheckAccess()) 
                {
                    paintCurrentImpl(currentPaints);
                }
                else
                {
                    paintCurrent(currentPaints);
                }
#endif
            }
#if LOG
            long t1 = System.Environment.TickCount;
            TrackingApp.CN1Extensions.Log("Impl.flushGraphics; finished took {0} ms", (t1 - t0));
#endif
        }

#if __UNUSED

        private void wipe()
        {
#if __RE_SPIN
            bool lockTaken = false;
            try
            {
                globalGraphics.nextPaintsLock.Enter(ref lockTaken);
                globalGraphics.pendingPaintsList.Clear();
                globalGraphics.pendingPaintsList.Add(new WipeScreen(globalGraphics));
            }
            finally
            {
                if (lockTaken) globalGraphics.nextPaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else
            lock (globalGraphics.pendingPaintsList)
            {
                globalGraphics.pendingPaintsList.Clear();
                globalGraphics.pendingPaintsList.Add(new WipeScreen(globalGraphics));
            }
#endif
        }

#endif

        private void paintCurrent(List<OperationPending> currentPaints)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                paintCurrentImpl(currentPaints);
            });
        }

        private void paintCurrentImpl(List<OperationPending> currentPaints)
        {
            Canvas cl = SilverlightImplementation.cl;
            int count = currentPaints.Count;
            for (int iter = 0; iter < count; iter++)
            {
                currentPaints[iter].perform(cl);
            }
        }

        #region CN1 Image methods implementation (MIDP Image)

        public override void getRGB(java.lang.Object n1, _nArrayAdapter<int> n2, int n3, int n4, int n5, int n6, int n7)
        {
            CodenameOneImage cn = (CodenameOneImage)n1;
            if (cn.img != null) {
                UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
                {
                    WriteableBitmap wb = new WriteableBitmap((BitmapSource)cn.img);
                    int[] p = wb.Pixels;
                    int[] dst = n2.getCSharpArray();
#if __PIXEL_ITERCOPY
                    for (int N = p.Length, iter = 0; iter < N; iter++)
                    {
                        dst[iter] = p[iter];
                    }
#else
                    Buffer.BlockCopy(p, 0, dst, 0, p.Length); // TODO may not work correctly
#endif
                });
                return;
            }
            throw new global::org.xmlvm._nExceptionAdapter(new global::java.lang.NullPointerException());
        }

        public override void setImageName(global::java.lang.Object n1, global::java.lang.String n2)
        {
            if (n2 != null)
            {
                ((CodenameOneImage)n1).name = toCSharp(n2);
            }
        }

        public override object createImage(_nArrayAdapter<int> n1, int n2, int n3)
        {
            CodenameOneImage ci = null;
            UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
            {
                WriteableBitmap wb = new WriteableBitmap(n2, n3);
                int[] p = wb.Pixels;
                int[] src = n1.getCSharpArray();
#if __PIXEL_ITERCOPY
                for (int N = p.Length, iter = 0; iter < N; iter++)
                {
                    p[iter] = src[iter];
                }
#else
                Buffer.BlockCopy(src, 0, p, 0, p.Length); // TODO may not work correctly
#endif
                CodenameOneImage cim = new CodenameOneImage();
                cim.@this();
                cim.img = wb;
                cim.width = n2;
                cim.height = n3;
                ci = cim;
            });
            return ci;
        }

        public override object createImage(java.lang.String n1)
        {
            if (n1.startsWith("file:".toJava()))
            {
                createImage((java.io.InputStream)openFileInputStream(n1));
            }
            return createImage((global::java.io.InputStream)getResourceAsStream(null, n1));
        }

        public override object createImage(java.io.InputStream n1)
        {
            object result = null;
            InputStreamProxy proxy = n1 as InputStreamProxy;
            if (proxy == null)
            {
#if LOG
                long t0 = System.Environment.TickCount;
#endif
                Stream s = toStream(n1);
#if LOG
                long t1 = System.Environment.TickCount;
#endif
                result = createImage(s);
#if LOG
                long t2 = System.Environment.TickCount;
                TrackingApp.CN1Extensions.Log("Impl.create image from java stream; read: {0} ms; decode: {1} ms [{2}]", (t1 - t0), (t2 - t1), n1);
#endif
            }
            else
            {
#if LOG
                long t1 = System.Environment.TickCount;
#endif
                result = createImage(proxy.Stream);
#if LOG
                long t2 = System.Environment.TickCount;
                TrackingApp.CN1Extensions.Log("Impl.create image from proxy stream: {0} ms [{1}]", (t2 - t1), proxy.Stream);
#endif
            }
            return result;
        }

        public override global::System.Object createImage(global::org.xmlvm._nArrayAdapter<sbyte> n1, int n2, int n3)
        {
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.create image from array");
#endif
            return createImage(new MemoryStream(toByteArray(n1.getCSharpArray()), n2, n3, false, true));
        }

        private static global::System.Object createImage(Stream stream)
        {
#if LOG
            long t0 = System.Environment.TickCount;
            TrackingApp.CN1Extensions.Log("Impl.create image from stream [{0}]", stream);
#endif
            BitmapSource bi = null;
            int bw = 0, bh = 0;
            bool tryPng = stream is MemoryStream;
            if (tryPng)
            {
                tryPng = TrackingApp.ImageHelper.TryExtractPngDimensions(stream, out bw, out bh);
#if LOG
                if (tryPng)
                {
                    long te = System.Environment.TickCount;
                    TrackingApp.CN1Extensions.Log("Impl.create image extracted w x h in {0} ms: {1} x {2}", (te - t0), bw, bh);
                }
#endif
            }
            UISynchronizationContext.Dispatcher.InvokeSync(() => // justified
            {
                BitmapImage bitmapImage = new BitmapImage();
                bitmapImage.CreateOptions = BitmapCreateOptions.IgnoreImageCache;
                if (tryPng)
                {
                    bitmapImage.CreateOptions |= BitmapCreateOptions.BackgroundCreation | BitmapCreateOptions.DelayCreation;
                }
                bitmapImage.SetSource(stream);
                if (!tryPng) 
                {
                    bw = bitmapImage.PixelWidth;
                    bh = bitmapImage.PixelHeight;
                }
#if __USE_WBMP
                bi = new WriteableBitmap(bitmapImage);
#else
                bi = bitmapImage;
#endif
            });
#if LOG
            long t1 = System.Environment.TickCount;
            TrackingApp.CN1Extensions.Log("Impl.create image from native stream; {0} ms [{1}]", (t1 - t0), stream);
#endif
            CodenameOneImage ci = new CodenameOneImage();
            ci.@this();
            ci.img = bi;
            ci.width = bw;
            ci.height = bh;
            return ci;
        }

        #endregion

        #region CN1 UI and Graphics API

        public override object createMutableImage(int n1, int n2, int n3)
        {
            CodenameOneMutableImage ci = new CodenameOneMutableImage();
            ci.@this();
            ci.width = n1;
            ci.height = n2;
            ci.opaque =((n3 & 0xff000000) == 0xff000000);
            if((n3 & 0xff000000) != 0) {
                ci.graphics.color = n3;
                ci.imagePaints.Add(new FillRect(ci.graphics, pendingWipe, 0, 0, n1, n2));
            }
            return ci;
        }

        public override int getImageWidth(java.lang.Object n1)
        {
            return ((CodenameOneImage)n1).getImageWidth();
        }

        public override int getImageHeight(java.lang.Object n1)
        {
            return ((CodenameOneImage)n1).getImageHeight();
        }

        public override object scale(java.lang.Object n1, int n2, int n3)
        {
            CodenameOneImage ci = new CodenameOneImage();
            ci.@this();
            CodenameOneImage source = (CodenameOneImage)n1;
            ci.img = source.img;
            ci.width = n2;
            ci.height = n3;
            return ci;
        }

        public override int getSoftkeyCount()
        {
            return 0;
        }

        public override object getSoftkeyCode(int n1)
        {
            return null;
        }

        public override int getClearKeyCode()
        {
            return 0;
        }

        public override int getBackspaceKeyCode()
        {
            return 0;
        }

        public override int getBackKeyCode()
        {
            return -20;
        }

        public override int getGameAction(int n1)
        {
            return 0;
        }

        public override int getKeyCode(int n1)
        {
            return 0;
        }

        public override bool isTouchDevice()
        {
            return true;
        }

        public override int getColor(java.lang.Object n1)
        {
            return ((NativeGraphics)n1).color;
        }

        public override void setColor(java.lang.Object n1, int n2)
        {
            ((NativeGraphics)n1).color = n2;
        }

        public override void setAlpha(java.lang.Object n1, int n2)
        {
            ((NativeGraphics)n1).alpha = n2;
        }

        public override int getAlpha(java.lang.Object n1)
        {
            return ((NativeGraphics)n1).alpha;
        }

        public override void setNativeFont(java.lang.Object n1, java.lang.Object n2)
        {
            ((NativeGraphics)n1).font = (NativeFont)n2;
        }

        public override int getClipX(java.lang.Object n1)
        {
            return ((NativeGraphics)n1).clipX;
        }

        public override int getClipY(java.lang.Object n1)
        {
            return ((NativeGraphics)n1).clipY;
        }

        public override int getClipWidth(java.lang.Object n1)
        {
            return ((NativeGraphics)n1).clipW;
        }

        public override int getClipHeight(java.lang.Object n1)
        {
            return ((NativeGraphics)n1).clipH;
        }

        public override void setClip(java.lang.Object n1, int n2, int n3, int n4, int n5)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            bool unsetClip = false;
            if (ng == globalGraphics)
            {
                unsetClip = n2 == 0 && n3 == 0 && n4 == getDisplayWidth() && n5 == getDisplayHeight();
            }
            else if (ng is MutableImageGraphics)
            {
                MutableImageGraphics ing = ng as MutableImageGraphics;
                unsetClip = n2 == 0 && n3 == 0 && n4 == ing.Width && n5 == ing.Height;
            }
            if (unsetClip)
            {
                ng.clipSet = false;
            }
            else
            {
                ng.clipSet = true;
                ng.clipX = n2;
                ng.clipY = n3;
                ng.clipW = n4;
                ng.clipH = n5;
            }
        }

        public override void clipRect(java.lang.Object n1, int n2, int n3, int n4, int n5)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.clipSet = true;
            Microsoft.Xna.Framework.Rectangle a = new Microsoft.Xna.Framework.Rectangle(ng.clipX, ng.clipY, ng.clipW, ng.clipH);
            Microsoft.Xna.Framework.Rectangle b = new Microsoft.Xna.Framework.Rectangle(n2, n3, n4, n5);
            Microsoft.Xna.Framework.Rectangle r = Microsoft.Xna.Framework.Rectangle.Intersect(a, b);
            setClip(n1, r.X, r.Y, r.Width, r.Height);
        }

        public override void drawLine(java.lang.Object n1, int x1, int y1, int x2, int y2)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawLine(ng, pendingWipe, x1, y1, x2, y2));
        }

        public override void fillRect(java.lang.Object n1, int x, int y, int w, int h)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            if (ng.alpha > 0)
            {
#if __UNUSED
                // wipe the screen if the operation will clear the whole screen
                int dw = getDisplayWidth();
                int dh = getDisplayHeight();
                if (ng == globalGraphics && x == 0 && y == 0 && w == dw && h == dh && ng.clipX == 0 && ng.clipY == 0 && ng.clipW == w &&
                    ng.clipH == h && ng.alpha == 255)
                {
                    Console.Error.WriteLine("Avoid using wipe!");
                    wipe();
                }
#endif

                ng.paint(new FillRect(ng, pendingWipe, x, y, w, h));
            }
        }

        public override void fillTriangle(java.lang.Object n1, int x1, int y1, int x2, int y2, int x3, int y3)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new FillTriangle(ng, pendingWipe, x1, y1, x2, y2, x3, y3));
        }

        public override void drawRect(java.lang.Object n1, int x, int y, int w, int h)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawRect(ng, pendingWipe, x, y, w, h, 1));
        }

        public override void drawRect(java.lang.Object n1, int x, int y, int w, int h, int t)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawRect(ng, pendingWipe, x, y, w, h, t));
        }

        public override void drawRoundRect(java.lang.Object n1, int x, int y, int w, int h, int arcW, int arcH)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawRoundRect(ng, pendingWipe, x, y, w, h, arcW, arcH, false));
        }

        public override void fillRoundRect(java.lang.Object n1, int x, int y, int w, int h, int arcW, int arcH)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawRoundRect(ng, pendingWipe, x, y, w, h, arcW, arcH, true));
        }

        public override void fillArc(java.lang.Object n1, int n2, int n3, int n4, int n5, int n6, int n7)
        {
        }

        public override void drawArc(java.lang.Object n1, int n2, int n3, int n4, int n5, int n6, int n7)
        {
        }

        public override void drawString(java.lang.Object n1, java.lang.String n2, int n3, int n4)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawString(ng, pendingWipe, f(ng.font), toCSharp(n2), n3, n4));
        }

        public override void drawImage(java.lang.Object n1, java.lang.Object n2, int x, int y)
        {
            NativeGraphics ng = (NativeGraphics)n1;
#if __UNUSED
            // wipe the screen if the operation will clear the whole screen
            int dw = getDisplayWidth();
            int dh = getDisplayHeight();
            if (ng == globalGraphics && x == 0 && y == 0 && img.width == dw && img.height == dh && ng.clipX == 0 && ng.clipY == 0 && ng.clipW == img.width &&
                ng.clipH == img.height && ng.alpha == 255)
            {
                wipe(); // flickers
            }
#endif
            if (n2 is CodenameOneMutableImage)
            {
#if !__MI_FIX
                CodenameOneImage img = (CodenameOneImage)n2;
                int cx = getClipX(ng);
                int cy = getClipY(ng);
                int cw = getClipWidth(ng);
                int ch = getClipHeight(ng);
                clipRect(ng, cx, cy, img.width, img.height);
                long t0 = System.Environment.TickCount;
# if LOG
                com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.drawMutableImage: " + ((CodenameOneMutableImage)n2).imagePaints.Count + " paints"));
# endif
//                bool lockTaken = false;
//                try
//                {
//                    ((CodenameOneMutableImage)n2).paintsLock.Enter(ref lockTaken);
                lock (((CodenameOneMutableImage)n2).imagePaints)
                {
                    foreach (OperationPending p in ((CodenameOneMutableImage)n2).imagePaints)
                    {
                        ng.paint(p.clone(pendingWipe, x, y));
                    }
                }
//                }
//                finally
//                {
//                    if (lockTaken) ((CodenameOneMutableImage)n2).paintsLock.Exit(false);
//                }
                long t1 = System.Environment.TickCount;
# if LOG
                com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.drawMutableImage took " + (t1 - t0) + " ms"));
# endif
                setClip(ng, cx, cy, cw, ch);
#else // __MI_FIX
                ng.paint(new DrawMutableImage(ng, pendingWipe, n2, x, y));
#endif // __MI_FIX
            }
            else
            {
                ng.paint(new DrawImage(ng, pendingWipe, n2, x, y));
            }
        }

#if __UNUSED
        public override bool areMutableImagesFast()
        {
            return false;
        }

        public override bool shouldPaintBackground()
        {
            return false;
        }

        public override bool isScaledImageDrawingSupported()
        {
            return true;
        }
#endif

        public override void drawImage(java.lang.Object n1, java.lang.Object n2, int x, int y, int w, int h)
        {
            NativeGraphics ng = (NativeGraphics)n1;
#if __UNUSED
            // wipe the screen if the operation will clear the whole screen
            int dw = getDisplayWidth();
            int dh = getDisplayHeight();
            if (ng == globalGraphics && x == 0 && y == 0 && w == dw && h == dh && ng.clipX == 0 && ng.clipY == 0 && ng.clipW == w &&
                ng.clipH == h && ng.alpha == 255)
            {
                wipe();
            }
#endif
            ng.paint(new DrawImage(ng, pendingWipe, n2, x, y, w, h));
        }

        public override void drawRegion(java.lang.Object n1, java.lang.Object n2, int x_src, int y_src, int w, int h, int transform, int x_dest, int y_dest)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawImageRegion(ng, pendingWipe, n2, x_src, y_src, w, h, transform, x_dest, y_dest));
        }

        public override void drawRGB(java.lang.Object n1, _nArrayAdapter<int> rgb, int offset, int x, int y, int w, int h, bool n8)
        {
            CodenameOneImage ci = new CodenameOneImage();
            ci.@this();
            WriteableBitmap wb = new WriteableBitmap(w, h);
            int[] p = wb.Pixels;
            int[] src = rgb.getCSharpArray();
#if __PIXEL_ITERCOPY
            for (int N = p.Length, iter = 0; iter < N; iter++)
            {
                p[iter] = src[iter];
            }
#else
            Buffer.BlockCopy(src, 0, p, 0, p.Length); // TODO may not work correctly
#endif
            ci.img = wb;
            ci.width = w;
            ci.height = h;

            NativeGraphics ng = (NativeGraphics)n1;
            ng.paint(new DrawImage(ng, pendingWipe, ci, x, y));
        }

        public override object getNativeGraphics()
        {
            return globalGraphics;
        }

        public override object getNativeGraphics(java.lang.Object n1)
        {
            CodenameOneMutableImage img = (CodenameOneMutableImage)n1;
            MutableImageGraphics graphics = img.graphics;
            graphics.clipSet = false;
            graphics.clipW = img.width;
            graphics.clipH = img.height;
            return graphics;
        }

        public override bool isOpaque(global::com.codename1.ui.Image n1, global::java.lang.Object n2)
        {
            CodenameOneImage c = (CodenameOneImage)n2;
            if (c is CodenameOneMutableImage)
            {
                return false;
            }
            // TODO!
            return true;
        }

        public override void setStrokeStyle(java.lang.Object n1, int n2)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.strokeStyle = n2;
        }

        public override void setStrokeWidth(java.lang.Object n1, int n2)
        {
            NativeGraphics ng = (NativeGraphics)n1;
            ng.strokeWidth = n2;
        }

        #endregion

        public override object getPlatformName()
        {
            return "win".toJava();
        }

        public override global::System.Object getLocalizationManager()
        {
            throw new NotSupportedException();
#if __UNUSED
            //XMLVM_BEGIN_WRAPPER[com.codename1.impl.SilverlightImplementation: com.codename1.l10n.L10NManager getLocalizationManager()]
            global::org.xmlvm._nElement _r0;
            _r0.i = 0;
            _r0.l = 0;
            _r0.f = 0;
            _r0.d = 0;
            global::System.Object _r0_o = null;
            global::org.xmlvm._nElement _r1;
            _r1.i = 0;
            _r1.l = 0;
            _r1.f = 0;
            _r1.d = 0;
            global::System.Object _r1_o = null;
            global::org.xmlvm._nExceptionAdapter _ex = null;
            _r1_o = this;
            _r0_o = new global::com.codename1.impl.SilverlightImplementation_2L10NManagerImpl();
            ((global::com.codename1.impl.SilverlightImplementation_2L10NManagerImpl)_r0_o).@this();
            return (global::com.codename1.l10n.L10NManager)_r0_o;
            //XMLVM_END_WRAPPER[com.codename1.impl.SilverlightImplementation: com.codename1.l10n.L10NManager getLocalizationManager()]
#endif
        }

        public override object getLocationManager()
        {
            if (locationManager == null)
            {
                locationManager = new LocationManager();
            }
            return locationManager;
        }
    }

    #region Pending operations (DrawXXX, FillXXX, ...)

    internal abstract class OperationPending
    {
        protected readonly static BitmapCache CACHE_MODE = new BitmapCache();
#if BENCHMARK
        int tick;
#endif
        public int clipX, clipY, clipW, clipH;
        public bool clipSet;
        /*
        protected LinkedList<UIElement> componentPaints, containerPaints;
        */
        protected WipeComponent pendingWipe;

        public OperationPending(NativeGraphics gr, WipeComponent pendingWipe)
        {
            clipSet = gr.clipSet;
            clipX = gr.clipX;
            clipY = gr.clipY;
            clipW = gr.clipW;
            clipH = gr.clipH;
            /*
            componentPaints = SilverlightImplementation.currentPaintDestination;
            containerPaints = SilverlightImplementation.currentPaintContainer;
            */
            this.pendingWipe = pendingWipe;
        }

        protected OperationPending(OperationPending p, WipeComponent w)
        {
            clipX = p.clipX;
            clipY = p.clipY;
            clipW = p.clipW;
            clipH = p.clipH;
            clipSet = p.clipSet;
            pendingWipe = w;
        }
        /*
        protected void add(UIElement uim)
        {
            if (componentPaints != null)
            {
                componentPaints.AddLast(uim);
            }
            if (containerPaints != null)
            {
                containerPaints.AddLast(uim);
            }
        }
        */
        protected void updateClip(FrameworkElement i, int x, int y)
        {
            if (clipSet)
            {
                i.Clip = new RectangleGeometry()
                {
                    Rect = new Rect(clipX - x, clipY - y, clipW, clipH)
                };
            }
        }

        public string clipString()
        {
            if (clipSet)
            {
                return "[clip x: " + clipX + " y: " + clipY + " w: " + clipW + " h: " + clipH + "]";
            }
            return "[no clipping]";
        }

        public virtual bool isDrawOperation()
        {
            return true;
        }

        public virtual void prerender()
        {
        }

#if BENCHMARK
        public void performBench(Canvas cl)
        {
            tick = System.Environment.TickCount;
            perform(cl);
        }
#endif

        public abstract void perform(Canvas cl);

#if __USE_WBMP
        public virtual void perform(WriteableBitmap wbmp)
        {
        }
#endif

        public void log(String s)
        {
            System.Diagnostics.Debug.WriteLine(s);
        }

#if BENCHMARK
        public void printBench()
        {
            System.Diagnostics.Debug.WriteLine(GetType().Name + " took " + (System.Environment.TickCount - tick));
        }
#endif

        public abstract OperationPending clone(WipeComponent w, int translateX, int translateY);

        public abstract void printLogging();
    }

    class DrawImage : OperationPending
    {
        protected java.lang.Object image;
        protected int x;
        protected int y;
        protected int w;
        protected int h;
        int alpha;
        public DrawImage(NativeGraphics ng, WipeComponent pendingWipe, java.lang.Object image, int x, int y)
            : base(ng, pendingWipe)
        {
            this.image = image;
            this.x = x;
            this.y = y;
            this.w = -1;
            this.alpha = ng.alpha;
        }

        public DrawImage(NativeGraphics ng, WipeComponent pendingWipe, java.lang.Object image, int x, int y, int w, int h)
            : base(ng, pendingWipe)
        {
            this.image = image;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.alpha = ng.alpha;
        }

        protected DrawImage(DrawImage p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.image = p.image;
            this.x = p.x + translateX;
            this.y = p.y + translateY;
            this.w = p.w;
            this.h = p.h;
            this.alpha = p.alpha;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawImage(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            CodenameOneImage img = (CodenameOneImage)image;
            if (img.img != null)
            {
                Image i = null;
                if (pendingWipe != null)
                {
#if LOG
                    TrackingApp.CN1Extensions.Log("Impl.DrawImage perform; pending wipe!!!", TrackingApp.CN1Extensions.Level.WARNING);
#endif
                    i = pendingWipe.getExistingImage(img);
                    if (i == null)
                    {
#if LOG
                        TrackingApp.CN1Extensions.Log("Impl.DrawImage perform; pending wipe new image");
#endif
                        i = new Image();
                        i.Source = img.img;
                        i.Opacity = ((double)alpha) / 255.0;
                    }
                    else
                    {
#if LOG
                        TrackingApp.CN1Extensions.Log("Impl.DrawImage perform; pending wipe existing image: {0}", i);
#endif
                        cl.Children.Remove(i);
                    }
                }
                else
                {
#if __CACHE_IMAGE_CONTROL
                    if (img.imageCache == null)
                    {
#endif
                        i = new Image();
                        i.Source = img.img;
                        i.Opacity = ((double)alpha) / 255.0;
#if __CACHE_IMAGE_CONTROL
                    }
                    else
                    {
                        i = img.imageCache;
                        if (i.Parent is Panel)
                        {
                            (i.Parent as Panel).Children.Remove(i);
                        }
                    }
#endif
                }
                Canvas.SetLeft(i, x);
                Canvas.SetTop(i, y);
                updateClip(i, x, y);
                if (w > 0)
                {
                    i.Width = w;
                    i.Height = h;
                }
                else
                {
                    i.Width = img.width;
                    i.Height = img.height;
                }
                //i.CacheMode = CACHE_MODE; // carefull!!!
                //add(i);
                cl.Children.Add(i);
#if __CACHE_IMAGE_CONTROL
                img.imageCache = i;
#endif
#if LOG_RENDER
                com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.DrawImage perform; i: " + x + "," + y + " " + i.Width + "x" + i.Height + "; img: " + img.getImageWidth() + "x" + img.getImageHeight()));
#endif
            }
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            CodenameOneImage img = (CodenameOneImage)image;
            if (img.img != null)
            {
                WriteableBitmapExtensions.BlendMode blendMode = img.OpaqueHint ? WriteableBitmapExtensions.BlendMode.None : WriteableBitmapExtensions.BlendMode.Alpha;
                if (clipSet)
                {
                    wbmp.Blit(new Rect(clipX, clipY, clipW, clipH), (WriteableBitmap)img.img, new Rect(clipX - x, clipY - y, clipW, clipH), blendMode);
                }
                else
                {
                    wbmp.Blit(new Rect(x, y, img.width, img.height), (WriteableBitmap)img.img, new Rect(0, 0, img.width, img.height), blendMode);
                }
            }
        }
#endif

        public override void printLogging()
        {
            CodenameOneImage img = (CodenameOneImage)image;
            bool isNull = img.img == null;
            log("Draw image x: " + x + " y: " + y +
                " w: " + w + " h: " + h + " image null: " + isNull + clipString());
        }
    }

#if __MI_FIX

    class DrawMutableImage : DrawImage
    {
        public DrawMutableImage(NativeGraphics ng, WipeComponent pendingWipe, java.lang.Object image, int x, int y)
            : base(ng, pendingWipe, image, x, y)
        {
        }

        public DrawMutableImage(NativeGraphics ng, WipeComponent pendingWipe, java.lang.Object image, int x, int y, int w, int h)
            : base(ng, pendingWipe, image, x, y, w, h)
        {
        }

        protected DrawMutableImage(DrawMutableImage p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe, translateX, translateY)
        {
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            throw new InvalidOperationException("should never be called");
        }

#if !__USE_WBMP

        public override void perform(Canvas cl)
        {
            CodenameOneMutableImage img = (CodenameOneMutableImage)image;
            if (img.imagePaints.Count == 0) 
                return;
            Canvas i = new Canvas();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y);
            updateClip(i, x, y);
            if (w > 0)
            {
                i.Width = w;
                i.Height = h;
            }
            else
            {
                i.Width = img.width;
                i.Height = img.height;
            }
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.DrawMutableImage perform; paints: {0}", img.imagePaints.Count);
#endif
#if __MI_SPIN
            bool lockTaken = false;
            try
            {
                img.imagePaintsLock.Enter(ref lockTaken);
                foreach (OperationPending p in img.imagePaints)
                {
                    p.perform(i);
                }
            }
            finally
            {
                if (lockTaken) img.imagePaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else // !__MI_SPIN
            lock (img.imagePaints)
            {
                foreach (OperationPending p in img.imagePaints)
                {
                    p.perform(i);
                }
            }
#endif // !__MI_SPIN
            foreach (UIElement e in i.Children)
            {
                if (e is Button) // on-screen menu
                    continue;
                e.IsHitTestVisible = false;
            }
            //i.IsHitTestVisible = false; // not needed for top-level Canvas and also breaks button input
            //add(i);
            cl.Children.Clear();
            cl.Children.Add(i);
/* Simpler is just to clear all children, see above. And with CacheMode, it (not keeping old canvas) reduces memory usage.
#if __CT_RENDER
            if (img.cl0 != null)
            {
#if LOG
                TrackingApp.CN1Extensions.Log("Impl.DrawMutableImage perform; remove cl0 from cl");
#endif
                cl.Children.Remove(img.cl0);
            }
#endif // __CT_RENDER
#if __CT_RENDER
            img.cl0 = i;
#else
            if (img.cl1 != null)
            {
                cl.Children.Remove(img.cl1);
            }
            img.cl1 = img.cl0;
            img.cl0 = i;
#endif
*/
        }

#else // __USE_WBMP

        public override void perform(Canvas cl)
        {
            CodenameOneMutableImage img = (CodenameOneMutableImage)image;
            if (img.imagePaints.Count == 0) return;
            Image i = new Image();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y);
            updateClip(i, x, y);
            if (w > 0)
            {
                i.Width = w;
                i.Height = h;
            }
            else
            {
                i.Width = img.width;
                i.Height = img.height;
            }
            if (img.wbmp == null)
            {
                img.wbmp = new WriteableBitmap((int)i.Width, (int)i.Height);
            }
            i.Source = img.wbmp;
            i.CacheMode = CACHE_MODE;
            cl.Children.Add(i);
#if LOG
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.DrawMutableImage perform; paints: " + img.imagePaints.Count));
#endif
            //using (var bitmapContext = img.wbmp.GetBitmapContext())
            {
#if __MI_SPIN
                bool lockTaken = false;
                try
                {
                    img.paintsLock.Enter(ref lockTaken);
                    foreach (OperationPending p in img.imagePaints)
                    {
                        p.perform(img.wbmp);
                    }
                }
                finally
                {
                    if (lockTaken) img.paintsLock.Exit(false);
                    System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
                }
#else // !__MI_SPIN
                lock (img.imagePaints)
                {
                    foreach (OperationPending p in img.imagePaints)
                    {
                        p.perform(i);
                    }
                }
#endif // !__MI_SPIN
                img.wbmp.Invalidate();
            }
            //add(i);
            if (img.cl1 != null)
            {
                cl.Children.Remove(img.cl1);
            }
            img.cl1 = img.cl0;
            img.cl0 = i;
        }

#endif // __USE_WBMP

    }

#endif // __MI_FIX

    class FillRect : OperationPending
    {
        int x;
        int y;
        int w;
        int h;
        Color color;

        public FillRect(NativeGraphics ng, WipeComponent pendingWipe, int x, int y, int w, int h)
            : base(ng, pendingWipe)
        {
            this.color = ng.sColor;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        protected FillRect(FillRect p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.color = p.color;
            this.x = p.x + translateX;
            this.y = p.y + translateY;
            this.w = p.w;
            this.h = p.h;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new FillRect(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            Rectangle i = new Rectangle();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y);
            updateClip(i, x, y);
            i.Width = w;
            i.Height = h;
            i.Stroke = null;
            i.Fill = new SolidColorBrush(color);
            //add(i);
            cl.Children.Add(i);
#if LOG_RENDER
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.FillRect perform"));
#endif
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            wbmp.FillRectangle(x, y, x + w, y + h, color);
        }
#endif

        public override void printLogging()
        {
            log("Fill rect x: " + x + " y: " + y +
                " w: " + w + " h: " + h + " color: " + color + " " + clipString());
        }
    }

    class FillTriangle : OperationPending
    {
        int x1, x2, x3;
        int y1, y2, y3;
        Color color;

        public FillTriangle(NativeGraphics ng, WipeComponent pendingWipe, int x1, int y1, int x2, int y2, int x3, int y3)
            : base(ng, pendingWipe)
        {
            this.color = ng.sColor;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.x3 = x3;
            this.y3 = y3;
        }

        protected FillTriangle(FillTriangle p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.color = p.color;
            this.x1 = p.x1 + translateX;
            this.y1 = p.y1 + translateY;
            this.x2 = p.x2 + translateX;
            this.y2 = p.y2 + translateY;
            this.x3 = p.x3 + translateX;
            this.y3 = p.y3 + translateY;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new FillTriangle(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            Polygon i = new Polygon();
            Canvas.SetLeft(i, 0);
            Canvas.SetTop(i, 0);
            updateClip(i, 0, 0);
            i.Stroke = null;
            i.Fill = new SolidColorBrush(color);
            i.Points.Add(new Point(x1, y1));
            i.Points.Add(new Point(x2, y2));
            i.Points.Add(new Point(x3, y3));
            //add(i);
            cl.Children.Add(i);
#if LOG_RENDER
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.FillTriangle perform"));
#endif
        }

        public override void printLogging()
        {
            log("Fill polygon ... " + " color: " + color + " " + clipString());
        }
    }

    class DrawRect : OperationPending
    {
        int x;
        int y;
        int w;
        int h;
        int thickness;
        Color color;

        public DrawRect(NativeGraphics ng, WipeComponent pendingWipe, int x, int y, int w, int h, int thickness)
            : base(ng, pendingWipe)
        {
            this.color = ng.sColor;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.thickness = thickness;
        }

        protected DrawRect(DrawRect p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.x = p.x + translateX;
            this.y = p.y + translateY;
            this.w = p.w;
            this.h = p.h;
            this.thickness = p.thickness;
            color = p.color;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawRect(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            Rectangle i = new Rectangle();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y);
            updateClip(i, x, y);
            i.Width = w;
            i.Height = h;
            i.Stroke = new SolidColorBrush(color);
            i.Fill = null;
            i.StrokeThickness = thickness;
            //add(i);
            cl.Children.Add(i);
#if LOG_RENDER
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.DrawRect perform"));
#endif
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            wbmp.DrawRectangle(x, y, x + w, y + h, color);
        }
#endif

        public override void printLogging()
        {
            log("Draw rect x: " + x + " y: " + y +
                " w: " + w + " h: " + h + " color: " + color + " " + clipString());
        }
    }

    class DrawRoundRect : OperationPending
    {
        int x;
        int y;
        int w;
        int h;
        int arcW;
        int arcH;
        bool bfill;
        Color color;

        public DrawRoundRect(NativeGraphics ng, WipeComponent pendingWipe, int x, int y, int w, int h, int arcW, int arcH, bool fill)
            : base(ng, pendingWipe)
        {
            this.color = ng.sColor;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.arcW = arcW;
            this.arcH = arcH;
            this.bfill = fill;
        }

        protected DrawRoundRect(DrawRoundRect p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.color = p.color;
            this.x = p.x + translateX;
            this.y = p.y + translateY;
            this.w = p.w;
            this.h = p.h;
            this.arcW = p.arcW;
            this.arcH = p.arcH;
            this.bfill = p.bfill;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawRoundRect(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            Rectangle i = new Rectangle();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y);
            updateClip(i, x, y);
            i.RadiusX = arcW;
            i.RadiusY = arcH;
            i.Width = w;
            i.Height = h;
            SolidColorBrush brush = new SolidColorBrush(color);
            if (bfill)
            {
                i.Fill = brush;
            }
            else
            {
                i.Stroke = brush;
            }
            //add(i);
            cl.Children.Add(i);
#if LOG_RENDER
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.DrawRoundRect perform"));
#endif
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            if (bfill)
            {
                wbmp.FillRectangle(x, y, x + w, y + h, color);
            }
            else
            {
                wbmp.DrawRectangle(x, y, x + w, y + h, color);
            }
        }
#endif

        public override void printLogging()
        {
            log("Draw round rect x: " + x + " y: " + y +
                " w: " + w + " h: " + h +  " " + clipString());
        }
    }

    class DrawLine : OperationPending
    {
        int x1;
        int y1;
        int x2;
        int y2;
        Color color;
        int strokeStyle, strokeWidth;

        public DrawLine(NativeGraphics ng, WipeComponent pendingWipe, int x1, int y1, int x2, int y2)
            : base(ng, pendingWipe)
        {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = ng.sColor;
            this.strokeStyle = ng.strokeStyle;
            this.strokeWidth = ng.strokeWidth;
        }

        protected DrawLine(DrawLine p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.x1 = p.x1 + translateX;
            this.y1 = p.y1 + translateY;
            this.x2 = p.x2 + translateX;
            this.y2 = p.y2 + translateY;
            this.color = p.color;
            this.strokeStyle = p.strokeStyle;
            this.strokeWidth = p.strokeWidth;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawLine(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            Line i = new Line();
            Canvas.SetLeft(i, 0);
            Canvas.SetTop(i, 0);
            updateClip(i, 0, 0);
            i.X1 = x1;
            i.Y1 = y1;
            i.X2 = x2;
            i.Y2 = y2;
            i.Stroke = new SolidColorBrush(color);
            i.StrokeThickness = strokeWidth;
            i.StrokeStartLineCap = i.StrokeEndLineCap = PenLineCap.Round;
            if (strokeStyle == javax.microedition.lcdui.Graphics._fDOTTED)
            {
                i.StrokeDashArray.Add(strokeWidth * 2);
                i.StrokeDashArray.Add(strokeWidth);
            }
            i.Fill = null;
            //add(i);
            cl.Children.Add(i);
#if LOG_RENDER
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.DrawLine perform"));
#endif
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            wbmp.DrawLineBresenham(x1, y1, x2, y2, color);
        }
#endif

        public override void printLogging()
        {
            log("Draw line x1: " + x1 + " y1: " + y1 +
                " x2: " + x2 + " y2: " + y2 + " color: " + color + " " + clipString());
        }
    }

    class DrawString : OperationPending
    {
        int x;
        int y;
        string str;
        Color color;
        NativeFont font;

        public DrawString(NativeGraphics ng, WipeComponent pendingWipe, NativeFont font, string str, int x, int y)
            : base(ng, pendingWipe)
        {
            this.color = ng.sColor;
            this.x = x;
            this.y = y;
            this.str = str;
            this.font = font;
        }

        protected DrawString(DrawString p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.x = p.x + translateX;
            this.y = p.y + translateY;
            this.str = p.str;
            this.font = p.font;
            this.color = p.color;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawString(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            TextBlock i = new TextBlock();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y - 2); // -2 hack
            updateClip(i, x, y - 2); // -2 hack
            i.FontSize = font.height;
            i.Text = str;
            i.Foreground = new SolidColorBrush(color);
            //i.Measure(new Size(100000, 100000));
            //i.Width = i.DesiredSize.Width;
            //i.Height = font.actualHeight;
            //add(i);
            cl.Children.Add(i);
#if LOG_RENDER
            com.codename1.io.Log.p(SilverlightImplementation.toJava("Impl.DrawString perform; " + str));
#endif
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            TextBlock i = new TextBlock();
            i.FontSize = font.height;
            i.Text = str;
            i.Foreground = new SolidColorBrush(color);
            using (var bitmapContext = wbmp.GetBitmapContext(ReadWriteMode.ReadWrite))
            {
                //wbmp.Render(i, new TranslateTransform() { X = x, Y = y });
                //wbmp.Invalidate(); // Dispose not correctly implemented in WP8 branch of WBMPex
            }
            //WriteableBitmap tBmp = new WriteableBitmap(i, null);
            //wbmp.Blit(new Point(x, y), tBmp, new Rect(0, 0, tBmp.PixelWidth, tBmp.PixelHeight), color, WriteableBitmapExtensions.BlendMode.Alpha);
        }
#endif

        public override void printLogging()
        {
            log("Draw string x: " + x + " y: " + y +
                " str: " + str + " color: " + color + " " + clipString());
        }
    }

    #endregion

    internal class NativeGraphics : global::java.lang.Object
    {
        public List<OperationPending> pendingPaintsList = new List<OperationPending>();
#if __RE_SPIN
        public SpinLock nextPaintsLock = new SpinLock(false);
#endif
        public int clipX, clipY, clipW, clipH;
        public bool clipSet;
        public int color;
        public int alpha = 255;
        public int strokeStyle, strokeWidth = 1;
        public NativeFont font;

        public Color sColor
        {
            get
            {
                Color cc = new Color();
                cc.A = (byte)alpha;
                cc.B = (byte)(color & 0xff);
                cc.R = (byte)((color >> 16) & 0xff);
                cc.G = (byte)((color >> 8) & 0xff);
                return cc;
            }
        }

        public virtual void paint(OperationPending o) {
#if LOG
            TrackingApp.CN1Extensions.Log("NativeGraphics.paint; {0}", o);
#endif
#if __RE_SPIN
            bool lockTaken = false;
            try
            {
                nextPaintsLock.Enter(ref lockTaken);
                pendingPaintsList.Add(o);
            }
            finally
            {
                if (lockTaken) nextPaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else
            lock (pendingPaintsList)
            {
                pendingPaintsList.Add(o);
            }
#endif
        }
    }

    public class CodenameOneImage : global::java.lang.Object
    {
        public ImageSource img;
        public int width;
        public int height;
        public string name;
        public Image imageCache;

        public new virtual void @this()
        {
            base.@this();
        }

        public virtual int getImageWidth()
        {
            return width;
        }

        public virtual int getImageHeight()
        {
            return height;
        }

        public bool OpaqueHint { get; set; }
    }

    /**
     * A mutable image is just a series of paints
     */
    class CodenameOneMutableImage : CodenameOneImage
    {
        public MutableImageGraphics graphics;
        public bool opaque;
        public List<OperationPending> imagePaints = new List<OperationPending>();
#if __MI_SPIN
        public SpinLock imagePaintsLock = new SpinLock(false);
#endif
#if __USE_WBMP
        public WriteableBitmap wbmp;
        public Image cl0, cl1;
#else
        /*public Canvas cl0, cl1;*/
#endif

        public override void @this()
        {
            base.@this();
        }

        public CodenameOneMutableImage()
        {
            graphics = new MutableImageGraphics(this);
        }
    }

    /**
     * A mutable image is just a series of paints
     */
    class MutableImageGraphics : NativeGraphics, global::javax.microedition.lcdui.game.ExtendedGraphics
    {
        private CodenameOneMutableImage image;
        private List<OperationPending> pendingPaints = new List<OperationPending>(32);
        private bool flushable;

        public MutableImageGraphics(CodenameOneMutableImage image)
        {
            this.image = image;
        }

        public int Width { get { return image.width; } }
        public int Height { get { return image.height; } }

        public override void paint(OperationPending o)
        {
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mutableImageGraphics paint; flushable? {0}; {1}", flushable, o);
#endif
            if (flushable)
            {
                pendingPaints.Add(o);
            }
            else
            {
#if __MI_SPIN
                bool lockTaken = false;
                try
                {
                    image.imagePaintsLock.Enter(ref lockTaken);
                    image.imagePaints.Add(o);
                }
                finally
                {
                    if (lockTaken) image.imagePaintsLock.Exit(false);
                    System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
                }
#else
                lock (image.imagePaints)
                {
                    image.imagePaints.Add(o);
                }
#endif
            }
        }

        public void setFlushable(bool flushable) 
        {
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mutableImageGraphics set flushable to {0}", flushable);
#endif
            this.flushable = flushable;
        }

        public void reset()
        {
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mutableImageGraphics reset");
#endif
#if __MI_SPIN
            bool lockTaken = false;
            try
            {
                image.imagePaintsLock.Enter(ref lockTaken);
                image.imagePaints.Clear();
            }
            finally
            {
                if (lockTaken) image.imagePaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else
            lock (image.imagePaints)
            {
                image.imagePaints.Clear();
            }
#endif
        }

        public void flush()
        {
#if LOG
            TrackingApp.CN1Extensions.Log("Impl.mutableImageGraphics flushing {0} paints", pendingPaints.Count);
#endif
#if __MI_SPIN
            bool lockTaken = false;
            try
            {
                image.imagePaintsLock.Enter(ref lockTaken);
                image.imagePaints.AddRange(pendingPaints);
                pendingPaints.Clear();
            }
            finally
            {
                if (lockTaken) image.imagePaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else
            lock (image.imagePaints)
            {
                image.imagePaints.AddRange(pendingPaints);
                pendingPaints.Clear();
            }
#endif
            
#if true
            SilverlightImplementation instance = SilverlightImplementation.instance;
            instance.drawImage((NativeGraphics)(instance.getNativeGraphics()), image, 0, 0);
            instance.flushGraphics();
#else       // incomplete solution
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                if (i == null)
                {
                    i = new Image();
                    image.img = new WriteableBitmap((int)SilverlightImplementation.cl.ActualWidth, (int)SilverlightImplementation.cl.ActualHeight);
                    i.Source = image.img;
                    SilverlightImplementation.cl.Children.Add(i);
                }
                realize();
            });
#endif
        }

        public void realize()
        {
            Canvas cl = new Canvas();
            cl.Width = image.width;
            cl.Height = image.height;
#if __MI_SPIN
            bool lockTaken = false;
            try
            {
                image.imagePaintsLock.Enter(ref lockTaken);
                foreach (OperationPending p in image.imagePaints)
                {
                    p.perform(cl);
                }
            }
            finally
            {
                if (lockTaken) image.imagePaintsLock.Exit(false);
                System.Diagnostics.Debug.Assert(lockTaken, "lock not taken!");
            }
#else
            lock (image.imagePaints)
            {
                foreach (OperationPending p in image.imagePaints)
                {
                    p.perform(cl);
                }
            }
#endif
#if true
            image.img = new WriteableBitmap(cl, null);
#else       // incomplete solution
            if (image.img == null)
            {
                image.img = new WriteableBitmap(cl, null);
            }
            else
            {
                ((WriteableBitmap)image.img).Render(cl, null);
                ((WriteableBitmap)image.img).Invalidate();
            }
#endif
        }
    }

} // end of namespace: com.codename1.impl
