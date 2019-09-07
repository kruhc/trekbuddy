// @LICENSE@

package cz.kruch.track.util;

//#ifdef __ANDROID__

import android.util.Log;

import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Properties;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import cz.kruch.track.ui.YesNoDialog;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.configuration.Config;

public class Package implements YesNoDialog.AnswerListener {
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;

    private String name, url;
    private int countd, countf;

    private String pkgName, pkgScreen;
    private int pkgScreenWidth, pkgScreenHeight;

    public Package(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public void response(int answer, Object closure) {
        if (answer == YesNoDialog.YES) {
            Log.d(TAG, "unpack " + url + " to " + Config.getDataDir());
            Exception error = null;
            cz.kruch.track.ui.nokia.DeviceControl.setTicker(Desktop.display.getCurrent(), "Importing content from " + name + " ...");
            try {
                unpack();
            } catch (Exception e) {
                error = e;
            } finally {
                cz.kruch.track.ui.nokia.DeviceControl.setTicker(Desktop.display.getCurrent(), null);
            }
            if (error == null) {
                final int check = check();
                final StringBuilder sb = new StringBuilder(64);
                if (pkgName == null) {
                    sb.append("Import complete.");
                } else {
                    sb.append("Import of package \"" + pkgName + "\" complete.");
                }
                if (countd > 0 || countf > 0) {
                    sb.append(countd).append( " new folders, ").append(countf).append( " new files).\r\nPlease restart application.");
                } else {
                    sb.append("No new content was found.");
                }
                switch (check) {
                    case 0:
                        sb.append("\r\n\r\nWarning: Resolution check skipped.");
                        break;
                    case 1:
                        sb.append("\r\n\r\nResolution check successful.");
                        break;
                    case -1:
                        sb.append("\r\n\r\nWarning: Target resolution is ")
                                .append(pkgScreen).append(", but screen resolution is ")
                                .append(Desktop.width).append('x').append(Desktop.height);
                        break;
                }
                if (check == 1) {
                    Desktop.showInfo(sb.toString(), Desktop.screen, true);
                } else {
                    Desktop.showWarning(sb.toString(), null, Desktop.screen);
                }
            } else {
                Desktop.showError("Import failed", error, Desktop.screen);
            }
        }
    }

    private void unpack() throws IOException, URISyntaxException {
        final ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(getFile(url))));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            final String name = entry.getName();
            if (name.toLowerCase().startsWith("trekbuddy/")) {
                String sub = name.substring("trekbuddy/".length());
                if (sub.length() > 0) {
                    final long modified = entry.getTime();
                    Log.d(TAG, "found TB entry " + sub);
                    if (entry.isDirectory()) {
                        countd += create(sub, modified);
                    } else {
                        countf += copy(sub, modified, zip);
                    }
                }
            } else if (".content".equals(name.toLowerCase())) {
                Log.d(TAG, "found package meta info");
                meta(zip);
            }
        }
        zip.close();
    }

    private int check() {
        int result = 0;
        if (pkgScreenWidth > 0 && pkgScreenHeight > 0) {
            if (Desktop.width == pkgScreenWidth && Desktop.height == pkgScreenHeight) {
                result = 1;
            } else {
                result = -1;
            }
        }
        return result;
    }

    public static final String UTF8_BOM = "\uFEFF";

    private void meta(final InputStream in) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(UTF8_BOM)) {
                line = line.substring(UTF8_BOM.length());
            }
            Log.d(TAG, "meta info: " + line);
            final String[] split = line.split("=");
            if (split.length == 2) {
                final String key = split[0];
                final String value = split[1];
                if (key.length() > 0 && value.length() > 0) {
                    if ("package.name".equals(key)) {
                        pkgName = value;
                    } else if ("screen.resolution".equals(key)) {
                        pkgScreen = value;
                        final String[] dims = value.split("x");
                        if (dims.length == 2) {
                            try {
                                pkgScreenWidth = Integer.parseInt(dims[0]);
                                pkgScreenHeight = Integer.parseInt(dims[1]);
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "package has invalid descriptor", e); 
                            }
                        }
                    }
                }
            }
        }
    }

    private static int create(final String sub, final long timestamp) throws IOException, URISyntaxException {
        int create = 0;
        final File target = getFile(Config.getDataDir() + sub);
        Log.d(TAG, "content folder " + target.getPath());
        if (!target.exists()) {
            Log.d(TAG, " - does not exist");
            create = 1;
            target.mkdirs();
            target.setLastModified(timestamp);
        }
        return create;
    }

    private static int copy(String sub, long timestamp, InputStream in) throws IOException, URISyntaxException {
        int copy = 0;
        final File target = getFile(Config.getDataDir() + sub);
        Log.d(TAG, "content file " + target.getPath());
        if (target.exists()) {
            if (target.lastModified() < timestamp) {
                Log.d(TAG, " - overwriting older file " + (new Date(target.lastModified()) + " with " + (new Date(timestamp))));
                copy = 1;
            } else if (target.lastModified() > timestamp) {
                Log.d(TAG, " - existing file is newer " + (new Date(target.lastModified()) + " than " + (new Date(timestamp))));
            } else {
                Log.d(TAG, " - existing file is the same time, do nothing");
            }
        } else {
            Log.d(TAG, " - does not exist");
            copy = 1;
        }
        if (copy > 0) {
            final OutputStream out = new FileOutputStream(target);
            final byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            target.setLastModified(timestamp);
        }
        return copy;
    }

    private static File getFile(String url) throws URISyntaxException {
        return new File(new URI(url));
    }
}

//#endif