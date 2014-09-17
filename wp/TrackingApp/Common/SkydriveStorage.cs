using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using Microsoft.Live;
using Microsoft.Phone.Net.NetworkInformation;

using com.codename1.impl;
using org.xmlvm;

using TrackingApp;
using UISynchronizationContext = net.trekbuddy.wp8.ui.UISynchronizationContext;
using AppResources = TrackingApp.Resources.AppResources;

namespace net.trekbuddy.wp8
{
    internal sealed class SkydriveStorage
    {
        private static readonly string CLIENT_ID = "0000000040103E4E";
        private static readonly string[] SCOPES = { "wl.skydrive", "wl.skydrive_update" };
        private static readonly string TYPE_FOLDER = "folder";

        private LiveConnectClient client;

        private IDictionary<string, string> fileIds;
        private IDictionary<string, int> fileSizes;

        private static SkydriveStorage instance;

        class Match
        {
            public bool Exact;
            public string Path, Id;
        }

        public static bool Connected
        {
            get { return instance != null; }
        }

        public static bool ConnectionPossible
        {
            get
            {
                if (instance != null)
                {
                    return true;
                }
                UIHelper.showProgressBar(AppResources.OneDriveCheckProgress);
                try
                {
                    NetworkInterfaceType ifaceType = NetworkInterface.NetworkInterfaceType;
#if LOG
                    CN1Extensions.Log("Sky.connectionCheck; inet iface: {0}", NetworkInterface.NetworkInterfaceType);
#endif
                    return ifaceType != NetworkInterfaceType.None;
                }
                finally
                {
                    UIHelper.showProgressBar(null);
                }
            }
        }

        // TODO fix async vs wait
        public static SkydriveStorage GetSkydrive()
        {
            if (instance == null)
            {
                Task<LiveConnectClient> task = null;
                UISynchronizationContext.Dispatcher.InvokeSync(() =>
                {
                    task = InitializeAsync();
                });
                task.SafeWait(AppResources.OneDriveInitFailedMessage);
#if LOG
                CN1Extensions.Log("skydrive client: {0}", task.Result);
#endif
                if (task.Result != null)
                {
                    instance = new SkydriveStorage(task.Result);
                }
            }
            return instance;
        }

        private SkydriveStorage(LiveConnectClient client)
        {
            this.client = client;
            this.fileIds = new Dictionary<string, string>(16);
            this.fileIds[""] = "me/skydrive";
            this.fileSizes = new Dictionary<string, int>(16);
        }

        private static async Task<LiveConnectClient> InitializeAsync()
        {
            LiveConnectClient client = null;
            LiveAuthClient auth = new LiveAuthClient(CLIENT_ID);
            LiveLoginResult result = await auth.InitializeAsync(SCOPES);
            if (result.Status == LiveConnectSessionStatus.Connected)
            {
                client = new LiveConnectClient(result.Session);
            }
            else
            {
                result = await auth.LoginAsync(SCOPES);
                if (result.Status == LiveConnectSessionStatus.Connected)
                {
                    client = new LiveConnectClient(result.Session);
                }
                else
                {
                    MessageBox.Show(String.Format(AppResources.OneDriveLoginFailedMessage, result.Status),
                                    AppResources.ApplicationTitle, MessageBoxButton.OK);
                }
            }
            return client;
        }

        private async Task<List<string>> ListFiles(string nativePath)
        {
            nativePath = nativePath.Replace('\\', '/');
            string path = fileIds[nativePath];
            LiveOperationResult operationResult = await client.GetAsync(path + "/files").ConfigureAwait(false);
            List<object> data = operationResult.Result["data"] as List<object>;
            List<string> result = new List<string>();
            foreach (IDictionary<string, object> content in data)
            {
                string type = (string)content["type"];
                string filename = (string)content["name"];
                string fileId = (string)content["id"];
                int fileSize = (int)content["size"];
#if LOG
                CN1Extensions.Log("item {0} type {1} id {2} size {3}", filename, type, fileId, fileSize);
#endif
                if (TYPE_FOLDER == type)
                {
                    result.Add(filename + "/");
                }
                else
                {
                    result.Add(filename);
                }

                if (String.IsNullOrEmpty(nativePath))
                {
                    fileIds[filename] = fileId;
                }
                else
                {
                    fileIds[nativePath + "/" + filename] = fileId;
                }

                fileSizes[fileId] = fileSize;
            }
            return result;
        }

