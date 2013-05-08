package javax.microedition.io.file;

import com.codename1.io.FileSystemStorage;

import java.util.Enumeration;
import java.util.Vector;

public final class FileSystemRegistry {

    private FileSystemRegistry() {
    }

    public static Enumeration listRoots() {
        final String[] roots = FileSystemStorage.getInstance().getRoots();
        final Vector<String> list = new Vector<String>();
        if (roots != null) {
            for (int i = 0, N = roots.length; i < N; i++) {
                list.addElement(roots[i]);
            }
        }
        return list.elements();
    }
}
