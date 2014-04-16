package javax.microedition.rms;

//#define __XAML__

//#ifdef __XAML__
import com.codename1.ui.FriendlyAccess;
//#else
import com.codename1.io.Storage;
//#endif

public class RecordStore {
    public static final int AUTHMODE_ANY = 1;
    public static final int AUTHMODE_PRIVATE = 0;

    private String name;
//#ifndef __XAML__
    private Storage instance;
//#endif

//#ifdef __XAML__
    private RecordStore(String name) {
        this.name = name;
    }
//#else
    private RecordStore(String name, Storage instance) {
        this.name = name;
        this.instance = instance;
    }
//#endif

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary,
                                              int authmode, boolean writable) throws RecordStoreException {
//#ifdef
        return new RecordStore(recordStoreName);
//#else
        return new RecordStore(recordStoreName, Storage.getInstance());
//#endif
    }

    public int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreException {
//#ifdef __XAML__
        return FriendlyAccess.getImplementation().storageAddRecord(name, data, offset, numBytes);
//#else
        int recordId = getNumRecords() + 1;
        if (instance.writeObject(name, data))
        {
            return recordId;
        }
        throw new RecordStoreException();
//#endif
    }

    public void closeRecordStore() throws RecordStoreException {
//#ifdef __XAML__
        FriendlyAccess.getImplementation().storageClose(name);
//#else
        com.codename1.io.Log.p("RecordStore.closeRecordStore - nothing to do", com.codename1.io.Log.WARNING);
//#endif
    }

    public int getNumRecords() throws RecordStoreException {
//#ifdef __XAML__
        return FriendlyAccess.getImplementation().storageGetNumRecords(name);
//#else
        if (instance.exists(name))
        {
            return 1;
        }
        return 0;
//#endif
    }

    public byte[] getRecord(int recordId) throws RecordStoreException {
//#ifdef __XAML__
        Object result = FriendlyAccess.getImplementation().storageGetRecord(name, recordId);
//#else
        if (recordId != 1) {
            throw new IllegalArgumentException("recordId");
        }
        Object result = instance.readObject(name);
//#endif
        if (result != null) {
            return (byte[]) result;
        }
        throw new RecordStoreException();
    }

    public void setRecord(int recordId, byte[] newData, int offset, int numBytes) throws RecordStoreException {
//#ifdef __XAML__
        FriendlyAccess.getImplementation().storageSetRecord(name, recordId, newData, offset, numBytes);
//#else
        if (recordId != 1) {
            throw new IllegalArgumentException("recordId");
        }
        if (instance.writeObject(name, newData))
        {
            return;
        }
        throw new RecordStoreException();
//#endif
    }
}
