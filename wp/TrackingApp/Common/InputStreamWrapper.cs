#define __SBYTE_CAST        // use ([])(Array) cast instead of block copy

using System;
using System.IO;

using org.xmlvm;
using TrackingApp;

namespace net.trekbuddy.wp8
{
    internal sealed class InputStreamWrapper : java.io.InputStream
    {
        private Stream internalStream;
        private long markedPosition;
        private bool brokenSkip;

        public Stream Stream
        {
            get
            {
                return internalStream;
            }
        }

        public InputStreamWrapper(Stream internalStream)
        {
            base.@this();
            this.internalStream = internalStream;
            this.brokenSkip = cz.kruch.track.configuration.Config._fwp8Io && internalStream.GetType().FullName.Equals("Microsoft.Phone.Storage.NativeFileStream");
#if LOG
            CN1Extensions.Log("InputStreamWrapper.ctor; {0}", internalStream.GetType().FullName);
#endif
        }

        public override int available()
        {
/*
            long a = internalStream.Length;
            if (a < int.MaxValue)
            {
                return (int)a;
            }
            return int.MaxValue;
*/
            return (int) internalStream.Length;
        }

        public override void close()
        {
            internalStream.Close();
        }

        public override bool markSupported()
        {
            return internalStream.CanSeek;
        }

        public override void mark(int readlimit)
        {
            markedPosition = internalStream.Position;
        }

        public override int read()
        {
            return internalStream.ReadByte();
        }

        public override int read(global::org.xmlvm._nArrayAdapter<sbyte> n1)
        {
            return read(n1, 0, n1.Length);
        }

        public override int read(global::org.xmlvm._nArrayAdapter<sbyte> n1, int n2, int n3)
        {
#if LOG
            if (n2 != 0)
            {
                CN1Extensions.Log("InputStreamWrapper.read with offset ({0}) - may be inefficient", n2);
            }
#endif
            sbyte[] sb = n1.getCSharpArray();
#if __SBYTE_CAST
#if LOG
            CN1Extensions.Log("InputStreamWrapper.read with cast hack");
#endif
            int v = internalStream.Read((byte[])((Array)sb), n2, n3);
            if (v <= 0)
            {
                return -1;
            }
            return v;
#else
            byte[] buffer = new byte[sb.Length];
            int v = internalStream.Read(buffer, n2, n3);
            if (v <= 0)
            {
                return -1;
            }
            Buffer.BlockCopy(buffer, n2, sb, n2, v);
            return v;
#endif
        }

        public override void reset()
        {
            internalStream.Seek(markedPosition, SeekOrigin.Begin);
        }

        public override long skip(long n)
        {
            long nx = brokenSkip ? n << 32 : n;
            long before = internalStream.Position;
            internalStream.Seek(nx, SeekOrigin.Current);
            long after = internalStream.Position;
            if (n > 0 && after == before)
            {
                throw new IOException("Seek failed. SD card I/O is broken in WP8, try 'broken I/O' option");
            }
            return after - before;
        }

        public override object toString()
        {
            return (internalStream.GetType().FullName + " (wrapped)").toJava();
        }
    }
}
