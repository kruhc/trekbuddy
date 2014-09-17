using System;

namespace api.lang
{
    public class ThreadPool
    {
        public static bool QueueUserWorkItem(global::java.lang.Runnable runnable)
        {
            return System.Threading.ThreadPool.QueueUserWorkItem(o => runnable.run());
        }

        private static ThreadPool impl;

        public static ThreadPool instance()
        {
            if (impl == null)
            {
                impl = new ThreadPool();
            }
            return impl;
        }

        public bool enqueue(global::java.lang.Runnable runnable)
        {
            return QueueUserWorkItem(runnable);
        }

        public int getQueueSize()
        {
            return -1;
        }
    }
}
