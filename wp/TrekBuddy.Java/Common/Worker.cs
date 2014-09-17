using System;
using System.Collections.Generic;
using System.Threading;

namespace cz.kruch.track.util
{
    public class Worker
    {
        private List<global::java.lang.Runnable> tasks = new List<global::java.lang.Runnable>(16);
        private volatile bool idle;
        private int maxSize;

        public Worker()
        {
            this.idle = true;
        }

        public void @this(global::java.lang.String name) 
        {
            // nothing to do
        }

        public void start()
        {
            // nothing to do
        }

        public void setPriority(int prio)
        {
            // nothing to do
        }

        public int getQueueSize()
        {
            lock (tasks)
            {
                return tasks.Count;
            }
        }

        public int getMaxQueueSize()
        {
            lock (tasks)
            {
                return maxSize;
            }
        }

        public void enqueue(global::java.lang.Runnable task)
        {
            bool exec = false;
            lock (tasks)
            {
                tasks.Add(task);
                if (tasks.Count > maxSize)
                {
                    maxSize = tasks.Count;
                }
                if (idle)
                {
                    idle = false;
                    exec = true;
                }
            }
            if (exec)
            {
                ThreadPool.QueueUserWorkItem(o => run());
            }
        }

        public global::java.lang.Runnable peek()
        {
            global::java.lang.Runnable r = null;
            lock (tasks) {
                if (tasks.Count > 0) {
                    r = tasks[0];
                }
            }
            return null;
        }

        public void run()
        {
            global::java.lang.Runnable task;
            lock (tasks)
            {
                task = tasks[0];
                tasks.RemoveAt(0);
            }
            try
            {
                task.run();
            }
            catch (Exception e)
            {
                // TODO log error
            }
            bool exec = false;
            lock (tasks)
            {
                if (tasks.Count == 0)
                {
                    idle = true;
                }
                else 
                {
                    exec = true;
                }
            }
            if (exec)
            {
                ThreadPool.QueueUserWorkItem(o => run());
            }
        }
    }
}
