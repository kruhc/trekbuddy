import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class RimOtaJadSha extends Task {

    private String moduleName;
    private String jad;
    private FileSet fileSet;

    public void execute() throws BuildException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(jad, true);
            final DirectoryScanner ds = fileSet.getDirectoryScanner(getProject());
            final String[] includedFiles = ds.getIncludedFiles();
            Arrays.sort(includedFiles, new Comparator() {
                public int compare(Object o1, Object o2) {
                    String s1 = (String) o1;
                    String s2 = (String) o2;
                    if (s1.length() != s2.length()) {
                        return s1.length() - s2.length();
                    }
                    return s1.compareTo(s2);
                }
            });
            StringBuffer sb = new StringBuffer(64);
            for (int i = 0; i < includedFiles.length; i++) {
                if (i > 0) {
                    sb.append("\r\n          ");
                }
                sb.append(includedFiles[i]);
            }
            getProject().setNewProperty("rim.cod.modules", sb.toString());
            for (int i = 0; i < includedFiles.length; i++) {
                String filename = includedFiles[i];
                String suffix = filename.substring(moduleName.length(), filename.indexOf(".cod"));
                File file = new File(ds.getBasedir(), filename);
                String sha1 = getSha1(getData(file));
                fos.write(("RIM-COD-URL" + suffix + ": " + filename + "\r\n").getBytes());
                fos.write(("RIM-COD-Size" + suffix + ": " + file.length() + "\r\n").getBytes());
                fos.write(("RIM-COD-SHA1" + suffix + ": " + sha1 + "\r\n").getBytes());
            }
        } catch (NoSuchAlgorithmException e) {
            throw new BuildException(e);
        } catch (IOException e) {
            throw new BuildException(e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public void setJad(String jad) {
        this.jad = jad;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public void addFileset(FileSet fileSet) {
        this.fileSet = fileSet;
    }

    private static byte[] getData(final File file) throws IOException {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final FileInputStream fin = new FileInputStream(file);
        final byte[] block = new byte[512];
        int i = fin.read(block);
        while (i != -1) {
            data.write(block, 0, i);
            i = fin.read(block);
        }
        fin.close();
        data.flush();

        return data.toByteArray();
    }

    private static String getSha1(final byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(data);
        final byte[] digest = md.digest();
        final StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0)
                hexString.append(' ');
            String hex = Integer.toHexString(0xff & digest[i]);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }

}
