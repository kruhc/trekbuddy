using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Linq;

using org.xmlvm;
using net.trekbuddy.wp8;
using InputStreamProxy = net.trekbuddy.wp8.InputStreamWrapper;

namespace com.codename1.impl
{
    internal partial class SilverlightImplementation : CodenameOneImplementation //, IServiceProvider
    {
        public override void deleteStorageFile(global::java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                store.DeleteFile(toCSharp(n1));
            }
        }

        public override global::System.Object createStorageOutputStream(global::java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                return new OutputStreamProxy(store.OpenFile(toCSharp(n1), FileMode.Create, FileAccess.Write));
            }
        }

        public override global::System.Object createStorageInputStream(global::java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                return new InputStreamProxy(store.OpenFile(toCSharp(n1), FileMode.Open, FileAccess.Read));
            }
        }

        public override bool storageFileExists(global::java.lang.String n1)
        {
            using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
            {
                return store.FileExists(toCSharp(n1));
            }
        }

        public override int storageAddRecord(java.lang.String n1, _nArrayAdapter<sbyte> n2, int n3, int n4)
        {
            List<sbyte[]> records;
            IsolatedStorageSettings settings = IsolatedStorageSettings.ApplicationSettings;
            if (settings.Contains(n1.toCSharp()))
            {
                records = (List<sbyte[]>)settings[n1.toCSharp()];
            }
            else
            {
                records = new List<sbyte[]>();
                settings[n1.toCSharp()] = records;
            }
            sbyte[] entry = new sbyte[n4];
            Buffer.BlockCopy(n2.getCSharpArray(), n3, entry, 0, n4);
            records.Add(entry);
            return records.Count;
        }

        public override void storageSetRecord(java.lang.String n1, int n2, _nArrayAdapter<sbyte> n3, int n4, int n5)
        {
            sbyte[] entry = new sbyte[n5];
            Buffer.BlockCopy(n3.getCSharpArray(), n4, entry, 0, n5);
            ((List<sbyte[]>)IsolatedStorageSettings.ApplicationSettings[n1.toCSharp()])[n2 - 1] = entry;
        }

        public override object storageGetRecord(java.lang.String n1, int n2)
        {
            sbyte[] record = ((List<sbyte[]>)IsolatedStorageSettings.ApplicationSettings[n1.toCSharp()]).ElementAt(n2 - 1);
            return new global::org.xmlvm._nArrayAdapter<sbyte>(record);
        }

        public override int storageGetNumRecords(java.lang.String n1)
        {
            IsolatedStorageSettings settings = IsolatedStorageSettings.ApplicationSettings;
            if (settings.Contains(n1.toCSharp()))
            {
                return ((List<sbyte[]>)settings[n1.toCSharp()]).Count;
            }
            return 0;
        }

        public override void storageClose(java.lang.String n1)
        {
            IsolatedStorageSettings.ApplicationSettings.Save();
        }
    }
}