using System;
using System.IO;
using System.IO.IsolatedStorage;
using System.Threading.Tasks;

using org.xmlvm;
using TrackingApp;

namespace net.trekbuddy.wp8
{
    // TODO switch to Windows.Storage API

    internal class LocalStorage
    {
        private static string[] EMPTY_RESULT = new string[0];

        private LocalStorage()
        {
        }

        internal static string[] ListFiles(string nativePath)
        {
#if LOG
            CN1Extensions.Log("LocalStorage.listFiles in {0}", nativePath);
#endif
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                if (store.DirectoryExists(nativePath))
                {
                    string[] dirnames = store.GetDirectoryNames(Path.Combine(nativePath, "*"));
                    for (int N = dirnames.Length, i = 0; i < N; i++)
                    {
                        if (!dirnames[i].EndsWith("/"))
                        {
                            dirnames[i] = dirnames[i] + "/";
                        }
                    }
                    string[] filenames = store.GetFileNames(Path.Combine(nativePath, "*"));
                    string[] all = new string[dirnames.Length + filenames.Length];
                    dirnames.CopyTo(all, 0);
                    filenames.CopyTo(all, dirnames.Length);
#if LOG
                    foreach (string item in all)
                    {
                        CN1Extensions.Log("  '{0}'", item);
                    }
#endif
                    return all;
                }
                else
                {
                    return EMPTY_RESULT;
                }
            }
        }

        public static bool Exists(string path, bool isdir)
        {
            bool result = false;
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                result = store.FileExists(path) || store.DirectoryExists(path);
            }
            return result;
        }

        public static Stream OpenInputStream(string path)
        {
#if LOG
            CN1Extensions.Log("LocalStorage.openInputStream: {0}", path);
#endif
            Stream stream = null;
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                IsolatedStorageFileStream s = store.OpenFile(path, FileMode.Open, FileAccess.Read);
                stream = s;
            }
            return stream;
        }

        public static Stream OpenOutputStream(string path)
        {
            Stream stream = null;
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                IsolatedStorageFileStream s = store.OpenFile(path, FileMode.OpenOrCreate, FileAccess.Write);
                stream = s;
            }
            return stream;
        }
    }
}
