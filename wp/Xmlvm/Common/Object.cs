using org.xmlvm;
using System;
using System.Threading;
namespace java.lang
{
	public class Object
	{
		static Object()
		{
			Object.@static();
		}
		private static void initNativeLayer()
		{
		}
        public void @this()
		{
		}
		public virtual object clone()
		{
			return base.MemberwiseClone();
		}
		public virtual bool equals(Object n1)
		{
			return this.Equals(n1);
		}
		public virtual void @finally()
		{
			throw new _nNotYetImplementedException("native/wrapper method not yet implemented");
		}
		public virtual object getClass()
		{
			Type type = base.GetType();
			return _nTIB.getClass(type);
		}
		public virtual int hashCode()
		{
			return this.GetHashCode();
		}
		public virtual void notify()
		{
			Monitor.Pulse(this);
		}
		public virtual void notifyAll()
		{
			Monitor.PulseAll(this);
		}
		public virtual object toString()
		{
			_nElement nElement;
			nElement.i = 0;
			nElement.l = 0L;
			nElement.f = 0f;
			nElement.d = 0.0;
			object obj = new StringBuilder();
			((StringBuilder)obj).@this();
			object obj2 = ((Object)this).getClass();
			obj2 = ((Class)obj2).getName();
			obj = ((StringBuilder)obj).append((String)obj2);
			nElement.i = 64;
			obj = ((StringBuilder)obj).append((char)nElement.i);
			nElement.i = ((Object)this).hashCode();
			obj2 = Integer.toHexString(nElement.i);
			obj = ((StringBuilder)obj).append((String)obj2);
			obj = ((StringBuilder)obj).toString();
			return (String)obj;
		}
		public virtual void wait()
		{
			Monitor.Wait(this);
		}
		public virtual void wait(long n1)
		{
			Monitor.Wait(this, (int)n1);
		}
		public virtual void wait(long n1, int n2)
		{
			Monitor.Wait(this, (int)n1);
		}
		public static void @static()
		{
			Object.initNativeLayer();
		}
	}
}
