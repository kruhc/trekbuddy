using System;
using System.Threading.Tasks;
using System.Windows;

using org.xmlvm;
using net.trekbuddy.wp8;
using com.codename1.impl;
using AppResources = TrackingApp.Resources.AppResources;

namespace TrackingApp
{
    internal class FileBrowserHelper
    {
        private static string CMD_UPLOAD = AppResources.CommandUpload;
        private static string CMD_DELETE = AppResources.CommandDelete;

        private static readonly string[] FB_CMDS = { CMD_UPLOAD, CMD_DELETE };
        private static readonly string[] FF_CMDS = { CMD_DELETE };
        private static readonly string[] WY_CMDS = { CMD_UPLOAD };

        private FileBrowserHelper()
        {
        }

        public static bool IsActive(object contextObject)
        {
#if LOG
            CN1Extensions.Log("FileBrowserHelper.isActive? {0}", contextObject != null ? ((java.lang.String)((java.lang.Object)contextObject).toString()).toCSharp() : "no context object");
#endif
            if (contextObject == null)
            {
                return false;
            }
            if (contextObject is cz.kruch.track.ui.FileBrowser && GetCurrentUrl(contextObject) == null) // at root
            {
#if LOG
                CN1Extensions.Log("FileBrowserHelper.isActive; current URL is null");
#endif
                return false;
            }
            return true;
        }

        public static bool IsFile(string name)
        {
            return !name.EndsWith("/") && !name.Equals("..");
        }

        public static string[] GetContextCommands(object contextObject, string fileName)
        {
            if (contextObject is java.lang.String)
            {
                return WY_CMDS;
            }
            else if (contextObject is cz.kruch.track.ui.FileBrowser)
            {
                if (".." == fileName)
                {
                    return null;
                }
                string url = GetCurrentUrl(contextObject);
                bool isdir;
                SilverlightImplementation.FsType fsType;
                string natPath = SilverlightImplementation.nativePath(url.toJava());
                string path = SilverlightImplementation.relativePath(natPath, out fsType, out isdir);
                if (fsType == SilverlightImplementation.FsType.Local)
                {
                    if (fileName.EndsWith("/"))
                    {
                        return FF_CMDS;
                    }
                    else
                    {
                        return FB_CMDS;
                    }
                }
                return null;
            }
            throw new ArgumentException("contextObject: " + contextObject);
        }

        private static string GetCurrentUrl(object contextObject)
        {
            java.lang.String javaUrl;
            if (contextObject is java.lang.String)
            {
                javaUrl = cz.kruch.track.configuration.Config.getFolderURL(contextObject as java.lang.String) as java.lang.String;
            }
            else if (contextObject is cz.kruch.track.ui.FileBrowser)
            {
                javaUrl = (contextObject as cz.kruch.track.ui.FileBrowser).getCurrentURL() as java.lang.String;
            }
            else
            {
                throw new ArgumentException("contextObject");
            }
            if (javaUrl == null)
            {
                return null;
            }
            return javaUrl.toCSharp();
        }

        public static string GetQuestion(object contextObject, string cmd, string item)
        {
            string result;
            if (cmd == CMD_UPLOAD) {
                result = String.Format("{0} {1} to OneDrive?", cmd, item);
            } else if (cmd == CMD_DELETE) {
                result = String.Format("{0} {1}?", cmd, item);
            } else {
                result = String.Format("Unsupported action: {0}", cmd);
            }
            return result;
        }

        public static async Task Action(object contextObject, string cmd, string item)
        {
            string url = GetCurrentUrl(contextObject);
            string natPath = SilverlightImplementation.nativePath((url + item).toJava());
            string path = SilverlightImplementation.relativePath(natPath);
            string targetPath = (new Uri(url)).AbsolutePath;
            if (targetPath.StartsWith("/")) // strip leading '/'
                targetPath = targetPath.Substring(1);
            if (targetPath.EndsWith("/")) // strip ending '/'
                targetPath = targetPath.Substring(0, targetPath.LastIndexOf('/'));
#if LOG
            CN1Extensions.Log("FileBrowserHelper.action {0} {1} {2} ({3}) {4}", cmd, item, url, path, targetPath);
#endif
            if (cmd == CMD_UPLOAD) { // called from UI thread!!!
                    object result = false;
                    await Task.Factory.StartNew(() =>
                    {
                        try
                        {
                            if (SkydriveStorage.ConnectionPossible)
                            {
                                result = SkydriveStorage.UploadFile(path, item, targetPath);
#if LOG
                                CN1Extensions.Log("FileBrowserHelper.action upload; result: {0}", result);
#endif
                            }
                        }
                        catch (Exception e)
                        {
                            result = e;
                        }
                    });
                    if (result is bool)
                    {
                        MessageBox.Show((bool)result ? AppResources.OneDriveUploadSuccessMessage : String.Format(AppResources.OneDriveUploadFailedMessage, result),
                                        AppResources.ApplicationTitle, MessageBoxButton.OK);
                    }
                    else
                    {
                        MessageBox.Show(String.Format(AppResources.OneDriveUploadFailedMessage, result),
                                        AppResources.ApplicationTitle, MessageBoxButton.OK);
                    }
            } else if (cmd == CMD_DELETE) {
                if (item.EndsWith("/")) 
                {
                    net.trekbuddy.wp8.LocalStorage.DeleteFolder(path);
                }
                else
                {
                    net.trekbuddy.wp8.LocalStorage.DeleteFile(path);
                }
#if LOG
                CN1Extensions.Log("FileBrowserHelper.action delete; {0}", path);
#endif
                (contextObject as cz.kruch.track.ui.FileBrowser).refresh();
            }
        }

        public static void Close(object contextObject)
        {
            if (contextObject is cz.kruch.track.ui.FileBrowser)
            {
                (contextObject as cz.kruch.track.ui.FileBrowser).quit();
            }
        }
    }
}