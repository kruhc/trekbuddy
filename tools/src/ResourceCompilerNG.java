import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.StringTokenizer;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Compiles resources into resource file.
 * TODO JSR-238 compatibility?
 */
public class ResourceCompilerNG {
    private static final int RESOURCE_FILE_SIGNATURE = 0xEB4D4910; // JSR-238: 0xEE4D4910

    /* entry point */
    public static void main(String[] args) throws IOException {
        /* command line check */
        if (args.length != 3) {
            System.err.println("Usage: -ascii|-utf8 inputfile outputfile");
            System.err.println("Example:\n\t'java -cp . ResourceCompiler' -utf8 language.txt language.res");
            System.exit(-1);
        }

        /* 1. count number of entries */
        Properties resources = new Properties();
        if ("-ascii".equals(args[0])) {
            resources.load(new FileInputStream(args[1]));
        } else if ("-utf8".equals(args[0])) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[1]), "UTF-8"));
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith("#")) {
                    System.out.println("comment: '" + line + "'");
                } else if (line.length() > 0 && line.indexOf('=') > 0) {
                    String[] pair = null;
                    try {
                        pair = line.split("=");
                        if (pair[1].indexOf("\\n") > -1) {
                            System.err.println(" WARNING: fixing \\\\n -> \\n for item " + pair[0]);
                            pair[1] = pair[1].replaceAll("\\\\n", "\n");
                        }
                        if (pair[1].length() > 1 && pair[1].endsWith(" ")) {
                            pair[1] = pair[1].substring(0, pair[1].length() - 1);
                        }
                        resources.setProperty(pair[0], pair[1]);
                    } catch (Exception e) {
                        if ("0399".equals(pair[0])) {
                            resources.setProperty(pair[0], "");
                        } else {
                            System.err.println(" ERROR: " + e.getClass().getName() + ": " + e.getMessage());
                            System.err.println(" LINE: '" + line + "'");
                            System.exit(-1);
                        }
                    }
                }
                line = reader.readLine();
            }
            reader.close();
        } else {
            System.err.println("First parameter must be -ascii or -utf8");
            System.exit(-1);
        }

        /* load ignore list */
        List ignored = new ArrayList();
/*
        try {
            Properties ip = new Properties();
            ip.load(ResourceCompilerNG.class.getResourceAsStream("ignored.properties"));
            System.out.print("Ignored entries:\n\t");
            for (Enumeration keys = ip.propertyNames(); keys.hasMoreElements(); ) {
                String key = (String) keys.nextElement();
                System.out.print(key + " ");
                ignored.add(key);
            }
            System.out.println("");
        } catch (Exception e) {
            // ignore
        }
*/
//        ignored.add(new String(new char[]{ 271, 187, 380, 35 })); // ???
//        ignored.add(new String(new char[]{ 65279, 35 })); // UTF8

        /* write meta info */
        DataOutputStream resout = new DataOutputStream(new FileOutputStream(args[2]));
        resout.writeInt(RESOURCE_FILE_SIGNATURE);   // signature
        final int hl = resources.size() * 8;
        resout.writeInt(hl);          // header length

        /* write resources */
        StringBuffer ffString = new StringBuffer(1024);
        SortedSet keys = new TreeSet(resources.keySet());
        final long offset = hl;
        for (Iterator it = keys.iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            String value = resources.getProperty(key);
            if (ignored.contains(key)) {
                System.out.println("[IGNORED] Resource: ID=" + key + " value=\"" + value + "\"");
            } else {
                try {
                    int id = Integer.parseInt(key);
                    id <<= 16;
                    id += offset + ffString.length();
                    if (value.endsWith(":")) {
                        value += " ";
                    }
                    resout.writeInt(id);
                    ffString.append(value);
                    System.out.println("Resource: ID=" + key + " value=\"" + value + "\"");
                } catch (NumberFormatException e) {
                    System.err.println(e.toString());
                    char[] chars = key.toCharArray();
                    for (int N = chars.length, i = 0; i < N; i++) {
                        System.out.print((int)chars[i]);
                        System.out.print(' ');
                    }
                    System.out.println("");
                    throw e;
                }
            }
        }
        String allinone = ffString.toString();
        resout.writeUTF(allinone);
        resout.close();

        /* end */
        System.out.println("Entries: " + resources.size());
        System.out.println("Signature: " + Integer.toHexString(RESOURCE_FILE_SIGNATURE));
    }
}
