// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.file.File;

import javax.microedition.lcdui.List;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Helper for arrays.
 */
public final class Arrays {

    /**
     * Sorts enumeration of strings to GUI list.
     * @param list list
     * @param items enumeration of strings
     */
    public static void sort2list(List list, Enumeration items) {
        // enum to list
        Vector v = new Vector(8, 8);
        while (items.hasMoreElements()) {
            v.addElement((String) items.nextElement());
        }

        // list to array
        String[] array = new String[v.size()];
        v.copyInto(array);

        // gc hint
        v.removeAllElements();
        v = null;

        // sort array
        sort(array);

        // add items sorted
        for (int N = array.length, i = 0; i < N; i++) {
            list.append(array[i], null);
        }

        // gc hint
        for (int i = array.length; --i >= 0; ) {
            array[i] = null;
        }
    }

    /*
     * String array sorting. From JDK.
     */

    private static void sort(final String[] a) {
        String aux[] = new String[a.length];
        System.arraycopy(a, 0, aux, 0, a.length);
        mergeSort(aux, a, 0, a.length);
    }

    private static void mergeSort(final String src[], final String dest[],
                                  final int low, final int high) {
        int length = high - low;

        // small arrays sorting
        if (length < 7) {
            for (int i = low; i < high; i++)
                for (int j = i; j > low && compareAsFiles(dest[j - 1], dest[j]) > 0; j--)
                    swap(dest, j, j - 1);
            return;
        }

        // half
        int mid = (low + high) >> 1;
        mergeSort(dest, src, low, mid);
        mergeSort(dest, src, mid, high);

        /*
         * If list is already sorted, just copy from src to dest.  This is an
         * optimization that results in faster sorts for nearly ordered lists.
         */
        if (compareAsFiles(src[mid - 1], src[mid]) <= 0) {
            System.arraycopy(src, low, dest, low, length);
            return;
        }

        // merge sorted halves (now in src) into dest
        for (int i = low, p = low, q = mid; i < high; i++) {
            if (q >= high || p < mid && compareAsFiles(src[p], src[q]) <= 0)
                dest[i] = src[p++];
            else
                dest[i] = src[q++];
        }
    }

    private static void swap(final String x[], final int a, final int b) {
        String t = x[a];
        x[a] = x[b];
        x[b] = t;
    }

    /*
     * ~
     */

    /**
     * Compares objects as filenames, with directories first.
     */
    private static int compareAsFiles(String s1, String s2) {
        boolean isDir1 = File.isDir(s1);
        boolean isDir2 = File.isDir(s2);
        if (isDir1) {
            if (isDir2) {
                return s1.compareTo(s2);
            } else {
                return -1;
            }
        } else {
            if (isDir2) {
                return 1;
            } else {
                return s1.compareTo(s2);
            }
        }
    }
}
