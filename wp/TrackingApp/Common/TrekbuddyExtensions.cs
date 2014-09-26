//#define __USE_WBMP          // use WritableBitmap as Canvas backend

using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Resources;
using Microsoft.Phone.BackgroundAudio;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Storage;
using Microsoft.Phone.Shell;
using Microsoft.Phone.Tasks;

using ICSharpCode.SharpZipLib.Tar;
using org.xmlvm;
using com.codename1.impl;
using cz.kruch.track.configuration;

using TrackingApp;
using UISynchronizationContext = net.trekbuddy.wp8.ui.UISynchronizationContext;
using FsType = com.codename1.impl.SilverlightImplementation.FsType;
using AppResources = TrackingApp.Resources.AppResources;

namespace net.trekbuddy.wp8
{
    internal class TrekbuddyExtensions
    {
        const string FOLDER_RESOURCES   = @"TrekBuddy\resources";
        const string FOLDER_SOUNDS      = @"TrekBuddy\sounds";
        const string FOLDER_UI_PROFILES = @"TrekBuddy\ui-profiles";
        const string FOLDER_WPTS        = @"TrekBuddy\wpts";

        const string ALERT_SOUND        = "MIDPAlert.mp3";

        private static Exception noUiMirrorError;

        internal static global::System.Object execute(SilverlightImplementation instance, java.lang.String n1, System.Object[] n2)
        {
            global::System.Object javaResult = null;

            string action = n1.toCSharp();
#if LOG
            CN1Extensions.Log("execute: {0}", action);
#endif
            switch (action)
            {
                case "mirror-sd":
                    {
                        bool isUi = (n2[0] as java.lang.Boolean).booleanValue();
                        if (isUi)
                        {
                            Exception mirrorError = noUiMirrorError;
                            if (mirrorError == null)
                            {
                                if (CardStorage.Available)
                                {
                                    UIHelper.showProgressBar(AppResources.MemoryCardSyncProgress);
                                    try
                                    {
                                        var sd = CardStorage.Card;
                                        UnpackToLocal(sd, FOLDER_UI_PROFILES, ".tar");
                                        CopyToLocal(sd, FOLDER_WPTS, ".gpx", true);
                                    }
                                    catch (Exception e)
                                    {
                                        mirrorError = e;
                                    }
                                    finally
                                    {
                                        UIHelper.showProgressBar(null);
                                    }
                                }
                            }
                            if (mirrorError != null)
                            {
                                UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                                    MessageBox.Show(String.Format(AppResources.MemoryCardSyncFailedMessage, mirrorError),
                                                    AppResources.ApplicationTitle, MessageBoxButton.OK));
                            }
                        }
                        else
                        {
                            try
                            {
                                cz.kruch.track.configuration.Config.initDataDir();
                                UnjarToLocal(FOLDER_SOUNDS, "Audio/" + ALERT_SOUND);
                                if (CardStorage.Available)
                                {
                                    var sd = CardStorage.Card;
                                    UnpackToLocal(sd, FOLDER_RESOURCES, ".tar");
                                    UnpackToLocal(sd, FOLDER_SOUNDS, ".tar");
                                }
                            }
                            catch (Exception e)
                            {
                                noUiMirrorError = e;
                            }
                        }
                    }
                    break;
                case "mirror-sky":
                    {
                        bool isUi = (n2[0] as java.lang.Boolean).booleanValue();
                        bool skySync = false, skyCreate = false;
                        using (EventWaitHandle wait = new EventWaitHandle(false, EventResetMode.ManualReset))
                        {
                            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                            {
                                CheckBox checkBox = new CheckBox()
                                {
                                    Content = AppResources.OneDriveCreateFolderCheckbox,
                                    FontSize = 20, // FontSizeNormal
                                    IsChecked = true,
                                    IsEnabled = true
                                };
                                CustomMessageBox messageBox = new CustomMessageBox()
                                {
                                    Caption = AppResources.ApplicationTitle,
                                    Message = AppResources.OneDriveSyncQuestion,
                                    Content = checkBox,
                                    LeftButtonContent = "OK",
                                    RightButtonContent = AppResources.ButtonCancel,
                                    FontSize = 22.667 // FontSizeMedium
                                };
                                messageBox.Dismissed += (s1, e1) =>
                                {
                                    skySync = e1.Result == CustomMessageBoxResult.LeftButton;
                                    skyCreate = (bool)((s1 as CustomMessageBox).Content as CheckBox).IsChecked;
                                    wait.Set();
                                };
                                messageBox.Show();
                            });
                            wait.WaitOne();
                        }
                        if (skySync)
                        {
                            if (SkydriveStorage.ConnectionPossible)
                            {
                                SkydriveStorage.GetSkydrive();
                                bool skyHasFolders = false;
                                UIHelper.showProgressBar(AppResources.OneDriveSyncProgress);
                                try
                                {
                                    skyHasFolders = InitSkydriveDataDir(skyCreate);
                                    if (skyHasFolders)
                                    {
                                        string destPath;
                                        SkydriveStorage.CopyFolder(new Uri("file:///OneDrive/TrekBuddy/sounds/"), "TrekBuddy/sounds", out destPath);
                                        SkydriveStorage.CopyFolder(new Uri("file:///OneDrive/TrekBuddy/resources/"), "TrekBuddy/resources", out destPath);
                                        SkydriveStorage.CopyFolder(new Uri("file:///OneDrive/TrekBuddy/ui-profiles/"), "TrekBuddy/ui-profiles", out destPath);
                                        SkydriveStorage.CopyFolder(new Uri("file:///OneDrive/TrekBuddy/wpts/"), "TrekBuddy/wpts", out destPath);
                                    }
                                }
                                catch (Exception e)
                                {
                                    UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                                        MessageBox.Show(String.Format(AppResources.OneDriveSyncFailedMessage, 
                                                        e.ToString()), AppResources.ApplicationTitle, MessageBoxButton.OK));
                                }
                                finally
                                {
                                    UIHelper.showProgressBar(null);
                                }
                            }
                        }
                    } 
                    break;
                case "show-progress":
                    {
                        UIHelper.showProgressBar(n2[0] == null ? null : ((java.lang.String)n2[0]).toCSharp());
                    }
                    break;
                case "show-list":
                    {
                        UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                        {
                            Uri currentUri = App.RootFrame.CurrentSource;
                            Uri newUri = new Uri("/MIDP/List.xaml?id=" + n2[0].GetHashCode(), UriKind.Relative);
#if LOG
                            CN1Extensions.Log("current uri: {0}, new uri: {1}", currentUri, newUri);
#endif
                            if (newUri != currentUri)
                            {
                                PhoneApplicationService.Current.State["MIDP.Args"] = n2;
                                App.RootFrame.Navigate(newUri);
                            }
                            else
                            {
                                System.Diagnostics.Debug.WriteLine("{0} already current", newUri);
                            }
                        });
                    }
                    break;
                case "show-form":
                    {
                        UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                        {
                            Uri currentUri = App.RootFrame.CurrentSource;
                            Uri newUri = new Uri("/MIDP/Form.xaml?id=" + n2[0].GetHashCode(), UriKind.Relative);
#if LOG
                            CN1Extensions.Log("current uri: {0}, new uri: {1}", currentUri, newUri);
#endif
                            if (newUri != currentUri)
                            {
                                PhoneApplicationService.Current.State["MIDP.Args"] = n2;
                                App.RootFrame.Navigate(newUri);
                            }
                            else
                            {
                                System.Diagnostics.Debug.WriteLine("{0} already current", newUri);
                            }
                        });
                    }
                    break;
                case "show-canvas":
                    {
                        UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                        {
                            Uri currentUri = App.RootFrame.CurrentSource;
                            Uri newUri = new Uri("/MainPage.xaml", UriKind.Relative);
#if LOG
                            CN1Extensions.Log("current uri: {0}, new uri: {1}", currentUri, newUri);
#endif
                            if (newUri != currentUri)
                            {
                                App.RootFrame.Navigate(newUri);
                            }
                            else
                            {
                                System.Diagnostics.Debug.WriteLine("{0} already current", newUri);
                            }
                        });
                    }
                    break;
                case "show-alert":
                    {
                        UIHelper.showAlert((javax.microedition.lcdui.Alert)n2[0]);
                    }
                    break;
                case "backlight":
                    {
                        bool enabled = ((java.lang.Boolean)n2[0]).booleanValue();
                        PhoneApplicationService.Current.UserIdleDetectionMode = enabled ? IdleDetectionMode.Disabled : IdleDetectionMode.Enabled;
                    }
                    break;
                case "set-opaque":
                    {
                        (n2[0] as CodenameOneImage).OpaqueHint = true;
                    }
                    break;
                case "draw-button":
                    {
                        int x = (n2[1] as java.lang.Integer).intValue();
                        int y = (n2[2] as java.lang.Integer).intValue();
                        int w = (n2[3] as java.lang.Integer).intValue();
                        int h = (n2[4] as java.lang.Integer).intValue();
                        string label = ((java.lang.String)n2[5]).toCSharp();
                        NativeGraphics ng = n2[0] as MutableImageGraphics;
                        ng.paint(new DrawButton(ng, null, x, y, w, h, label));
                    }
                    break;
                case "play-sound":
                    {
                        string path;
                        if (n2.Length == 0)
                        {
#if LOG
                            CN1Extensions.Log("alert sound");
#endif
                            path = System.IO.Path.Combine(FOLDER_SOUNDS, ALERT_SOUND);
                        }
                        else
                        {
#if LOG
                            CN1Extensions.Log("sound file: {0}", (n2[0] as java.lang.String).toCSharp());
#endif
                            path = SilverlightImplementation.relativePath(SilverlightImplementation.nativePath((java.lang.String)n2[0]));
                        }
                        path = path.Replace(SilverlightImplementation.NATIVE_FSSEP, '/');
#if LOG
                        CN1Extensions.Log("play {0}", path);
#endif
                        BackgroundAudioPlayer player = BackgroundAudioPlayer.Instance;
                        if (player.PlayerState == PlayState.Playing)
                        {
                            player.Stop();
                        }
                        player.Track = new AudioTrack(new Uri(path, UriKind.Relative), "unknown", null, null, null);
                        player.Play();
#if LOG
                        CN1Extensions.Log("error? {0}", player.Error);
#endif
                    }
                    break;