        internal static string[] ListSkydriveFiles(string nativePath)
        {
#if LOG
            CN1Extensions.Log("Sky.listFiles in {0}", nativePath);
#endif
            string[] result;
            SkydriveStorage skydrive = GetSkydrive();
            if (skydrive == null)
            {
                result = new string[0];
            }
            else
            {
                UIHelper.showProgressBar(AppResources.OneDriveListingFolderProgress);
                try
                {
                    result = skydrive.ListFiles(nativePath).SafeWait("Sky.listSkydriveFiles failed; ").ToArray();
                }
                finally
                {
                    UIHelper.showProgressBar(null);
                }
            }

            return result;
        }

        internal static bool UploadFile(string localPath, string targetItem, string targetPath = null)
        {
            SkydriveStorage skydrive = GetSkydrive();
            if (skydrive == null)
            {
                return false;
            }

            bool result = false;
            UIHelper.showProgressBar(AppResources.OneDriveUploadingProgress);
            try
            {
                result = skydrive.UploadFileImpl(localPath, targetItem, targetPath).SafeWait();
            }
            finally
            {
                UIHelper.showProgressBar(null);
            }

            return result;
        }

        internal static string CreateFolder(string name, string fullPath, string parentId = null, bool forceCreation = false)
        {
            SkydriveStorage skydrive = GetSkydrive();
            if (skydrive == null)
            {
                return null;
            }

            return skydrive.CreateFolder(fullPath, parentId, forceCreation).SafeWait();
        }

        internal static bool CopyFolder(Uri source, string targetPath, out string destPath)
        {
            destPath = null;

            SkydriveStorage skydrive = GetSkydrive();
            if (skydrive == null)
            {
                return false;
            }

            string fileName = null, relPath = null, folderPath = null;
            GetTargetRelPath(source, targetPath, out fileName, out relPath, out folderPath);
            destPath = targetPath + "/" + relPath + "/" + fileName;

            bool ownProgress = UIHelper.showProgressBar(AppResources.OneDriveCopyingFilesProgress, true);
            try
            {
                skydrive.CopyFolder(relPath, skydrive.fileIds[folderPath], targetPath).SafeWait();
            }
            finally
            {
                if (ownProgress)
                {
                    UIHelper.showProgressBar(null);
                }
            }

            return true;
        }

        internal static bool CopyFile(Uri source, string targetPath, out string destPath)
        {
            destPath = null;

            SkydriveStorage skydrive = GetSkydrive();
            if (skydrive == null)
            {
                return false;
            }

            string fileName = null, relPath = null, folderPath = null;
            GetTargetRelPath(source, targetPath, out fileName, out relPath, out folderPath);
            destPath = targetPath + "/" + relPath + "/" + fileName;

            UIHelper.showProgressBar(AppResources.OneDriveCopyingFileProgress);
            try
            {
                skydrive.CopyFile(fileName, skydrive.fileIds[folderPath + "/" + fileName], 
                    Path.Combine(targetPath, relPath)).SafeWait();
            }
            finally
            {
                UIHelper.showProgressBar(null);
            }

            return true;
        }

        private static void GetTargetRelPath(Uri source, string pathBase, 
            out string fileName, out string relPath, out string folderPath)
        {
            fileName = Path.GetFileName(source.LocalPath);
            folderPath = source.LocalPath;
            folderPath = folderPath.Substring(("/" + SilverlightImplementation.FS_SKYDRIVE_ROOT + "/").Length);
            folderPath = folderPath.Substring(0, folderPath.Length - fileName.Length - "/".Length);
            int tbfsIdx = folderPath.LastIndexOf(pathBase);
            if (tbfsIdx > -1)
            {
                relPath = folderPath.Substring(tbfsIdx + pathBase.Length);
                if (relPath.StartsWith("/"))
                {
                    relPath = relPath.Substring("/".Length);
                }
            }
            else
            {
                relPath = folderPath;
            }
        }

