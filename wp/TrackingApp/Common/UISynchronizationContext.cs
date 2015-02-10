#define __USE_CONTEXT       // use DispatcherSynchronizationContext instead of Dispatcher itself

using System;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Threading;

// TODO
// 1. move to better namespace

namespace net.trekbuddy.wp8.ui
{
    internal class UISynchronizationContext
    {
        private DispatcherSynchronizationContext context;
        private Dispatcher dispatcher;

        #region Singleton implementation

        static readonly UISynchronizationContext instance = new UISynchronizationContext();

        public static UISynchronizationContext Dispatcher
        {
            get
            {
                return instance;
            }
        }

        UISynchronizationContext()
        {
        }

        #endregion

        public void Initialize(Dispatcher dispatcher)
        {
            this.dispatcher = dispatcher;
            this.context = new DispatcherSynchronizationContext(dispatcher);
        }

        public bool CheckAccess()
        {
            return dispatcher.CheckAccess();
        }

        /*
         * MS implementation does pretty much the same
         */

/*
        public void InvokeAsync(Action action)
        {
#if __USE_CONTEXT
#if true // original_always_executes_async
            context.Post(state => action(), null);
#else
            if (dispatcher.CheckAccess())
            {
                action();
            }
            else
            {
                context.Post(state => action(), null);
            }
#endif
#else
            dispatcher.BeginInvoke(action);
#endif
        }

        public void InvokeSync(Action action)
        {
#if __USE_CONTEXT
#if true // original_always_tries_direct_access_first
            if (dispatcher.CheckAccess())
            {
                action();
            }
            else
            {
                Exception oe = null;
                Action safeAction = delegate()
                {
                    try
                    {
                        action();
                    }
                    catch (Exception e)
                    {
                        oe = e;
                    }
                };
                context.Send(state => safeAction(), null);
                if (oe != null)
                {
                    throw oe;
                }
            }
#else
            context.Send(state => action(), null);
#endif
#else
            if (dispatcher.CheckAccess())
            {
                action();
            }
            else
            {
                using (AutoResetEvent are = new AutoResetEvent(false))
                {
                    dispatcher.BeginInvoke(() =>
                    {
                        action(); // TODO try-catch 
                        are.Set();
                    });
                    are.WaitOne();
                }
            }
#endif
        }
*/

        public void InvokeAsync(Action action)
        {
            context.Post(state => action(), null); // TODO handle exceptions
        }

        public void InvokeSync(Action action)
        {
            Exception oe = null;
            Action safeAction = delegate()
            {
                try
                {
                    action();
                }
                catch (Exception e)
                {
                    oe = e;
                }
            };
            context.Send(state => safeAction(), null);
            if (oe != null)
            {
                throw oe;
            }
        }
    }
}