/*
                case "OBSOLETE copy-map":
                    {
                        string fileUrl = (n2[0] as java.lang.String).toCSharp();
                        cz.kruch.track.ui.Desktop_2Event callback = (n2[1] as cz.kruch.track.ui.Desktop_2Event);
#if LOG
                        CN1Extensions.Log("copy map {0}", fileUrl);
#endif
                        CustomMessageBoxResult copyAndOpen = CustomMessageBoxResult.None;
                        using (EventWaitHandle wait = new EventWaitHandle(false, EventResetMode.ManualReset))
                        {
                            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                            {
                                CustomMessageBox box = new CustomMessageBox()
                                {
                                    Caption = "TrekBuddy",
                                    Message = "Copy to device and open it?",
                                    LeftButtonContent = "it is an atlas",
                                    RightButtonContent = "it is a map"
                                };
                                box.Dismissed += (s1, e1) =>
                                {
                                    copyAndOpen = e1.Result;
                                    wait.Set();
                                };
                                box.Show();
                            });
                            wait.WaitOne();
                        }
                        if (copyAndOpen != CustomMessageBoxResult.None)
                        {
#if LOG
                            CN1Extensions.Log("yes do copy and open {0}", fileUrl);
#endif
                            Uri uri = new Uri(fileUrl);
                            try
                            {
                                bool success;
                                string destPath;
                                if (copyAndOpen == CustomMessageBoxResult.LeftButton)
                                {
                                    success = SkydriveStorage.CopyFolder(uri, "TrekBuddy/maps", out destPath);
                                }
                                else
                                {
                                    success = SkydriveStorage.CopyFile(uri, "TrekBuddy/maps", out destPath);
                                }
                                if (success)
                                {
                                    uri = new Uri("file:///Local/" + destPath);
                                    var result = new string[] { 
                                        System.IO.Path.GetFileName(uri.LocalPath), 
                                        uri.ToString()
                                    };
                                    callback.invoke((_nArrayAdapter<System.Object>) SilverlightImplementation.convertArray(result), null, null);
                                }
                            }
                            catch (Exception e)
                            {
#if LOG
                                CN1Extensions.Err(e.ToJavaException());
#endif
                                callback.invoke(null, e.ToJavaException(), null);
                            }
                        }
                    }
                    break;
*/
                case "copy-map":
                    {
                        string fileUrl = (n2[0] as java.lang.String).toCSharp();
                        cz.kruch.track.ui.Desktop_2Event callback = (n2[1] as cz.kruch.track.ui.Desktop_2Event);
#if LOG
                        CN1Extensions.Log("copy map {0}", fileUrl);
#endif
                        bool copyAndOpen = false;
                        using (EventWaitHandle wait = new EventWaitHandle(false, EventResetMode.ManualReset))
                        {
                            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                            {
                                copyAndOpen = MessageBox.Show(AppResources.OneDriveUseMapQuestion, AppResources.ApplicationTitle,
                                                              MessageBoxButton.OKCancel) == MessageBoxResult.OK;
                                wait.Set();
                            });
                            wait.WaitOne();
                        }
                        if (copyAndOpen)
                        {
#if LOG
                            CN1Extensions.Log("yes do copy and open {0}", fileUrl);
#endif
                            Uri uri = new Uri(fileUrl);
                            try
                            {
                                string destPath;
                                bool success = SkydriveStorage.CopyFile(uri, "TrekBuddy/maps", out destPath);
                                string destUrl = "file:///Local/" + destPath;
                                if (success)
                                {
                                    if (TarInfo.Open(destUrl, true).IsAtlas)
                                    {
#if LOG
                                        CN1Extensions.Log("{0} is an atlas, copy layers", fileUrl);
#endif
                                        success = SkydriveStorage.CopyFolder(uri, "TrekBuddy/maps", out destPath);
                                    }
#if LOG
                                    else
                                    {
                                        CN1Extensions.Log("{0} is a map", fileUrl);
                                    }
#endif
                                }
                                if (success)
                                {
                                    uri = new Uri(destUrl);
                                    var result = new string[] { 
                                        System.IO.Path.GetFileName(uri.LocalPath), 
                                        uri.ToString()
                                    };
                                    callback.invoke((_nArrayAdapter<System.Object>) SilverlightImplementation.convertArray(result), null, null);
                                }
                            }
                            catch (Exception e)
                            {
#if LOG
                                CN1Extensions.Err(e);
#endif
                                callback.invoke(null, e.ToJavaException(), null);
                            }
                        }
                    }
                    break;
