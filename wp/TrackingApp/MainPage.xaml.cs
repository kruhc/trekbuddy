using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;
using System;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;

using TrackingApp.Resources;

using com.codename1.impl;
using com.codename1.ui;
using AppResources = TrackingApp.Resources.AppResources;

namespace TrackingApp
{
    public partial class MainPage : PhoneApplicationPage
    {
        //public static string BUILD_KEY = "f88283ae-0075-464f-98e0-c9f21255df72";
        //public static string PACKAGE_NAME = "net.trekbuddy.midlet";
        //public static string BUILT_BY_USER = "kruhc@seznam.cz";

        // Constructor
        public MainPage()
        {
            InitializeComponent();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
#if LOG
            try
            {
                CN1Extensions.Log("MainPage.OnNavigatedTo {0} {1}", e.Uri, e.NavigationMode);
            }
            catch (NullReferenceException)
            {
                // ignore, happens on start
            }
#endif

            if (App.instance == null)
            {
                net.trekbuddy.wp8.ui.UISynchronizationContext.Dispatcher.Initialize(Deployment.Current.Dispatcher);
                SilverlightImplementation.setCanvas(this, LayoutRoot);

                //Display.init(null);
                Display.init(); // hacked simplified init - does not start EDT etc
                // other ways of init
                //((com.codename1.impl.ImplementationFactory)com.codename1.impl.ImplementationFactory.getInstance()).createImplementation();
                //SilverlightImplementation impl = new SilverlightImplementation();
                //impl.@this();
                //impl.init(null);
                //com.codename1.ui.FriendlyAccess.setImplementation(impl);
#if LOG
                CN1Extensions.Log("MainPage.OnNavigatedTo; mode: {0} - new instance", e.NavigationMode);
#endif
                App.instance = new Main();
                App.instance.start();
            }
            else
            {
#if LOG
                CN1Extensions.Log("MainPage.OnNavigatedTo; mode: {0} - existing instance", e.NavigationMode);
#endif
            }

            base.OnNavigatedTo(e);
        }

        protected override void OnBackKeyPress(System.ComponentModel.CancelEventArgs e)
        {
            if (MessageBox.Show(AppResources.AppExitQuestion, AppResources.ApplicationTitle, MessageBoxButton.OKCancel) == MessageBoxResult.Cancel)
            {
                e.Cancel = true;
            }
            base.OnBackKeyPress(e);
        }
    }
}