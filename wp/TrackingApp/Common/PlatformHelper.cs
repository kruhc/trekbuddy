//#define __BYPASS_MS
#define __SMART_MS
//#define __DUP_TIN

using System;
using System.IO;
using System.Runtime.CompilerServices;

using org.xmlvm;

namespace com.codename1.impl
{
    internal partial class SilverlightImplementation : CodenameOneImplementation
    {
        private static Stream toStream(java.io.InputStream javaStream)
        {
            if (javaStream is api.io.FilterInputStream)
            {
                api.io.FilterInputStream fin = javaStream as api.io.FilterInputStream;
                if (fin._fin is net.trekbuddy.wp8.InputStreamWrapper)
                {
                    net.trekbuddy.wp8.InputStreamWrapper isw = fin._fin as net.trekbuddy.wp8.InputStreamWrapper;
                    return isw.Stream;
                }
            }
#if __BYPASS_MS
            else if (javaStream is com.ice.tar.TarInputStream)
            {
                com.ice.tar.TarInputStream ain = javaStream as com.ice.tar.TarInputStream;
                if (ain.getInputStream() is api.io.FilterInputStream)
                {
                    api.io.FilterInputStream fin = ain.getInputStream() as api.io.FilterInputStream;
                    if (fin._fin is net.trekbuddy.wp8.InputStreamWrapper)
                    {
                        net.trekbuddy.wp8.InputStreamWrapper isw = fin._fin as net.trekbuddy.wp8.InputStreamWrapper;
                        return new TarEntryStream(ain, isw.Stream);
                    }
                }
            }
#endif
#if LOG
            long t0 = System.Environment.TickCount;
#endif
#if !__SMART_MS
            sbyte[] buffer = new sbyte[cz.kruch.track.configuration.Config._finputBufferSize];
            global::org.xmlvm._nArrayAdapter<sbyte> ad = new global::org.xmlvm._nArrayAdapter<sbyte>(buffer);
            MemoryStream stream = new MemoryStream(8192);
            int c = javaStream.read(ad);
            while (c >= 0)
            {
                stream.Write((byte[])((Array)buffer), 0, c);
                c = javaStream.read(ad);
            }
            stream.Seek(0, SeekOrigin.Begin);
#else
            int capacity = cz.kruch.track.configuration.Config._finputBufferSize;
            bool directRead = false;
            com.ice.tar.TarInputStream tin = javaStream as com.ice.tar.TarInputStream;
            if (tin != null)
            {
                capacity = tin.available();
                if (capacity <= 0)
                {
                    capacity = 8192;
                }
                else
                {
                    directRead = true; // we know exact length
                }
            }
            MemoryStream stream = new MemoryStream(capacity);
            if (directRead) // will read into MemoryStream's buffer directly
            {
                stream.SetLength(capacity);
                byte[] buffer = stream.GetBuffer();
                global::org.xmlvm._nArrayAdapter<sbyte> ad = new global::org.xmlvm._nArrayAdapter<sbyte>((sbyte[])((Array)buffer));
                int count = 0, num = capacity;
                int c = javaStream.read(ad, 0, capacity);
                while (c >= 0 && num > 0)
                {
                    count += c;
                    num -= c;
                    if (num == 0) break;
                    c = javaStream.read(ad, count, num);
                }
            }
            else
            {
                sbyte[] buffer = new sbyte[cz.kruch.track.configuration.Config._finputBufferSize];
                global::org.xmlvm._nArrayAdapter<sbyte> ad = new global::org.xmlvm._nArrayAdapter<sbyte>(buffer);
                byte[] byteBuffer = (byte[])((Array)buffer);
                int c = javaStream.read(ad);
                while (c >= 0)
                {
                    stream.Write(byteBuffer, 0, c);
                    c = javaStream.read(ad);
                }
                stream.Seek(0, SeekOrigin.Begin);
            }
#endif
#if LOG
            long t1 = System.Environment.TickCount;
            TrackingApp.CN1Extensions.Log("PlatformHelper.toStream: {0} from {1}", (t1 - t0), javaStream);
#endif
            return stream;
        }

