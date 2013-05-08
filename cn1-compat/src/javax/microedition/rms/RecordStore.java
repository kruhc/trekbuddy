package javax.microedition.rms;

import com.codename1.io.Storage;

public class RecordStore {
    public static final int AUTHMODE_ANY = 1;
    public static final int AUTHMODE_PRIVATE = 0;

    private String name;
    private Storage instance;

    private RecordStore(String name, Storage instance) {
        this.name = name;
        this.instance = instance;
    }

    private String getBackend(int recordId) {
        return (new StringBuilder(16)).append(name).append('.').append(recordId).toString();
    }

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary,
                                              int authmode, boolean writable) throws RecordStoreException {
        return new RecordStore(recordStoreName, Storage.getInstance());
    }

    public int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreException {
        int recordId = getNumRecords() + 1;
        if (instance.writeObject(getBackend(recordId), data)) {
            return recordId;
        }
        throw new RecordStoreException();
    }

    public void closeRecordStore() throws RecordStoreException {
        System.err.println("WARN RecordStore.closeRecordStore empty implementation");
    }

    public int getNumRecords() throws RecordStoreException {
        return instance.listEntries().length;
    }

    public byte[] getRecord(int recordId) throws RecordStoreException {
        Object result = instance.readObject(getBackend(recordId));
        if (result != null) {
            return (byte[]) result;
        }
        throw new RecordStoreException();
    }

    public void setRecord(int recordId, byte[] newData, int offset, int numBytes) throws RecordStoreException {
        if (instance.writeObject(getBackend(recordId), newData)) {
            return;
        }
        throw new RecordStoreException();
    }
}