/*
                case "copy-gpx":
                    {
                        string fileName = (n2[0] as java.lang.String).toCSharp();
                        string fileUrl = (n2[1] as java.lang.String).toCSharp();
                        string folder = (n2[2] as java.lang.String).toCSharp();
#if LOG
                        CN1Extensions.Log("copy GPX {0} [{1}] to {2}", fileUrl, fileName, folder);
#endif
                        bool copyAndOpen = false;
                        using (EventWaitHandle wait = new EventWaitHandle(false, EventResetMode.ManualReset))
                        {
                            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
                            {
                                copyAndOpen = MessageBox.Show(string.Format("Copy {0} to iso and open it?", fileUrl), "TrekBuddy", MessageBoxButton.OKCancel) == MessageBoxResult.OK;
                                wait.Set();
                            });
                            wait.WaitOne();
                        }
                        if (copyAndOpen)
                        {
#if LOG
                            CN1Extensions.Log("yes do copy and open {0}", fileUrl);
#endif
                            if (folder.EndsWith("/")) {
                                folder = folder.Substring(0, folder.Length - 1);
                            }
                            Uri uri = new Uri(fileUrl);
                            try
                            {
                                bool success = false;
                                FsType fsType  = SilverlightImplementation.GetFsType(fileUrl);
                                switch (fsType)
                                {
                                    case FsType.Card:
                                        {
                                            string destName;
                                            success = CardStorage.CopyFile(uri, string.Format("TrekBuddy/{0}", folder), out destName);
                                        } break;
                                    case FsType.Skydrive:
                                        {
                                            string destName;
                                            success = SkydriveStorage.CopyFile(uri, string.Format("TrekBuddy/{0}", folder), out destName);
                                        } break;
                                }
                                if (success)
                                {
                                    string destUrl = string.Format("file:///Local/TrekBuddy/{0}/{1}", fileName);
                                    javaResult = destUrl.toJava();
                                }
                            }
                            catch (Exception e)
                            {
#if LOG
                                CN1Extensions.Err(e);
#endif
                                javaResult = e.ToJavaException();
                            }
                        }
                    } break;
*/
                case "send-log": // called from UI thread!!!
                    {
                        System.Threading.Tasks.Task.Factory.StartNew(() =>
                        {
                            if (SkydriveStorage.ConnectionPossible)
                            {
                                string newFileName = "transfer-" + CN1Extensions.logFileName;
                                (com.codename1.io.Log.getInstance() as com.codename1.io.Log).closeWriter();
                                using (IsolatedStorageFile isf = IsolatedStorageFile.GetUserStoreForApplication())
                                {
                                    if (isf.FileExists(newFileName))
                                    {
                                        isf.DeleteFile(newFileName);
                                    }
                                    isf.MoveFile(CN1Extensions.logFileName, newFileName);
                                    SkydriveStorage.UploadFile(newFileName, CN1Extensions.logFileName);
                                    isf.DeleteFile(newFileName);
                                }
                            }
                        });
                    }
                    break;
                case "set-loglevel": // called from UI thread!!!
                    {
                        System.Windows.Controls.Primitives.Popup popup = new System.Windows.Controls.Primitives.Popup();
                        LogLevelSetting child = new LogLevelSetting();
                        child.background.Width = Application.Current.Host.Content.ActualWidth;
                        child.background.Height = Application.Current.Host.Content.ActualHeight;
                        child.panel.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
                        Canvas.SetLeft(child.panel, (child.background.Width - child.panel.DesiredSize.Width) / 2);
                        Canvas.SetTop(child.panel, child.background.Height * 1 / 5);
                        popup.Child = child;
                        popup.IsOpen = true; 
                    }
                    break;
                case "create-file":
                    {
                        // move to FilePI.cs when/if Storage API is extended
                        string path = SilverlightImplementation.relativePath(SilverlightImplementation.nativePath(n2[0] as java.lang.String));
#if LOG
                        CN1Extensions.Log("create local file {0}", path);
#endif
                        using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                        {
                            using (IsolatedStorageFileStream unused = store.CreateFile(path))
                            {
                                // nothing to do
                            }
                        }
                    } 
                    break;
            }
            return javaResult;
        }

        private static bool InitSkydriveDataDir(bool createIfNecessary)
        {
            java.lang.String[] folders = {
                Config._fFOLDER_1MAPS, Config._fFOLDER_1NMEA, Config._fFOLDER_1PROFILES, 
                Config._fFOLDER_1RESOURCES, Config._fFOLDER_1SOUNDS, Config._fFOLDER_1TRACKS, 
                Config._fFOLDER_1WPTS, Config._fFOLDER_1GC, Config._fFOLDER_1PLUGINS
            };
            string rootId = SkydriveStorage.CreateFolder("TrekBuddy", "TrekBuddy", null, createIfNecessary);
            if (rootId == null)
            {
                return false;
            }
            foreach (java.lang.String folder in folders)
            {
                string csFolder = folder.toCSharp();
                if (csFolder.EndsWith("/"))
                    csFolder = csFolder.Substring(0, csFolder.Length - 1);
                SkydriveStorage.CreateFolder(csFolder, "TrekBuddy/" + csFolder, rootId, createIfNecessary);
            }
            return true;
        }

        private static void UnjarToLocal(string folder, string path)
        {
#if LOG
            CN1Extensions.Log("unjar-to-local: {0} {1}", folder, path);
#endif
            string filename = System.IO.Path.GetFileName(path);
            string localPath = System.IO.Path.Combine(folder, filename);
            StreamResourceInfo resource = Application.GetResourceStream(new Uri(path, UriKind.Relative));
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                if (!store.FileExists(localPath))
                {
#if LOG
                    CN1Extensions.Log("file {0} does not exist in local store folder {1}", filename, folder);
#endif
                    using (Stream output = store.CreateFile(localPath))
                    {
                        using (Stream input = resource.Stream)
                        {
                            input.CopyTo(output);
                        }
                    }
                }
#if LOG
                else
                {
                    CN1Extensions.Log("file {0} already exist in local store", filename);
                }
#endif
            }
        }

        private static void CopyToLocal(ExternalStorageDevice sd, string path, string pattern, bool includeSubfolders = false)
        {
#if LOG
            CN1Extensions.Log("copy-to-local: {0}:{1}", path, pattern);
#endif
            var task = System.Threading.Tasks.Task.Run(async () =>
            {
                ExternalStorageFolder folder = await sd.GetFolderAsync(path).ConfigureAwait(false);
#if LOG
                CN1Extensions.Log("check SD folder {0}", folder.Path);
#endif
                if (includeSubfolders)
                {
                    IEnumerable<ExternalStorageFolder> folders = await folder.GetFoldersAsync().ConfigureAwait(false);
                    foreach (ExternalStorageFolder item in folders)
                    {
                        CopyToLocal(sd, item.Path, pattern);
                    }
                }
                IEnumerable<ExternalStorageFile> files = await folder.GetFilesAsync().ConfigureAwait(false);
                foreach (ExternalStorageFile item in files)
                {
#if LOG
                    CN1Extensions.Log("sync file {0}?", item.Path);
#endif
                    if (item.Name.EndsWith(pattern))
                    {
                        using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                        {
                            if (!store.DirectoryExists(path))
                            {
#if LOG
                                CN1Extensions.Log("directory {0} does not exist yet", path);
#endif
                                store.CreateDirectory(path);
                            }
                            if (!store.FileExists(item.Path))
                            {
#if LOG
                                CN1Extensions.Log("file {0} does not exist in local store", item.Path);
#endif
                                using (Stream output = store.CreateFile(item.Path)) 
                                {
                                    using (Stream input = await item.OpenForReadAsync().ConfigureAwait(false))
                                    {
                                        input.CopyTo(output);
                                    }
                                }
                            }
#if LOG
                            else
                            {
                                CN1Extensions.Log("file {0} already exist in local store", item.Path);
                            }
#endif
                        }
                    }
                }
            });
            try
            {
                task.Wait();
            }
            catch (AggregateException ae)
            {
                CN1Extensions.Log(ae.Flatten().InnerException.ToString(), CN1Extensions.Level.ERROR);
                //throw new global::org.xmlvm._nExceptionAdapter(ae.Flatten().InnerException.ToJavaException());
                throw ae.Flatten().InnerException;
            }
        }
