// @LICENSE@

package cz.kruch.track.ui.nokia;

//#ifdef __RIM__

/**
 * Device control implementation for Blackberry phones.
 *
 * @author kruhc@seznam.cz
 */
final class BlackberryDeviceControl extends DeviceControl {
    private static final long KEY = 0xa224326efdc3cb05L; // long hash of "net.trekbuddy.midlet"

    static net.rim.device.api.system.PersistentObject store;
    static {
        store = net.rim.device.api.system.PersistentStore.getPersistentObject(KEY);
    }

    BlackberryDeviceControl() {
        this.name = "Blackberry";
    }

    /** @overriden */
    boolean isSchedulable() {
        return true;
    }

    /** @overriden */
    String getCellId() {
        final net.rim.device.api.system.GPRSInfo.GPRSCellInfo cellInfo = net.rim.device.api.system.GPRSInfo.getCellInfo();
        if (cellInfo != null) {
            return Integer.toString(cellInfo.getCellId());
        }
        return null;
    }

    /** @overriden */
    String getLac() {
        final net.rim.device.api.system.GPRSInfo.GPRSCellInfo cellInfo = net.rim.device.api.system.GPRSInfo.getCellInfo();
        if (cellInfo != null) {
            return Integer.toString(cellInfo.getLAC());
        }
        return null;
    }

    /** @overriden */
    void turnOn() {
        net.rim.device.api.system.Backlight.enable(true);
    }

    /** @overriden */
    void turnOff() {
        net.rim.device.api.system.Backlight.enable(false);
    }

    /** @overriden */
    public void run() {
        if (backlight != STATUS_OFF) {
            net.rim.device.api.system.Backlight.enable(true, 15); // must be greater than REFRESH_PERIOD
        }
    }

    /** @Override */
    void loadDatadir() {
        synchronized (store) {
            final String value = (String) store.getContents();
            if (value != null && value.length() != 0) {
                cz.kruch.track.configuration.Config.setDataDir(value);
            }
        }
    }

    /** @Override */
    void saveDatadir() {
        synchronized (store) {
            store.setContents(cz.kruch.track.configuration.Config.getDataDir());
            store.commit();
        }
    }
}

//#endif
