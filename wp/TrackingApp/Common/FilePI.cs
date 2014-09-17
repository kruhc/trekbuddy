using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;

using org.xmlvm;
using net.trekbuddy.wp8;
using InputStreamProxy = net.trekbuddy.wp8.InputStreamWrapper;
using TrackingApp;

namespace com.codename1.impl
{
    internal partial class SilverlightImplementation : CodenameOneImplementation //, IServiceProvider
    {
        internal const string FS_DEVICE_ROOT = "Local";
        internal const string FS_SDCARD_ROOT = "Card";
        internal const string FS_SKYDRIVE_ROOT = "OneDrive";

        public enum FsType
        {
            None = 0,
            Local = 1, // Isolated
            Card = 2,
            Skydrive = 3
        }

        internal readonly static char NATIVE_FSSEP = System.IO.Path.DirectorySeparatorChar; // '\\'
        internal readonly static string NATIVE_PATHSEP = new string(new char[] { NATIVE_FSSEP }); // "\\"
        
        #region CN1 Connector implementation (MIDP Connector)

        public override global::System.Object openOutputStream(global::java.lang.Object n1)
        {
            if (n1 is java.lang.String)
            {
                java.lang.String jn1 = System.Net.HttpUtility.UrlDecode(((java.lang.String)n1).toCSharp()).toJava();
                Stream stream = LocalStorage.OpenOutputStream(relativePath(nativePath(jn1)));
                return new OutputStreamProxy(stream);
            }

            NetworkOperation n = (NetworkOperation)n1;
            //com.codename1.io.BufferedOutputStream bo = new com.codename1.io.BufferedOutputStream();
            //bo.@this(new OutputStreamProxy(n.requestStream));
            //return bo;
            return new OutputStreamProxy(n.requestStream);
        }

        public override global::System.Object openOutputStream(global::java.lang.Object n1, int n2)
        {
            if (n1 is java.lang.String)
            {
                java.lang.String jn1 = System.Net.HttpUtility.UrlDecode(((java.lang.String)n1).toCSharp()).toJava();
                Stream stream = LocalStorage.OpenOutputStream(relativePath(nativePath(jn1)));
                return new OutputStreamProxy(stream);
            }
            return null;
        }

        public override global::System.Object openInputStream(global::java.lang.Object n1)
        {
            if (n1 is java.lang.String)
            {
                n1 = System.Net.HttpUtility.UrlDecode(((java.lang.String)n1).toCSharp()).toJava();
                Stream stream;
                FsType fsType;
                string natPath = nativePath((java.lang.String)n1);
                string path = relativePath(natPath, out fsType);
                // FIXME testing of helper
                /*if (path.EndsWith(".tar"))
                {
                    net.trekbuddy.wp8.TarInfo.Open(path, fsType, true);
                }*/
                switch (fsType)
                {
                    case FsType.Local:
                        {
                            stream = LocalStorage.OpenInputStream(path);
                        }
                        break;

                    case FsType.Card:
                        {
                            stream = CardStorage.OpenInputStream(path);
                        }
                        break;

                    default:
                        {
                            throw new ArgumentException(String.Format("FsType: {0}", fsType));
                        }
                }

                return new InputStreamProxy(stream);
            }

            NetworkOperation n = (NetworkOperation)n1;
            //com.codename1.io.BufferedInputStream bo = new com.codename1.io.BufferedInputStream();
            //bo.@this(new InputStreamProxy(n.response.GetResponseStream()));
            //return bo;
            return new InputStreamProxy(n.response.GetResponseStream());
        }

        #endregion

        #region CN1 File implementation (JSR-75)

