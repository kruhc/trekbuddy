using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Phone.Storage;

using org.xmlvm;
using TrackingApp;

namespace net.trekbuddy.wp8
{
    // TODO move external storage code from SilverlightImplementation here

    internal class CardStorage
    {
        private ExternalStorageDevice card;

        private static CardStorage instance;

        public static bool Available
        {
            get
            {
                if (instance == null)
                {
                    instance = new CardStorage();
                    instance.Initialize();
                }
                return instance.card != null;
            }
        }

        public static ExternalStorageDevice Card
        {
            get { return instance.card; }
        }

        private CardStorage()
        {
        }

        private void Initialize()
        {
            var task = Task.Run(async () =>
            {
                return (await ExternalStorage.GetExternalStorageDevicesAsync().ConfigureAwait(false)).FirstOrDefault();
            });
            try
            {
                task.FastWait();
            }
            catch (Exception e)
            {
#if LOG
                CN1Extensions.Log("CardStorage.init failed: {0}", e.Message);
#endif
                throw e;
            }
#if LOG
            CN1Extensions.Log("CardStorage.init result: {0}", task.Result);
#endif
            card = task.Result;
        }

        private async Task<List<string>> ListFilesImpl(string nativePath)
        {
#if LOG
            CN1Extensions.Log("CardStorage.listCardFiles in {0}", nativePath);
#endif
            List<string> result = new List<string>();
            ExternalStorageFolder folder = nativePath.Length == 0 ? card.RootFolder : await card.GetFolderAsync(nativePath).ConfigureAwait(false);
            IEnumerable<ExternalStorageFolder> folders = await folder.GetFoldersAsync().ConfigureAwait(false);
            IEnumerable<ExternalStorageFile> files = await folder.GetFilesAsync().ConfigureAwait(false);
            foreach (ExternalStorageFolder item in folders)
            {
#if LOG
                CN1Extensions.Log("CardStorage.listCardFiles found folder {0}", item.Name);
#endif
                result.Add(item.Name + "/");
            }
            foreach (ExternalStorageFile item in files)
            {
#if LOG
                CN1Extensions.Log("CardStorage.listCardFiles found file {0}", item.Name);
#endif
                result.Add(item.Name);
            }
            return result;
        }

        public static string[] ListFiles(string nativePath)
        {
#if LOG
            CN1Extensions.Log("CardStorage.listFiles in {0}", nativePath);
#endif
            string[] result;
            CardStorage storage = instance;
            if (storage == null)
            {
                result = new string[0];
            }
            else
            {
                result = instance.ListFilesImpl(nativePath).SafeWait("CardStorage.listSkydriveFiles failed; ").ToArray();
            }
            return result;
        }

        public static bool Exists(string path, bool isdir)
        {
            bool result = false;
            var task = System.Threading.Tasks.Task.Run(async () =>
            {
                if (isdir)
                {
                    ExternalStorageFolder folder = await CardStorage.Card.GetFolderAsync(path).ConfigureAwait(false);
                    result = true;
                }
                else
                {
                    using (ExternalStorageFile file = await CardStorage.Card.GetFileAsync(path).ConfigureAwait(false))
                    {
                        result = true;
                    }
                }
            });
            try
            {
                task.FastWait();
            }
            catch (Exception e)
            {
                // ignore
#if LOG
                CN1Extensions.Log("CardStorage.exists failed; {0}", e.Message);
#endif
            }
            return result;
        }

        public static Stream OpenInputStream(string path)
        {
#if LOG
            CN1Extensions.Log("CardStorage.openInputStream: {0}", path);
#endif
            var task = System.Threading.Tasks.Task.Run(async () =>
            {
                using (ExternalStorageFile file = await CardStorage.Card.GetFileAsync(path).ConfigureAwait(false))
                {
                    return await file.OpenForReadAsync().ConfigureAwait(false);
                }
            });
            return task.FastWait();
        }

        public static bool CopyFile(Uri source, string destPath, out string destName)
        {
            string sourcePath = source.AbsolutePath;
            destName = sourcePath.Substring(sourcePath.LastIndexOf('/'));
            using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
            {
                using (Stream output = instance.OpenFile(destPath, FileMode.OpenOrCreate, FileAccess.Write))
                {
                    var task = System.Threading.Tasks.Task.Run(async () =>
                    {
                        using (ExternalStorageFile file = await CardStorage.Card.GetFileAsync(source.AbsolutePath).ConfigureAwait(false))
                        {
                            using (Stream input = await file.OpenForReadAsync().ConfigureAwait(false))
                            {
                                input.CopyTo(output);
                            }
                        }
                    });
                    task.FastWait();
                }
            }
            return true;
        }
    }
}