        private delegate Task<string> DoWithParent(string parentPath, string parentId, string name);

        private async Task<Match> ItemExists(string path, DoWithParent action = null)
        {
            if (fileIds.ContainsKey(path)) // try luck
            {
                return new Match { Exact = true, Path = path, Id = fileIds[path] };
            }

            Match match = new Match();
            string[] fragments = path.Split('/');
            string partial = "", parent = "", id = null;
            int idx = 0, N = fragments.Length;
            for ( ; idx < N; idx++)
            {
                parent = partial;
                if (String.IsNullOrEmpty(partial))
                {
                    partial = fragments[idx];
                }
                else
                {
                    partial += "/" + fragments[idx];
                }
                if (fileIds.ContainsKey(partial))
                {
                    match.Exact = path == partial;
                    match.Path = partial;
                    match.Id = id = fileIds[partial];
                }
                else
                {
                    break;
                }
            }
            for ( ; idx < N; )
            {
                await ListFiles(parent).ConfigureAwait(false);
                if (fileIds.ContainsKey(partial))
                {
                    match.Exact = path == partial;
                    match.Path = partial;
                    match.Id = id = fileIds[partial];
                }
                else
                {
                    if (action == null)
                    {
                        break;
                    }
                    else
                    {
                        id = await action(parent, id, fragments[idx]).ConfigureAwait(false);
                        if (id == null)
                        {
                            break;
                        }
                        match.Exact = path == partial;
                        match.Path = partial;
                        match.Id = id;
                    }
                }
                parent = partial;
                if (idx < N - 1)
                {
                    partial += "/" + fragments[++idx];
                }
                else
                {
                    break;
                }
            }
            return match;
        }

        private async Task<bool> UploadFileImpl(string localPath, string targetItem, string targetPath)
        {
            string parentId = !string.IsNullOrEmpty(targetPath) && fileIds.ContainsKey(targetPath) ? fileIds[targetPath] : "me/skydrive";
#if LOG
            CN1Extensions.Log("Upload file {0} to {1}[{2}]/{3}", localPath, targetPath, parentId, targetItem);
#endif
            string displayPath = localPath.Length > 16 ? "..." + localPath.Substring(localPath.Length - 16) : localPath;
            UIHelper.updateProgressBar(String.Format(AppResources.OneDriveUploadingProgressPercent, displayPath, 0));
            var progressHandler = new Progress<LiveOperationProgress>((progress) =>
            {
                System.Diagnostics.Debug.WriteLine("Progress: {0} ", progress.ProgressPercentage);
                UIHelper.updateProgressBar(String.Format(AppResources.OneDriveUploadingProgressPercent, 
                                           displayPath, (int)progress.ProgressPercentage));
            });

            using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
            {
                using (Stream stream = instance.OpenFile(localPath, FileMode.Open, FileAccess.Read))
                {
                    var ct = new CancellationToken();
                    LiveOperationResult operationResult = await client.UploadAsync(parentId, targetItem, stream,
                        OverwriteOption.Overwrite, ct, progressHandler).ConfigureAwait(false);
#if LOG
                    CN1Extensions.Log("Upload result: {0}", operationResult.RawResult);
#endif
                }
            }
#if LOG
            CN1Extensions.Log("Uploading file {0} completed", localPath);
#endif

            return true;
        }

        // TODO param 'id' (actually parentId) is unused!!!
        private async Task<string> CreateFolder(string path, string id, bool create)
        {
#if LOG
            CN1Extensions.Log("Creating folder {0} with existence check first", path);
#endif
            Match found = await ItemExists(path, create ? async (string parentPath, string parentId, string name) =>
            {
#if LOG
                CN1Extensions.Log("Does not exist, creating, in parent {1}[{2}]", name, parentPath, parentId);
#endif
                var folderData = new Dictionary<string, object>();
                folderData.Add("name", name);
                LiveOperationResult operationResult = await client.PostAsync(parentId == null ? "me/skydrive" : parentId, folderData);
                var result = operationResult.Result;
#if LOG
                CN1Extensions.Log("Created folder {0} id {1}", result["name"], result["id"]);
#endif
                return (string)result["id"];
            } : (DoWithParent)null).ConfigureAwait(false);

            if (found.Exact)
            {
                return found.Id;
            }
            return null;
        }