        public override global::System.Object listStorageEntries()
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                return convertArray(store.GetFileNames());
            }
        }

        public override object listFilesystemRoots()
        {
            List<java.lang.String> roots = new List<java.lang.String>();
            //roots.Add(toJava(ApplicationData.Current.LocalFolder.Name + "/"));
            roots.Add(string.Concat(FS_DEVICE_ROOT, "/").toJava());
            if (CardStorage.Available)
            {
                //roots.Add(toJava(sdCard.RootFolder.Name + "/"));
                roots.Add(string.Concat(FS_SDCARD_ROOT, "/").toJava());
            }
            //if (SkydriveStorage.ConnectionPossible)
            {
                roots.Add(string.Concat(FS_SKYDRIVE_ROOT, "/").toJava());
            }
            return new _nArrayAdapter<System.Object>(roots.ToArray());
        }

        internal static string nativePath(java.lang.String s)
        {
            string ss = toCSharp(s);
            if (ss.StartsWith("file:/"))
            {
                ss = ss.Substring(6).Replace('/', NATIVE_FSSEP);
            }
            return ss;
        }

        private static string[] prependFile(string[] arr)
        {
            for (int iter = 0; iter < arr.Length; iter++)
            {
                if (!arr[iter].StartsWith("file:/"))
                {
                    arr[iter] = "file:/" + arr[iter];
                }
            }
            return arr;
        }

        public override object listFiles(java.lang.String n1)
        {
            ListFiles lister = ListLocalFiles;
            string path = nativePath(n1);
#if LOG
            CN1Extensions.Log("Impl.listFiles in {0} ({1})", path, n1.toCSharp());
#endif
            while (path.StartsWith(NATIVE_PATHSEP))
            {
                path = path.Substring(1);
            }
            if (path.StartsWith(FS_SDCARD_ROOT))
            {
                path = path.Substring(FS_SDCARD_ROOT.Length);
                lister = ListCardFiles;
            }
            else if (path.StartsWith(FS_SKYDRIVE_ROOT))
            {
                path = path.Substring(FS_SKYDRIVE_ROOT.Length);
                lister = ListSkydriveFiles;
            }
            else if (path.StartsWith(FS_DEVICE_ROOT))
            {
                path = path.Substring(FS_DEVICE_ROOT.Length);
            }
            while (path.StartsWith(NATIVE_PATHSEP))
            {
                path = path.Substring(1);
            }
            if (path.EndsWith(NATIVE_PATHSEP))
            {
                path = path.Substring(0, path.Length - 1);
            }
            return lister(path);
        }

        public delegate object ListFiles(string nativePath);

        internal static string relativePath(string path)
        {
            FsType fsType;
            bool isdir;
            return relativePath(path, out fsType, out isdir);
        }

        internal static string relativePath(string path, out FsType fsType)
        {
            bool isdir;
            return relativePath(path, out fsType, out isdir);
        }

        internal static string relativePath(string path, out FsType fsType, out bool isdir)
        {
            isdir = false;
            fsType = FsType.Local;

            while (path.StartsWith(NATIVE_PATHSEP))
            {
                path = path.Substring(1);
            }

            if (path.EndsWith(NATIVE_PATHSEP))
            {
                path = path.Substring(0, path.Length - 1);
                isdir = true;
            }

            if (path.StartsWith(FS_SDCARD_ROOT))
            {
                path = path.Substring(FS_SDCARD_ROOT.Length);
                fsType = FsType.Card;
            }
            else if (path.StartsWith(FS_SKYDRIVE_ROOT))
            {
                path = path.Substring(FS_SKYDRIVE_ROOT.Length);
                fsType = FsType.Skydrive;
            }
            else if (path.StartsWith(FS_DEVICE_ROOT))
            {
                path = path.Substring(FS_DEVICE_ROOT.Length);
            }

            while (path.StartsWith(NATIVE_PATHSEP))
            {
                path = path.Substring(1);
            }
            return path;
        }

        private static object ListLocalFiles(string nativePath)
        {
            return convertArray(LocalStorage.ListFiles(nativePath));
        }

        private static object ListCardFiles(string nativePath)
        {
            return convertArray(CardStorage.ListFiles(nativePath));
        }

        private static object ListSkydriveFiles(string nativePath)
        {
#if LOG
            CN1Extensions.Log("Impl.listSkydriveFiles in {0}", nativePath);
#endif
            if (String.IsNullOrEmpty(nativePath) && !SkydriveStorage.ConnectionPossible)
            {
                return convertArray(new string[0]);
            }
            return convertArray(SkydriveStorage.ListSkydriveFiles(nativePath));
        }

        public override long getRootSizeBytes(java.lang.String n1)
        {
            return 0;
        }

        public override long getRootAvailableSpace(java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                return store.Quota;
            }
        }

        public override void mkdir(java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                store.CreateDirectory(nativePath(n1));
            }
        }

        public override void deleteFile(java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                store.DeleteFile(nativePath(n1));
            }
        }

        public override bool isHidden(java.lang.String n1)
        {
            return false;
        }

        public override void setHidden(java.lang.String n1, bool n2)
        {
        }

        public override long getFileLength(java.lang.String n1)
        {
            n1 = System.Net.HttpUtility.UrlDecode(((java.lang.String)n1).toCSharp()).toJava();
            long l;
            bool isdir;
            FsType fsType;
            string natPath = nativePath((java.lang.String)n1);
            string path = relativePath(natPath, out fsType, out isdir);
            if (fsType == FsType.Local)
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    using (IsolatedStorageFileStream f = store.OpenFile(path, FileMode.Open, FileAccess.Read))
                    {
                        l = f.Length;
                    }
                }
            }
            else
            {
                l = 0;
            }
            return l;
        }

        public override bool isDirectory(java.lang.String n1)
        {
            bool isdir;
            FsType fsType;
            string path = relativePath(nativePath(n1), out fsType, out isdir);
            if (fsType == FsType.Local)
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    return store.DirectoryExists(path);
                }
            }
            else
            {
                return isdir;
            }
        }

        public override bool exists(java.lang.String n1)
        {
            n1 = System.Net.HttpUtility.UrlDecode(((java.lang.String)n1).toCSharp()).toJava();
            bool isdir, result = false;
            FsType fsType;
            string path = relativePath(nativePath(n1), out fsType, out isdir);
#if LOG
            CN1Extensions.Log("Impl.exists? {0}; fs type? {1}", path, fsType);
#endif
            switch (fsType)
            {
                case FsType.Local:
                    {
                        result = LocalStorage.Exists(path, isdir);
                    }
                    break;

                case FsType.Card:
                    {
                        result = CardStorage.Exists(path, isdir);
                    }
                    break;

                case FsType.Skydrive:
                    {
                        // TODO
                    }
                    break;
            }

            return result;
        }

        public override void rename(java.lang.String n1, java.lang.String n2)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                store.MoveFile(nativePath(n1), nativePath(n2));
            }
        }

        public override char getFileSystemSeparator()
        {
            return NATIVE_FSSEP;
        }

        #endregion

        internal static FsType GetFsType(string uri)
        {
            FsType fsType = FsType.None;
            string path = nativePath(uri.toJava());
            if (path.StartsWith(FS_SDCARD_ROOT))
            {
                fsType = FsType.Card;
            }
            else if (path.StartsWith(FS_SKYDRIVE_ROOT))
            {
                fsType = FsType.Skydrive;
            }
            return fsType;
        }
    }
}