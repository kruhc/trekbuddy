using System.IO;

namespace ICSharpCode.SharpZipLib.Tar
{
    /*
     * Override GetNextEntry() for faster iteration (uses Stream.Seek). Uses block factor of 1.
     * SharpZipLib's implementation Skip() method is implemented as "read through" :-(
     */
    public class FastTarInputStream : TarInputStream
    {
        private Stream stream;
        private bool brokenSkip;

        public FastTarInputStream(Stream stream) : base(stream, 1)
        {
            this.stream = stream;
            this.brokenSkip = cz.kruch.track.configuration.Config._fwp8Io && stream.GetType().FullName.Equals("Microsoft.Phone.Storage.NativeFileStream");
        }

        public new TarEntry GetNextEntry()
        {
            long a = Available;
            if (a > 0 && stream.CanSeek)
            {
                long n = a % TarBuffer.BlockSize == 0 ? a : (a / TarBuffer.BlockSize + 1) * TarBuffer.BlockSize;
                long nx = brokenSkip ? n << 32 : n;
                long before = Position;
                stream.Seek(nx, SeekOrigin.Current);
                long after = Position;
                if (after == before)
                {
                    throw new IOException("Seek failed. SD card I/O is broken in WP8, try 'broken I/O' option");
                }
                entryOffset = entrySize;
            }
            TarEntry te = base.GetNextEntry();
            return te;
        }
    }
}