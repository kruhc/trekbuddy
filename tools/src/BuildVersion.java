import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.Properties;

public class BuildVersion extends Task {
    private static final String PRODUCT_PROPERTIES = "properties/build-@@.properties";
    private static final String PRODUCT_VERSION = "product.version";

    private static final String BUILD_PROPERTIES = "build.properties";
    private static final String CURRENT_VERSION = "current_version";
    private static final String BUILD_VERSION = "build_version";

    private String currentVersion;

    public void execute() throws BuildException {
        final File baseDir = getProject().getBaseDir();
        final String propFile = BUILD_PROPERTIES;
        final Properties properties = new Properties();
        FileInputStream fin = null;
        try {
            properties.load(fin = new FileInputStream(new File(baseDir, propFile)));
        } catch (IOException e) {
            throw new BuildException(e);
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        String cv = properties.getProperty(CURRENT_VERSION);
        String bv = properties.getProperty(BUILD_VERSION);
        if (cv.equals(currentVersion)) {
            getProject().setNewProperty("build_version", bv);
        } else {
            throw new BuildException("version mismatch: " + cv + " != " + currentVersion);
        }
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java BuildVersion <vendor>");
            return;
        }

        Properties properties = new Properties();
        FileInputStream fin = new FileInputStream(PRODUCT_PROPERTIES.replaceAll("@@", args[0]));
        properties.load(fin);
        fin.close();
        String productVersion = properties.getProperty(PRODUCT_VERSION);
        System.out.println("product version: " + productVersion);

        properties = new Properties();
        File fi = new File(BUILD_PROPERTIES);
        if (fi.exists()) {
            fin = new FileInputStream(BUILD_PROPERTIES);
            properties.load(fin);
            fin.close();
        }
        String currentVersion = properties.getProperty(CURRENT_VERSION);
        int bv;
        if (productVersion.equals(currentVersion)) {
            bv = Integer.parseInt(properties.getProperty(BUILD_VERSION, "99"));
        } else {
            bv = 99;
        }
        System.out.println("current version: " + currentVersion + "; build: " + bv);

        properties.setProperty(CURRENT_VERSION, productVersion);
        properties.setProperty(BUILD_VERSION, Integer.toString(++bv));
        FileOutputStream fos = new FileOutputStream(BUILD_PROPERTIES);
        properties.store(fos, null);
        fos.close();
        System.out.println("use version: " + currentVersion + "; next build: " + bv);
    }
}
