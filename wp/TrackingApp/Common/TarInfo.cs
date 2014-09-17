using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;

using ICSharpCode.SharpZipLib.Tar;

using org.xmlvm;
using com.codename1.impl;
using TrackingApp;

namespace net.trekbuddy.wp8
{
    internal class TarInfo
    {
        public static TarInfo Open(string filename, bool quick = false)
        {
            filename = System.Net.HttpUtility.UrlDecode(filename);
            SilverlightImplementation.FsType fsType;
            string natPath = SilverlightImplementation.nativePath(filename.toJava());
            string path = SilverlightImplementation.relativePath(natPath, out fsType);

            return Open(path, fsType, quick);
        }

        public static TarInfo Open(string path, SilverlightImplementation.FsType fsType, bool quick = false)
        {
#if LOG
            CN1Extensions.Log("TarInfo.Open: {0}, fs type? {1}", path, fsType);
#endif
            TarInfo tarInfo = new TarInfo();
            Stream stream = null;
            switch (fsType)
            {
                case SilverlightImplementation.FsType.Local:
                {
                    stream = LocalStorage.OpenInputStream(path);
                }
                break;
                case SilverlightImplementation.FsType.Card:
                {
                    stream = CardStorage.OpenInputStream(path);
                }
                break;
                default:
                {
                    throw new NotSupportedException("OneDrive");
                }
            }
            using (FastTarInputStream tarIn = new FastTarInputStream(stream))
            {
                TarEntry tarEntry;
                while ((tarEntry = tarIn.GetNextEntry()) != null)
                {
#if LOG
                    CN1Extensions.Log("found entry {0}", tarEntry.Name);
#endif
                    if (tarEntry.IsDirectory)
                    {
                        if (tarEntry.Name == "set/")
                        {
                            tarInfo.IsAtlas = false;
                            tarInfo.Layers = null;
                            if (quick) 
                                break;
                        }
                        else
                        {
                            string layer = tarEntry.Name.Substring(0, tarEntry.Name.IndexOf('/'));
                            tarInfo.IsAtlas = true;
                            if (tarInfo.Layers == null)
                            {
                                tarInfo.Layers = new List<string>(16);
                            }
                            if (!tarInfo.Layers.Contains(layer))
                            {
#if LOG
                                CN1Extensions.Log("found layer {0}", layer);
#endif
                                tarInfo.Layers.Add(layer);
                            }
                        }
                    }
                    else
                    {
                        if (tarEntry.Name.EndsWith(".tba"))
                        {
                            tarInfo.IsAtlas = true;
                            if (quick) 
                                break;
                        }
                        else if (tarEntry.Name.StartsWith("set/"))
                        {
                            tarInfo.IsAtlas = false;
                            if (quick) 
                                break;
                        }
                    }
                }
            }
#if LOG
            if (tarInfo.IsAtlas)
            {
                CN1Extensions.Log("found atlas with {0} layers", tarInfo.Layers == null ? -1 : tarInfo.Layers.Count);
            }
            else
            {
                CN1Extensions.Log("{0} is not atlas", path);
            }
#endif

            return tarInfo;
        }

        public bool IsAtlas
        {
            get;
            private set;
        }

        public IList<string> Layers
        {
            get;
            private set;
        }

        private TarInfo()
        {
        }
    }
}
