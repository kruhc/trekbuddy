import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;
import java.text.SimpleDateFormat;

public class BarManifestVersion extends Task {
    private static final String ENTRY_PACKAGE_VERSION       = "Package-Version: ";
    private static final String ENTRY_APPLICATION_VERSION   = "Application-Version: ";

    private String infile, outfile, appVersion;

    public void execute() throws BuildException {
        try {
            main(new String[]{ infile, outfile, appVersion });
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    public void setInfile(String infile) {
        this.infile = infile;
    }

    public void setOutfile(String outfile) {
        this.outfile = outfile;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 3) {
            System.err.println("Parameters: [input manifest] [output manifest] [version]");
            System.exit(-1);
        }
        List content = new ArrayList(64);
        BufferedReader reader = new BufferedReader(new FileReader(args[0]));
        try {
            String line = reader.readLine();
            while (line != null) {
                content.add(line);
                line = reader.readLine();
            }
            reader.close();
        } finally {
            reader.close();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]));
        try {
            for (Iterator it = content.iterator(); it.hasNext(); ) {
                String line = (String) it.next();
                if (line.startsWith(ENTRY_PACKAGE_VERSION)) {
                    // I do not understand the format
                    //line = ENTRY_PACKAGE_VERSION + (new SimpleDateFormat("1.yyyy.Md.0").format(new Date()));
                } else if (line.startsWith(ENTRY_APPLICATION_VERSION)) {
                    System.out.print("Changing\n\t[" + line + "]\nto\n\t");
                    line = ENTRY_APPLICATION_VERSION + args[2];
                    System.out.println("[" + line + "]");
                }
                writer.write(line);
                writer.write("\r\n");
            }
        } finally {
            writer.close();
        }
    }
}
