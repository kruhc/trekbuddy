using System;
using System.IO;

namespace com.codename1.impl
{
    internal class OutputStreamProxy : global::java.io.OutputStream
    {
        private Stream internalStream;

        public OutputStreamProxy(Stream internalStream)
        {
            base.@this();
            this.internalStream = internalStream;
        }

        public override void close()
        {
            internalStream.Close();
        }

        public override void flush()
        {
            internalStream.Flush();
        }

        public override void write(global::org.xmlvm._nArrayAdapter<sbyte> n1)
        {
            write(n1, 0, n1.Length);
        }

        public override void write(global::org.xmlvm._nArrayAdapter<sbyte> n1, int n2, int n3)
        {
            internalStream.Write(SilverlightImplementation.toByteArray(n1.getCSharpArray()), n2, n3);
        }

        public override void write(int n1)
        {
            internalStream.WriteByte((byte)n1);
        }

    }
}