        private async Task CopyFolder(string name, string id, string targetPath)
        {
            string destPath = name == null ? targetPath : Path.Combine(targetPath, name);
#if LOG
            CN1Extensions.Log("Copying folder {0} to {1}", name, destPath);
#endif

            using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
            {
                if (!instance.DirectoryExists(destPath))
                {
#if LOG
                    CN1Extensions.Log("Creating folder folder {0}", destPath);
#endif
                    instance.CreateDirectory(destPath);
                }
            }

            LiveOperationResult result = await client.GetAsync(id + "/files").ConfigureAwait(false);
            List<object> data = result.Result["data"] as List<object>;
            foreach (IDictionary<string, object> content in data)
            {
                string type = (string)content["type"];
                string filename = (string)content["name"];
                string fileId = (string)content["id"];
                DateTime updated;
                bool useUpdated = DateTime.TryParse((string)content["updated_time"], out updated);
#if LOG
                CN1Extensions.Log("item {0} type {1} id {2}", filename, type, fileId);
#endif
                if ("folder".Equals(type))
                {
                    await CopyFolder(filename, fileId, destPath).ConfigureAwait(false);
                }
                else
                {
                    await CopyFile(filename, fileId, destPath, useUpdated ? updated : (DateTime?)null).ConfigureAwait(false);
                }
            }

        }

        private async Task CopyFile(string name, string id, string targetPath, DateTime? remoteUpdated = null)
        {
            string destPath = Path.Combine(targetPath, name);
            int fileSize = fileSizes[id];
            long realSize = 0;
#if LOG
            CN1Extensions.Log("Downloading file {0} of size {1} to {2}", name, fileSize, destPath);
#endif

            if (remoteUpdated.HasValue)
            {
                using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    if (instance.FileExists(destPath))
                    {
                        DateTimeOffset localUpdated = instance.GetLastWriteTime(destPath);
#if LOG
                        CN1Extensions.Log("File {0} exists, last updated {1}, remote updated {2}", name, localUpdated,remoteUpdated);
#endif
                        if (localUpdated.CompareTo(new DateTimeOffset(remoteUpdated.Value)) > 0)
                        {
#if LOG
                            CN1Extensions.Log("Local file is newer");
#endif
                            return;
                        }
                    }
                }
            }

            using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
            {
                if (!instance.DirectoryExists(targetPath))
                {
#if LOG
                    CN1Extensions.Log("Creating folder folder {0}", targetPath);
#endif
                    instance.CreateDirectory(targetPath);
                }
            }

            UIHelper.updateProgressBar(String.Format(AppResources.OneDriveDownloadingProgressPercent, name, 0));
            var progressHandler = new Progress<LiveOperationProgress>((progress) =>
                {
                    float totalProgress = (float)realSize / (float)fileSize * 100;
#if LOG
                    CN1Extensions.Log("Progress: {0}, total: {1} ", progress.ProgressPercentage, totalProgress);
#endif
                    UIHelper.updateProgressBar(String.Format(AppResources.OneDriveDownloadingProgressPercent, 
                                               name, (int)/*progress.ProgressPercentage*/totalProgress));
                });

            var ct = new CancellationToken();
            LiveDownloadOperationResult result = await client.DownloadAsync(id + "/content", ct, progressHandler).ConfigureAwait(false);
            using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
            {
                using (Stream output = instance.OpenFile(destPath, FileMode.Create, FileAccess.Write))
                {
                    using (Stream input = result.Stream)
                    {
                        input.CopyTo(output);
                    }
                }
            }
#if LOG
            CN1Extensions.Log("Downloading file {0} finished", name);
#endif
            using (IsolatedStorageFile instance = IsolatedStorageFile.GetUserStoreForApplication())
            {
                using (IsolatedStorageFileStream stream = instance.OpenFile(destPath, FileMode.Open, FileAccess.Read))
                {
                    realSize = stream.Length;
                }
            }
#if LOG
            CN1Extensions.Log("Downloaded file {0} of size {1}, expected {2}", destPath, realSize, fileSize);
#endif
        }
    }
}