/*
        private static void CopyToLocal(SkydriveStorage storage, string path, string pattern, bool includeSubfolders = false)
        {
#if LOG
            CN1Extensions.Log("copy-to-local: {0}:{1}", path, pattern);
#endif
            var task = System.Threading.Tasks.Task.Run(async () =>
            {
                ExternalStorageFolder folder = await sd.GetFolderAsync(path);
#if LOG
                CN1Extensions.Log("check SD folder {0}", folder.Path);
#endif
                if (includeSubfolders)
                {
                    IEnumerable<ExternalStorageFolder> folders = await folder.GetFoldersAsync();
                    foreach (ExternalStorageFolder item in folders)
                    {
                        CopyToLocal(storage, item.Path, pattern);
                    }
                }
                IEnumerable<ExternalStorageFile> files = await folder.GetFilesAsync();
                foreach (ExternalStorageFile item in files)
                {
#if LOG
                    CN1Extensions.Log("sync file {0}?", item.Path);
#endif
                    if (item.Name.EndsWith(pattern))
                    {
                        using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                        {
                            if (!store.DirectoryExists(path))
                            {
#if LOG
                                CN1Extensions.Log("directory {0} does not exist yet", path);
#endif
                                store.CreateDirectory(path);
                            }
                            if (!store.FileExists(item.Path))
                            {
#if LOG
                                CN1Extensions.Log("file {0} does not exist in local store", item.Path);
#endif
                                using (Stream output = store.CreateFile(item.Path))
                                {
                                    using (Stream input = await item.OpenForReadAsync())
                                    {
                                        input.CopyTo(output);
                                    }
                                }
                            }
#if LOG
                            else
                            {
                                CN1Extensions.Log("file {0} already exist in local store", item.Path);
                            }
#endif
                        }
                    }
                }
            });
            try
            {
                task.Wait();
            }
            catch (AggregateException ae)
            {
                CN1Extensions.Log(ae.Flatten().InnerException.ToString(), com.codename1.io.Log._fERROR);
                throw new global::org.xmlvm._nExceptionAdapter(ae.Flatten().InnerException.ToJavaException());
            }
        }
*/
        private static void UnpackToLocal(ExternalStorageDevice sd, string path, string pattern)
        {
#if LOG
            CN1Extensions.Log("unpack-to-local: {0}:{1}", path, pattern);
#endif
            var task = System.Threading.Tasks.Task.Run(async () =>
            {
                ExternalStorageFolder folder;
                try
                {
                    folder = await sd.GetFolderAsync(path).ConfigureAwait(false);
                }
                catch (FileNotFoundException e)
                {
#if LOG
                    CN1Extensions.Log("SD folder {0} does not exist", path);
#endif
                    return;
                }
#if LOG
                CN1Extensions.Log("check SD folder {0}", folder.Path);
#endif
                IEnumerable<ExternalStorageFile> files = await folder.GetFilesAsync().ConfigureAwait(false);
                foreach (ExternalStorageFile item in files)
                {
#if LOG
                    CN1Extensions.Log("unpack file {0}?", item.Path);
#endif
                    if (item.Name.EndsWith(pattern))
                    {
                        using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                        {
                            if (!store.DirectoryExists(path))
                            {
#if LOG
                                CN1Extensions.Log("directory {0} does not exist yet", path);
#endif
                                store.CreateDirectory(path);
                            }
                            using (Stream input = await item.OpenForReadAsync().ConfigureAwait(false))
                            {
                                using (TarInputStream tarIn = new TarInputStream(input))
                                {
                                    TarEntry tarEntry;
                                    while ((tarEntry = tarIn.GetNextEntry()) != null)
                                    {
#if LOG
                                        CN1Extensions.Log("found entry {0}", tarEntry.Name);
#endif
                                        if (tarEntry.IsDirectory)
                                        {
                                            continue;
                                        }
                                        string name = tarEntry.Name.Replace('/', Path.DirectorySeparatorChar);
                                        string target = Path.Combine(path, name);
                                        if (!store.FileExists(target))
                                        {
#if LOG
                                            CN1Extensions.Log("file {0} does not exist in local store", target);
#endif
                                            using (Stream output = store.CreateFile(target))
                                            {
                                                tarIn.CopyEntryContents(output);
                                            }
                                        }
                                        else
                                        {
#if LOG
                                            CN1Extensions.Log("file {0} already exist in local store", target);
#endif
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
            try
            {
                task.Wait();
            }
            catch (AggregateException ae)
            {
                CN1Extensions.Log(ae.Flatten().InnerException.ToString(), CN1Extensions.Level.ERROR);
                //throw new global::org.xmlvm._nExceptionAdapter(ae.Flatten().InnerException.ToJavaException());
                throw ae.Flatten().InnerException;
            }
        }

        public static int? loadLoglevel()
        {
            IsolatedStorageSettings settings = IsolatedStorageSettings.ApplicationSettings;
            if (settings.Contains("log.level"))
            {
                return (int) settings["log.level"];
            }
            return null;
        }

        public static void saveLoglevel(int level)
        {
            IsolatedStorageSettings settings = IsolatedStorageSettings.ApplicationSettings;
            settings["log.level"] = level;
            settings.Save();
        }
    }

    internal class UIHelper
    {
        //private static TrackingApp.ProgressBarWithText pendingControl;
        //private Rectangle pendingControlBg;
        private static volatile System.Windows.Controls.Primitives.Popup pendingProgress;

        public static bool showProgressBar(string text, bool ignoreIfPending = false)
        {
            bool retval = pendingProgress == null;
            System.Diagnostics.Debug.WriteLine("UI.showProgressBar {0}", text ?? "null");
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                if (text == null)
                {
                    pendingProgress.IsOpen = false;
                    pendingProgress = null;
                }
                else if (pendingProgress != null)
                {
                    if (ignoreIfPending)
                    {
                        return;
                    }
                    MessageBox.Show("Error: another progress bar running!");
                }
                else
                {
                    pendingProgress = new System.Windows.Controls.Primitives.Popup();
                    TrackingApp.ProgressBarWithText child = new TrackingApp.ProgressBarWithText(text);
                    child.background.Width = Application.Current.Host.Content.ActualWidth;
                    child.background.Height = Application.Current.Host.Content.ActualHeight;
                    child.panel.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
                    Canvas.SetLeft(child.panel, (child.background.Width - child.panel.DesiredSize.Width) / 2);
                    Canvas.SetTop(child.panel, child.background.Height * 2 / 3);
                    pendingProgress.Child = child;
                    pendingProgress.IsOpen = true;
                }
            });
            return retval;
        }

        public static void updateProgressBar(string text)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                if (pendingProgress != null)
                {
                    ProgressBarWithText child = ((ProgressBarWithText)pendingProgress.Child);
                    child.Text = text;
                    child.panel.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
                    Canvas.SetLeft(child.panel, (child.background.Width - child.panel.DesiredSize.Width) / 2);
                    Canvas.SetTop(child.panel, child.background.Height * 2 / 3);
                }
            });
        }

        public static void showAlert(javax.microedition.lcdui.Alert alert)
        {
            UISynchronizationContext.Dispatcher.InvokeAsync(() =>
            {
                System.Windows.Controls.Primitives.Popup popup = new System.Windows.Controls.Primitives.Popup();
                TrackingApp.Alert child = new TrackingApp.Alert(alert);
                /*child.panel.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
                popup.HorizontalOffset = (Application.Current.Host.Content.ActualWidth - child.panel.DesiredSize.Width) / 2;
                popup.VerticalOffset = (Application.Current.Host.Content.ActualHeight - child.panel.DesiredSize.Height) / 2;*/
                child.background.Width = Application.Current.Host.Content.ActualWidth;
                child.background.Height = Application.Current.Host.Content.ActualHeight;
                child.panel.MaxWidth = Application.Current.Host.Content.ActualWidth;
                child.panel.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
                child.separator.X2 = child.panel.DesiredSize.Width; // does not work in XAML using binding to panel
                Canvas.SetLeft(child.panel, (child.background.Width - child.panel.DesiredSize.Width) / 2);
                Canvas.SetTop(child.panel, (child.background.Height - child.panel.DesiredSize.Height) / 2);
                popup.Child = child;
                popup.IsOpen = true;
            });
        }
    }

    internal class DrawButton : OperationPending
    {
        int x;
        int y;
        int w;
        int h;
        string label;

        public DrawButton(NativeGraphics ng, WipeComponent pendingWipe, int x, int y, int w, int h, string label)
            : base(ng, pendingWipe)
        {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
        }

        protected DrawButton(DrawButton p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.x = p.x + translateX;
            this.y = p.y + translateY;
            this.w = p.w;
            this.h = p.h;
            this.label = p.label;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawButton(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
            Button i = new Button();
            Canvas.SetLeft(i, x);
            Canvas.SetTop(i, y);
            updateClip(i, x, y);
            i.Width = w;
            i.MinHeight = h;
            i.Content = label;
            i.FontSize = (double)Application.Current.Resources["PhoneFontSizeMediumLarge"];
            i.FontFamily = (FontFamily)Application.Current.Resources["PhoneFontFamilySemiLight"];
            i.Background = (Brush)Application.Current.Resources["PhoneBackgroundBrush"];
            i.Click += i_Click;
            i.ManipulationStarted += i_ManipulationStarted;
            i.ManipulationCompleted += i_ManipulationCompleted;
            //add(i);
            cl.Children.Add(i);
        }

        void i_ManipulationCompleted(object sender, System.Windows.Input.ManipulationCompletedEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("button manipulation completed");
            e.Handled = true;
        }

        void i_ManipulationStarted(object sender, System.Windows.Input.ManipulationStartedEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("button manipulation started");
            i_Click(sender, null);
            e.Handled = true;
        }

        static void i_Click(object sender, RoutedEventArgs e)
        {
#if LOG
            CN1Extensions.Log("DrawButton click");
#endif
            Button button = sender as Button;
            double x = (double)button.GetValue(Canvas.LeftProperty);
            double y = (double)button.GetValue(Canvas.TopProperty);
            //cz.kruch.track.ui.Desktop._fscreen.pointerPressed((int)(x + button.Width / 2), (int)(y + button.Height / 2));
            cz.kruch.track.ui.Desktop._fscreen.buttonPressed((int)(x + button.Width / 2), (int)(y + button.Height / 2));
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            wbmp.DrawRectangle(x, y, x + w, y + h, color);
        }
#endif

        public override void printLogging()
        {
            log("Draw button x: " + x + " y: " + y +
                " w: " + w + " h: " + h + " label: " + label + " " + clipString());
        }
    }

    internal class DrawImageRegion : OperationPending
    {
        protected java.lang.Object image;
        protected int x_src, x_dest;
        protected int y_src, y_dest;
        protected int w;
        protected int h;
        private int transform;
        int alpha;

        public DrawImageRegion(NativeGraphics ng, WipeComponent pendingWipe, java.lang.Object image, int x_src, int y_src, int w, int h,
            int transform, int x_dest, int y_dest)
            : base(ng, pendingWipe)
        {
            this.image = image;
            this.x_src = x_src;
            this.y_src = y_src;
            this.w = w;
            this.h = h;
            this.transform = transform;
            this.x_dest = x_dest;
            this.y_dest = y_dest;
            this.alpha = ng.alpha;
        }

        protected DrawImageRegion(DrawImageRegion p, WipeComponent pendingWipe, int translateX, int translateY)
            : base(p, pendingWipe)
        {
            this.image = p.image;
            this.x_src = p.x_src + translateX;
            this.y_src = p.y_src + translateY;
            this.w = p.w;
            this.h = p.h;
            this.transform = p.transform;
            this.x_dest = p.x_dest + translateX;
            this.y_dest = p.y_dest + translateY;
            this.alpha = p.alpha;
        }

        public override OperationPending clone(WipeComponent w, int translateX, int translateY)
        {
            return new DrawImageRegion(this, w, translateX, translateY);
        }

        public override void prerender()
        {
        }

        public override void perform(Canvas cl)
        {
#if true
            CodenameOneImage img = (CodenameOneImage)image;
            if (img.img != null)
            {
                // 1. cut region
                var ia = new Image();
                ia.Source = img.img;
                ia.Width = img.width;
                ia.Height = img.height;
                var wbmp = new WriteableBitmap(w, h);
                wbmp.Render(ia, new TranslateTransform() { X = -x_src, Y = -y_src });
                wbmp.Invalidate();
                ia = null;
                // 2. create image and perform transformation
                var i = new Image();
                i.Source = wbmp;
                Canvas.SetLeft(i, x_dest);
                Canvas.SetTop(i, y_dest);
                updateClip(i, x_dest, y_dest);
                i.Width = w;
                i.Height = h;
                Transform tf = null;
                switch (transform)
                {
                    case 0: // javax.microedition.lcdui.game.Sprite._fTRANS_1NONE
                        {
                            // no tf
                        }
                        break;
                    case 1: // javax.microedition.lcdui.game.Sprite._fTRANS_1MIRROR_1ROT180
                        {
                            CompositeTransform ctf = new CompositeTransform();
                            ctf.ScaleX = -1;
                            ctf.Rotation = 180;
                            ctf.CenterX = i.ActualWidth / 2;
                            ctf.CenterY = i.ActualHeight / 2;
                            tf = ctf;
                        }
                        break;
                    case 2: // javax.microedition.lcdui.game.Sprite._fTRANS_1MIRROR
                        {
                            ScaleTransform stf = new ScaleTransform();
                            stf.ScaleX = -1;
                            stf.CenterX = i.ActualWidth / 2;
                            stf.CenterY = i.ActualHeight / 2;
                            tf = stf;
                        }
                        break;
                    case 3: // javax.microedition.lcdui.game.Sprite._fTRANS_1ROT180
                        {
                            RotateTransform rtf = new RotateTransform();
                            rtf.Angle = 180;
                            rtf.CenterX = i.ActualWidth / 2;
                            rtf.CenterY = i.ActualHeight / 2;
                            tf = rtf;
                        }
                        break;
                    default:
                        throw new NotImplementedException(String.Format("drawRegion transformation {0} not implemented yet", transform));
                }
                if (tf != null)
                {
                    i.RenderTransform = tf;
                }
                //i.CacheMode = CACHE_MODE;
                //add(i);
                cl.Children.Add(i);
            }
#else
            CodenameOneImage img = (CodenameOneImage)image;
            if (img.img != null)
            {
                Image i = new Image();
                i.Stretch = Stretch.None;
                i.Source = img.img;
                i.Opacity = ((double)alpha) / 255.0;
                i.Width = img.width;
                i.Height = img.height;
                Canvas.SetLeft(i, x_dest - x_src);
                Canvas.SetTop(i, y_dest - y_src);
                System.Diagnostics.Debug.WriteLine("src:{0},{1} dest:{2},{3} tf:{4}", x_src, y_src, x_dest, y_dest, transform);
                System.Diagnostics.Debug.WriteLine("  pos:{0},{1} clip:{2},{3} {4}x{5}", x_dest - x_src, y_dest - y_src, x_src, y_src, w, h);
                int x0 = x_src;
                dynamic tf = null;
                switch (transform)
                {
                    case 0: // javax.microedition.lcdui.game.Sprite._fTRANS_1NONE
                        {
                        }
                        break;
                    case 1: // javax.microedition.lcdui.game.Sprite._fTRANS_1MIRROR_1ROT180
                        {
                            tf = new CompositeTransform();
                            tf.ScaleX = -1;
                            tf.Rotation = 180;
                            tf.CenterX = i.ActualWidth / 2;
                            tf.CenterY = i.ActualHeight / 2;
                        }
                        break;
                    case 2: // javax.microedition.lcdui.game.Sprite._fTRANS_1MIRROR
                        {
                            tf = new ScaleTransform();
                            tf.ScaleX = -1;
                            tf.CenterX = i.ActualWidth / 2;
                            tf.CenterY = i.ActualHeight / 2;
                            x0 = img.width - w - x_src;
                        }
                        break;
                    case 3: // javax.microedition.lcdui.game.Sprite._fTRANS_1ROT180
                        {
                            tf = new RotateTransform();
                            tf.Angle = 180;
                            tf.CenterX = i.ActualWidth / 2;
                            tf.CenterY = i.ActualHeight / 2;
                            x0 = img.width - w - x_src;
                        }
                        break;
                    default:
                        throw new NotImplementedException(String.Format("drawRegion transformation {0} not supported", transform));
                }
                if (tf != null)
                {
                    //i.RenderTransformOrigin = new Point(0.5, 0.5);
                    i.RenderTransform = tf;
                }
                /*i.Clip = new RectangleGeometry()
                {
                    Rect = new Rect(x0, y_src, w, h)
                };*/
                i.CacheMode = CACHE_MODE;
                cl.Children.Add(i);
                add(i);
            }
#endif
        }

#if __USE_WBMP
        public override void perform(WriteableBitmap wbmp)
        {
            throw new NotImplementedException();
        }
#endif

        public override void printLogging()
        {
            CodenameOneImage img = (CodenameOneImage)image;
            bool isNull = img.img == null;
            log("Draw region x: " + x_dest + " y: " + y_dest +
                " w: " + w + " h: " + h + " image null: " + isNull + clipString());
        }
    }
}
