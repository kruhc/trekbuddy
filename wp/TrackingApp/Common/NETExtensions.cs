using System;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;

using org.xmlvm;

namespace TrackingApp
{
    internal static class NETExtensions
    {

#if false

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void SafeWait(this Task task, string failureMessage = null)
        {
            try
            {
                task.Wait();
            }
            catch (AggregateException ae)
            {
                throw Flatten(ae, failureMessage);
            }
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static T SafeWait<T>(this Task<T> task, string failureMessage = null)
        {
            try
            {
                task.Wait();
            }
            catch (AggregateException ae)
            {
                throw Flatten(ae, failureMessage);
            }
            return task.Result;
        }

        private static Exception Flatten(AggregateException ae, string failureMessage = null)
        {
            Exception e = ae.Flatten().InnerException;
            if (failureMessage == null)
                CN1Extensions.Log(e.ToString(), CN1Extensions.Level.ERROR);
            else
                CN1Extensions.Log((failureMessage + e.ToString()), CN1Extensions.Level.ERROR);
            //throw new global::org.xmlvm._nExceptionAdapter(e.ToJavaException());
            return e;
        }

#else

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void SafeWait(this Task task, string failureMessage = null)
        {
            try
            {
                task.ConfigureAwait(false).GetAwaiter().GetResult();
            }
            catch (Exception e)
            {
                throw Throw(e, failureMessage);
            }
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static T SafeWait<T>(this Task<T> task, string failureMessage = null)
        {
            try
            {
                return task.ConfigureAwait(false).GetAwaiter().GetResult();
            }
            catch (Exception e)
            {
                throw Throw(e, failureMessage);
            }
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static void FastWait(this Task task)
        {
            task.ConfigureAwait(false).GetAwaiter().GetResult();
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static T FastWait<T>(this Task<T> task)
        {
            return task.ConfigureAwait(false).GetAwaiter().GetResult();
        }

        private static Exception Throw(Exception e, string failureMessage = null)
        {
            if (failureMessage == null)
                CN1Extensions.Log(e.ToString(), CN1Extensions.Level.ERROR);
            else
                CN1Extensions.Log((failureMessage + e.ToString()), CN1Extensions.Level.ERROR);
            //throw new global::org.xmlvm._nExceptionAdapter(e.ToJavaException());
            return e;
        }

#endif

    }
}
