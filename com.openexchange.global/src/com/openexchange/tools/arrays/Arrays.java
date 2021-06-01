/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 * 
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.tools.arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains convenience methods for dealing with arrays.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class Arrays {

    /**
     * Prevent instantiation
     */
    private Arrays() {
        super();
    }

    /**
     * Concatenates the specified arrays.
     * <pre>
     *   int[] a = {1,2,3};
     *   int[] b = {4,5,6};
     *   int[] c = concatenate(a, b);
     *   System.out.println(Arrays.toString(c))
     * </pre>
     * Outputs: <code>{1,2,3,4,5,6}</code>
     *
     * @param a The first array
     * @param b The second array
     * @return The resulting array
     */
    public static int[] concatenate(int[] a, int[] b) {
        if (a == null) {
            return clone(b);
        }

        if (b == null) {
            return clone(a);
        }

        int[] c = new int[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /**
     * Clones specified array
     *
     * @param data The array to clone
     * @return The cloned array
     */
    public static int[] clone(int[] data) {
        if (data == null) {
            return null;
        }

        int[] copy = new int[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        return copy;
    }

    /**
     * Searches the given int value in the int array.
     *
     * @param array int array tested for containing the search parameter.
     * @param search this int is tested if the array contains it.
     * @return <code>true</code> if the array contains the int value.
     */
    public static boolean contains(int[] array, int search) {
        for (int i = array.length; i-- > 0;) {
            if (array[i] == search) {
                return true;
            }
        }
        return false;
    }

    public static int[] addUniquely(final int[] toExtend, final int... other) {
        if (toExtend == null || other == null) {
            return toExtend;
        }
        int[] retval = toExtend;
        for (int i : other) {
            boolean found = false;
            for (int j = retval.length; !found && j-- > 0;) {
                found = retval[j] == i;
            }
            if (!found) {
                int[] newarr = new int[retval.length + 1];
                System.arraycopy(retval, 0, newarr, 0, retval.length);
                newarr[retval.length] = i;
                retval = newarr;
            }
        }
        return retval;
    }

    /**
     * Generates consecutive subarrays for an array,each of the same size (the final array may be smaller).
     * <p>
     * For example, partitioning an array containing {@code [a, b, c, d, e]} with a partition
     * size of 3 yields<br>{@code [[a, b, c], [d, e]]} -- an outer list containing two inner array
     * of three and two elements, all in the original order.
     *
     * @param array The array to return consecutive subarrays of
     * @param size The desired size of each subarray (the last may be smaller)
     * @return A list of consecutive subarrays
     * @throws IllegalArgumentException If {@code size} is non-positive
     */
    public static <T> List<T[]> partition(T[] array, int size) {
        checkNotNull(array);
        checkArgument(size > 0);

        int length = array.length;
        if (length <= size) {
            return Collections.singletonList(array);
        }

        List<T[]> retval = new ArrayList<>((length + size - 1) / size);
        int stopIndex = 0;
        for (int startIndex = 0; startIndex + size <= length; startIndex += size) {
            stopIndex += size;
            retval.add(java.util.Arrays.copyOfRange(array, startIndex, stopIndex));
        }
        if (stopIndex < length) {
            retval.add(java.util.Arrays.copyOfRange(array, stopIndex, length));
        }
        return retval;
    }

    /**
     * Generates consecutive subarrays for an array,each of the same size (the final array may be smaller).
     * <p>
     * For example, partitioning an array containing {@code [a, b, c, d, e]} with a partition
     * size of 3 yields<br>{@code [[a, b, c], [d, e]]} -- an outer list containing two inner array
     * of three and two elements, all in the original order.
     *
     * @param array The array to return consecutive subarrays of
     * @param size The desired size of each subarray (the last may be smaller)
     * @return A list of consecutive subarrays
     * @throws IllegalArgumentException If {@code size} is non-positive
     */
    public static List<int[]> partition(int[] array, int size) {
        checkNotNull(array);
        checkArgument(size > 0);

        int length = array.length;
        if (length <= size) {
            return Collections.singletonList(array);
        }

        List<int[]> retval = new ArrayList<>((length + size - 1) / size);
        int stopIndex = 0;
        for (int startIndex = 0; startIndex + size <= length; startIndex += size) {
            stopIndex += size;
            retval.add(java.util.Arrays.copyOfRange(array, startIndex, stopIndex));
        }
        if (stopIndex < length) {
            retval.add(java.util.Arrays.copyOfRange(array, stopIndex, length));
        }
        return retval;
    }

    @SafeVarargs
    public static <T> T[] remove(T[] removeFrom, T... toRemove) {
        List<T> tmp = new ArrayList<T>();
        for (T copy : removeFrom) {
            tmp.add(copy);
        }
        for (T remove : toRemove) {
            tmp.remove(remove);
        }
        @SuppressWarnings("unchecked")
        T[] retval = tmp.toArray((T[]) Array.newInstance(removeFrom.getClass().getComponentType(), tmp.size()));
        return retval;
    }

    @SafeVarargs
    public static <T> T[] add(T[] toExtend, T... other) {
        if (other == null) {
            return toExtend;
        }
        @SuppressWarnings("unchecked")
        T[] tmp = (T[]) Array.newInstance(toExtend.getClass().getComponentType(), toExtend.length + other.length);
        System.arraycopy(toExtend, 0, tmp, 0, toExtend.length);
        System.arraycopy(other, 0, tmp, toExtend.length, other.length);
        return tmp;
    }

    public static <T> T[] clone(T[] toClone) {
        @SuppressWarnings("unchecked")
        T[] retval = (T[]) Array.newInstance(toClone.getClass().getComponentType(), toClone.length);
        System.arraycopy(toClone, 0, retval, 0, toClone.length);
        return retval;
    }

    public static int[] extract(int[] source, int start) {
        return extract(source, start, source.length - start);
    }

    public static int[] extract(int[] source, int start, int length) {
        final int realLength = determineRealSize(source.length, start, length);
        final int[] retval = new int[realLength];
        System.arraycopy(source, start, retval, 0, realLength);
        return retval;
    }

    /**
     * Extracts specified sub-array from given source array starting at given offset.
     *
     * @param source The source array to extract from
     * @param start The start offset
     * @param length The number of elements to extract
     * @param clazz The array's type
     * @return The extracted sub-array
     */
    public static <T> T[] extract(T[] source, int start, int length, Class<? extends T> clazz) {
        final int realLength = determineRealSize(source.length, start, length);
        @SuppressWarnings("unchecked")
        final T[] retval = (T[]) Array.newInstance(clazz, realLength);
        System.arraycopy(source, start, retval, 0, realLength);
        return retval;
    }

    /**
     * Determines the real size
     *
     * @param size The size/length of the source array
     * @param start The start offset
     * @param length The number of elements to extract
     * @return The size of the resulting array carrying the extracted elements
     */
    public static int determineRealSize(int size, int start, int length) {
        return start + length > size ? size - start : length;
    }

    public static Serializable[] toSerializable(Integer[] ids) {
        final Serializable[] retval = new Serializable[ids.length];
        for (int i = 0; i < ids.length; i++) {
            retval[i] = ids[i];
        }
        return retval;
    }

    /**
     * Reverses the order of the elements contained in the supplied array.
     *
     * @param array The array to reverse
     */
    public static <T> void reverse(T[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            T t = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = t;
        }
    }

    /**
     * Gets a value indicating whether the supplied array contains an element that is "equal to" the supplied one.
     *
     * @param array The array to check
     * @param t The element to lookup
     * @return <code>true</code> if an equal element was found, <code>false</code>, otherwise
     */
    public static <T> boolean contains(T[] array, T t) {
        if (null != t) {
            if (null == array) {
                return false;
            }

            for (int i = array.length; i-- > 0;) {
                if (t.equals(array[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets a value indicating whether the supplied array contains only elements that are "equal to" one of the supplied ones.
     *
     * @param array The array to check
     * @param ts The possible elements to check against
     * @return <code>true</code> if for all array elements an equal element was found, <code>false</code>, otherwise
     */
    public static <T> boolean containsOnly(T[] array, T... ts) {
        if (null != ts) {
            if (null == array) {
                return false;
            }
            for (int i = array.length; i-- > 0;) {
                boolean found = false;
                for (int j = 0; j < ts.length; j++) {
                    if (ts[j].equals(array[i])) {
                        found = true;
                        break;
                    }
                }
                if (false == found) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

}
