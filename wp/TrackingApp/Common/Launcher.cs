using java.lang;
using org.xmlvm;
using net.trekbuddy.midlet;

namespace TrackingApp
{
    internal class Main : Object, Runnable
    {
        public static net.trekbuddy.midlet.TrackingApp i;

		public Main()
		{
			base.@this();
            System.Version wpVersion = System.Environment.OSVersion.Version;
            java.lang.System.setProperty("microedition.platform".toJava(), string.Format("WP {0}.{1}", wpVersion.Major, wpVersion.Minor).toJava());
            java.lang.System.setProperty("microedition.locale".toJava(), System.Globalization.CultureInfo.CurrentUICulture.TwoLetterISOLanguageName.toJava());
            //System.Version dllVersion = System.Version.Parse((System.Reflection.Assembly.GetExecutingAssembly().GetCustomAttributes(typeof(System.Reflection.AssemblyFileVersionAttribute), false)[0] as System.Reflection.AssemblyFileVersionAttribute).Version);
            //string midletVersion = string.Format("{0}.{1}.{2}-beta{3}", dllVersion.Major, dllVersion.Minor, dllVersion.Build, dllVersion.Revision);
            string infoVersion = (System.Reflection.Assembly.GetExecutingAssembly().GetCustomAttributes(typeof(System.Reflection.AssemblyInformationalVersionAttribute), false)[0] as System.Reflection.AssemblyInformationalVersionAttribute).InformationalVersion;
            string midletVersion = infoVersion;
            java.lang.System.setProperty("MIDlet-Version".toJava(), midletVersion.toJava());
            java.lang.System.setProperty("trekbuddy.app-flags".toJava(), "log_enable".toJava());
			//((Display)Display.getInstance()).callSerially(this);
            //com.codename1.impl.SilverlightImplementation.instance.callSerially(this);
            run();
		}

		public void run()
		{
            Main.i = new net.trekbuddy.midlet.TrackingApp();
			Main.i.@this();
			Main.i.init(this);
		}

		public void start()
		{
			//((Display)Display.getInstance()).callSerially(new StartClass());
            //com.codename1.impl.SilverlightImplementation.instance.callSerially(new StartClass());
            Main.i.start();
		}

		public void stop()
		{
			//((Display)Display.getInstance()).callSerially(new StopClass());
            //com.codename1.impl.SilverlightImplementation.instance.callSerially(new StopClass());
            Main.i.stop();
		}

        public void destroy()
        {
            //((Display)Display.getInstance()).callSerially(new DestroyClass());
            //com.codename1.impl.SilverlightImplementation.instance.callSerially(new DestroyClass());
            Main.i.destroy();
        }
    }
/*
    internal class StartClass : Object, Runnable
    {
        public void run()
        {
            Main.i.start();
        }
    }

    internal class StopClass : Object, Runnable
    {
        public void run()
        {
            Main.i.stop();
        }
    }

    internal class DestroyClass : Object, Runnable
    {
        public void run()
        {
            Main.i.destroy();
        }
    }
*/
}
