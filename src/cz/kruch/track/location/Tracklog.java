// @LICENSE@

package cz.kruch.track.location;

import cz.kruch.track.event.Callback;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.Resources;
import api.file.File;
import api.location.Location;
import api.io.BufferedOutputStream;

import javax.microedition.io.Connector;
import java.io.OutputStream;
import java.util.TimerTask;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

public class Tracklog extends TimerTask {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Tracklog");
//#endif

    public static final int CODE_RECORDING_STOP    = 0;
    public static final int CODE_RECORDING_START   = 1;

    private String fileName, fileUrl;
    private File file;

    protected Callback callback;
    protected long fileTime;
    protected OutputStream output;

    // NMEA-specific
//#ifndef __ANDROID__
    private net.trekbuddy.midlet.Runtime runtime;
//#else
    private net.trekbuddy.midlet.IRuntime runtime;
//#endif
    private volatile boolean state;

    protected Tracklog(Callback callback, long time) {
        this.callback = callback;
        this.fileTime = time;
    }

    // NMEA-specific
//#ifndef __ANDROID__
    public Tracklog(Callback callback, net.trekbuddy.midlet.Runtime runtime, long time)
//#else
    public Tracklog(Callback callback, net.trekbuddy.midlet.IRuntime runtime, long time)
//#endif
    {
        this(callback, time);
        this.runtime = runtime;
    }

    // TODO move to Utils
    public static String dateToFileDate(final long time) {
        final Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(new Date(time));
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.append(sb, calendar.get(Calendar.YEAR));
        appendTwoDigitStr(sb, calendar.get(Calendar.MONTH) + 1);
        appendTwoDigitStr(sb, calendar.get(Calendar.DAY_OF_MONTH)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY));
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE));
        appendTwoDigitStr(sb, calendar.get(Calendar.SECOND));

        return sb.toString();
    }

    protected static StringBuffer appendTwoDigitStr(final StringBuffer sb, final int i) {
        if (i < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, i);

        return sb;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDefaultFileName() {
        return (new StringBuffer(32)).append(dateToFileDate(fileTime)).append(".nmea").toString();
    }

    public String getURL() {
        return fileUrl;
    }

    protected String getFileFolder() {
        return Config.FOLDER_NMEA;
    }

    public void start() {
        state = true;
        cz.kruch.track.ui.Desktop.getDiskWorker().enqueue(this);
    }

    public void stop() {
        state = false;
        cz.kruch.track.ui.Desktop.getDiskWorker().enqueue(this);
    }

    public void run() {

        // start?
        if (state) {

            // open file
            Throwable status = open();

            // alles gute?
            if (status == null) {

                // register as observer
                runtime.getProvider().setObserver(output);

                // signal recording start
                callback.invoke(new Integer(CODE_RECORDING_START), null, this);

            } else {

                // signal failure
                callback.invoke(Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG_FAILED)+ ": " + getURL(), status, this);

            }

        } else {

            // deregister as observer
            runtime.getProvider().setObserver(null);

            // signal recording start
            callback.invoke(new Integer(CODE_RECORDING_STOP), null, this);

            // just close
            close();
        }
    }

    public Throwable open() {

        // local vars
        Throwable throwable = null;

        // construct file URL
        if (fileName == null) {
            fileName = getDefaultFileName();
        }
        fileUrl = Config.getFileURL(getFileFolder(), fileName);

        // create output file and stream
        try {

            // delete first
            file = File.open(fileUrl, Connector.READ_WRITE);
            if (file.exists()) {
                file.delete();
            }

            // create new
            file.create();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("file created: " + fileUrl);
//#endif

            // open stream
            output = new BufferedOutputStream(file.openOutputStream(), 4096, true);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("stream opened");
//#endif

        } catch (Exception e) {

            // result
            throwable = e;

//#ifdef __LOG__
            log.error("failed to open file/stream: " + e);
//#endif
            // cleanup - safe operation
            close();
        }

        return throwable;
    }

    public void close() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("closing stream and file");
//#endif

        // safe operation
        File.closeQuietly(output);
        output = null; // gc hint
        File.closeQuietly(file);
        file = null; // gc hint
    }

    /*
     * Nothing to do with NMEA log
     */

    public void locationUpdated(final Location location) {
    }

    public void insert(final Location location) {
    }

    public void insert(final Boolean bool) {
    }
}
