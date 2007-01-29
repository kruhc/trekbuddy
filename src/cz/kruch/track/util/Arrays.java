// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import javax.microedition.lcdui.List;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Helper for arrays.
 */
public final class Arrays {

    /**
     * Clears array.
     * @param a array
     */
    public static void clear(Object[] a) {
        for (int i = a.length; --i >= 0; ) {
            a[i] = null;
        }
    }

    /**
     * Sorts enumeration of strings to GUI list.
     * @param list list
     * @param items enumeration of strings
     */
    public static void sort2list(List list, Enumeration items) {
        // enum to list
        Vector v = new Vector();
        while (items.hasMoreElements()) {
            v.addElement((String) items.nextElement());
        }

        // list to array
        String[] array = new String[v.size()];
        v.copyInto(array);

        // sort array
        sort(array);

        // add items sorted
        for (int N = array.length, i = 0; i < N; i++) {
            list.append(array[i], null);
        }
    }

    /*
     * String array sorting.
     */

    private static void sort(String[] a) {
        String aux[] = new String[a.length];
        System.arraycopy(a, 0, aux, 0, a.length);
        mergeSort(aux, a, 0, a.length);
    }

    private static void mergeSort(String src[], String dest[], int low, int high) {
        int length = high - low;

        // small arrays sorting
        if (length < 7) {
            for (int i = low; i < high; i++)
                for (int j = i; j > low && compare(dest[j - 1], dest[j]) > 0; j--)
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
        if (compare(src[mid - 1], src[mid]) <= 0) {
            System.arraycopy(src, low, dest, low, length);
            return;
        }

        // merge sorted halves (now in src) into dest
        for (int i = low, p = low, q = mid; i < high; i++) {
            if (q >= high || p < mid && compare(src[p], src[q]) <= 0)
                dest[i] = src[p++];
            else
                dest[i] = src[q++];
        }
    }

    private static void swap(String x[], int a, int b) {
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
    private static int compare(String s1, String s2) {
        boolean isDir1 = '/' == s1.charAt(s1.length() - 1);
        boolean isDir2 = '/' == s2.charAt(s2.length() - 1);
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