        #region CN1 Arrays methods implementation

        public override bool instanceofObjArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<global::System.Object>;
        }

        public override bool instanceofByteArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<sbyte>;
        }

        public override bool instanceofShortArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<short>;
        }

        public override bool instanceofLongArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<long>;
        }

        public override bool instanceofIntArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<int>;
        }

        public override bool instanceofFloatArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<float>;
        }

        public override bool instanceofDoubleArray(global::java.lang.Object n1)
        {
            return n1 is global::org.xmlvm._nArrayAdapter<double>;
        }

        #endregion

        #region Local helper methods (Java<->C#)

        public static string toCSharp(java.lang.String str)
        {
            return global::org.xmlvm._nUtil.toNativeString(str);
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static object convertArray(string[] arr)
        {
            java.lang.String[] resp = new java.lang.String[arr.Length];
            for (int iter = 0; iter < resp.Length; iter++)
            {
                resp[iter] = arr[iter].toJava();
            }
            return new _nArrayAdapter<System.Object>(resp);
        }

        public static byte[] toByteArray(sbyte[] byteArray)
        {
            return (byte[])(Array)byteArray;
        }

        #endregion
    }

    /**
     * Length getter and Position setter must work correctly for BitmapImage to work correctly!!!
     */
    class TarEntryStream : Stream
    {
        private com.ice.tar.TarInputStream tarStream;
        private Stream nativeStream;
        private long entryPosition, entrySize;
        private long offset;
        private byte[] oneByte;

        public TarEntryStream(com.ice.tar.TarInputStream tarStream, Stream nativeStream) 
        {
            this.tarStream = tarStream;
            this.nativeStream = nativeStream;
            this.entryPosition = nativeStream.Position;
            this.entrySize = tarStream.getLength();
            this.oneByte = new byte[1];
        }

        public override bool CanSeek { get { return false; } }
        public override bool CanWrite { get { return false; } }
        public override bool CanTimeout { get { return false; } }
        public override bool CanRead { get { return true; } }
        public override long Length
        {
            get { 
                return entrySize; 
            }
        }
        public override long Position
        {
            get { 
                return nativeStream.Position - entryPosition; 
            }
            set {
                nativeStream.Position = entryPosition + value;
                tarStream.setOffsets(entryPosition + value, value);
            }
        }

        public override void SetLength(long value) { throw new NotSupportedException(); }
        public override void Write(byte[] buffer, int offset, int count) { throw new NotSupportedException(); }
        public override void WriteByte(byte value) { throw new NotSupportedException(); }
        public override long Seek(long offset, SeekOrigin origin) { throw new NotSupportedException(); }
        public override void Flush() { throw new NotSupportedException(); }

        public override int ReadByte()
        {
#if !__DUP_TIN
            return tarStream.read();
#else
            int i = Read(oneByte, 0, 1);
            if (i > 0)
            {
                return oneByte[0];
            }
            return -1;
#endif
        }

        public override int Read(byte[] buffer, int offset, int count)
        {
#if !__DUP_TIN
            global::org.xmlvm._nArrayAdapter<sbyte> ad = new _nArrayAdapter<sbyte>((sbyte[])((Array)buffer));
            int v = tarStream.read(ad, offset, count);
            if (v < 0)
            {
                return 0;
            }
            return v;
#else // this just duplicates TarInputStream.read(byte[], int, int)
            if (this.offset == this.entrySize) 
            {
                return 0;
            }
            int num = count - offset;
            if (num > (this.entrySize - this.offset))
            {
                num = (int) (this.entrySize - this.offset);
            }
            int v = nativeStream.Read(buffer, offset, count);
            if (v > 0)
            {
                tarStream.advanceOffsets(v);
                this.offset += v;
            }
            return v;
#endif
        }

        public override void Close()
        {
            // ignore
        }
    }
}
