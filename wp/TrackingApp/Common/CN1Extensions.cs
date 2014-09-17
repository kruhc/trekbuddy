using System;

using org.xmlvm;

using System.Runtime.CompilerServices;

namespace TrackingApp
{
    internal static class CN1Extensions
    {
        internal static string logFileName = "trekbuddy.log";

        public enum Level
        {
            DEBUG = 1,
            INFO,
            WARNING,
            ERROR
        }
        /*
        public const int DEBUG      = 1;
        public const int INFO       = 2;
        public const int WARNING    = 3;
        public const int ERROR      = 4;
        */
        private static int? level;

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void InitLogging()
        {
            com.codename1.io.Log log = (com.codename1.io.Log.getInstance() as com.codename1.io.Log);
            log.setFileWriteEnabled(true);
            log.setFileName(logFileName.toJava());
            level = GetLevel();
            com.codename1.io.Log.setLevel((int)level);
            com.codename1.io.Log.setAutoflush(true);
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void SetLevel(int level)
        {
            System.Diagnostics.Debug.WriteLine("setting loglevel to {0}", level);
            CN1Extensions.level = level;
            com.codename1.io.Log.setLevel(level);
            net.trekbuddy.wp8.TrekbuddyExtensions.saveLoglevel(level);
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static int GetLevel()
        {
            if (!level.HasValue)
            {
                level = net.trekbuddy.wp8.TrekbuddyExtensions.loadLoglevel();
                System.Diagnostics.Debug.WriteLine("loaded loglevel? {0}", level);
            }
            if (!level.HasValue)
            {
                level = (int)Level.WARNING;
                System.Diagnostics.Debug.WriteLine("using default loglevel {0}", level);
            }
            return (int)level;
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void Log(string message, Level level = Level.DEBUG)
        {
            if ((int)level >= CN1Extensions.level)
            {
                com.codename1.io.Log.p(message.toJava(), (int)level);
            }
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void Log(string format, object arg0)
        {
            if ((int)Level.DEBUG >= CN1Extensions.level)
            {
                com.codename1.io.Log.p(String.Format(format, arg0).toJava());
            }
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void Log(string format, params object[] args)
        {
            if ((int)Level.DEBUG >= CN1Extensions.level)
            {
                com.codename1.io.Log.p(String.Format(format, args).toJava());
            }
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void Err(Exception exception)
        {
            com.codename1.io.Log.e(exception.ToJavaException());
        }

        [Obsolete]
        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void Err(java.lang.Throwable throwable)
        {
            com.codename1.io.Log.e(throwable);
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void Err(string message, java.lang.Throwable throwable)
        {
            com.codename1.io.Log.p(message.toJava(), com.codename1.io.Log._fERROR);
            com.codename1.io.Log.e(throwable);
        }
    }
}
