using System;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;

using org.xmlvm;

namespace TrackingApp
{
    internal static class NETExtensions
    {
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
    }
}
