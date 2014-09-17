using System;

namespace java.util
{
    public abstract class TimerTask : global::java.lang.Object, global::java.lang.Runnable
    {
        private volatile System.Threading.Timer nt;
        private volatile int period;
        private volatile bool executed;

        protected TimerTask()
        {
        }

        public void @this()
        {
        }

        public abstract void run();

        public bool cancel()
        {
            if (nt == null)
            {
                System.Diagnostics.Debug.WriteLine("One-shot timer already gone; {0}", this);
                return false;
            }
            
            System.Diagnostics.Debug.WriteLine("Cancelling periodic timer; {0}", this);
            nt.Dispose();
            nt = null;

            return true;
        }

        public void callback(object state)
        {
            executed = true;
            try
            {
                run();
            }
            finally
            {
                if (period == System.Threading.Timeout.Infinite)
                {
                    System.Diagnostics.Debug.WriteLine("Disposing one-shot timer; {0}", this);
                    nt.Dispose();
                    nt = null;
                }
            }
        }

        public void setProps(System.Threading.Timer timer, int period)
        {
            if (executed && period == System.Threading.Timeout.Infinite)
            {
                System.Diagnostics.Debug.WriteLine("One-shot timer already executed; {0}", this);
                timer.Dispose();
            }
            else
            {
                this.nt = timer;
                this.period = period;
            }
        }
    }
}
