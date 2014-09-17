using System;

namespace java.util
{
    public class Timer
    {
        public Timer()
        {
        }

        public void @this()
        {
        }

        public void cancel()
        {
        }

        public void schedule(TimerTask task, long delay)
        {
            create(task, delay);
        }

        public void schedule(TimerTask task, long delay, long period)
        {
            create(task, delay, (int) period);
        }

        public void scheduleAtFixedRate(TimerTask task, long delay, long period)
        {
            schedule(task, delay, period);
        }

        private void create(TimerTask task, long delay, int period = System.Threading.Timeout.Infinite)
        {
            task.setProps(null, period);
            System.Threading.Timer nt = new System.Threading.Timer(task.callback, null, delay, period);
            task.setProps(nt, period);
        }
    }
}
