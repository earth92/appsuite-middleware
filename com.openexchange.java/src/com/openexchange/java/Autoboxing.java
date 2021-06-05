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

package com.openexchange.java;

import java.util.Collection;

/**
 * Methods helping with Autoboxing to shorten method names and therefore source code.
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class Autoboxing {

    /**
     * Prevent instantiation.
     */
    private Autoboxing() {
        super();
    }

    public static Byte B(final byte b) {
        return Byte.valueOf(b);
    }

    public static byte b(final Byte b) {
        return b.byteValue();
    }

    /**
     * Short method name for {@link Integer#valueOf(int)} that uses cached instances for small values of integer.
     * @param i integer value to be converted to an Integer object.
     * @return Integer object.
     */
    public static Integer I(final int i) {
        return Integer.valueOf(i);
    }

    /**
     * Short method name for unboxing an {@link Integer} object.
     * @param integer {@link Integer} to unbox.
     * @return the int value
     * @throws NullPointerException If passed {@code java.lang.Integer} instance is <code>null</code>
     */
    public static int i(final Integer integer) {
        return integer.intValue();
    }

    /**
     * Short method name for {@link Long#valueOf(long)} that uses cached instances for small values of long.
     * @param l long value to be converted to a Long object.
     * @return Long object.
     */
    public static Long L(final long l) {
        return Long.valueOf(l);
    }

    public static long l(final Long l) {
        return l.longValue();
    }

    /**
     * Short method name for {@link Boolean#valueOf(boolean)} that uses cached instances.
     * @param b boolean value to be converted to a Boolean object.
     * @return Boolean object.
     */
    public static Boolean B(final boolean b) {
        return (b ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Short method name for {@link Boolean#booleanValue()}.
     * @param b {@link Boolean} object to be converted to a boolean value.
     * @return boolean value.
     */
    public static boolean b(final Boolean b) {
        return b.booleanValue();
    }

    /**
     * Short method name for the inverse of {@link Boolean#valueOf(boolean)} that uses cached instances.
     * @param b boolean value to be converted to a Boolean object.
     * @return <code>Boolean.TRUE</code> if b == false, <code>Boolean.FALSE</code> otherwise.
     */
    public static Boolean NOT(final boolean b) {
        return (!b ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Short method name for the inverse of {@link Boolean#booleanValue()}.
     * @param b {@link Boolean} object to be converted to a boolean value.
     * @return <code>true</code> if b == Boolean.TRUE, <code>false</code> otherwise.
     */
    public static boolean not(final Boolean b) {
        return !b.booleanValue();
    }

    /**
     * Short method name for {@link Float#valueOf(float)} that uses cached instances.
     * @param f float value to be converted to a Float object.
     * @return Float object.
     */
    public static Float F(final float f) {
        return Float.valueOf(f);
    }

    public static float f(final Float f) {
        return f.floatValue();
    }

    /**
     * Short method name for {@link Double#valueOf(double)} that uses cached instances.
     * @param d double value to be converted to a Double object.
     * @return Double object.
     */
    public static Double D(final double d) {
        return Double.valueOf(d);
    }

    /**
     * Short method name for {@link Double#doubleValue()}.
     *
     * @param d Double object to be converted to a double value.
     * @return double value
     */
    public static double d(final Double d) {
        return d.doubleValue();
    }

    /**
     * Short method name for {@link Character#charValue()}.
     *
     * @param c {@link Character} object to be converted to a char value.
     * @return char value.
     */
    public static char c(Character c) {
        return c.charValue();
    }

    /**
     * Short method name for {@link Character#valueOf(char)} that uses cached instances.
     *
     * @param c char value to be converted to a Character object.
     * @return Character object.
     */
    public static Character C(char c) {
        return Character.valueOf(c);
    }

    /**
     * Short method name for {@link Double#doubleValue()}.
     *
     * @param b {@link Double} object to be converted to a double value.
     * @return double value.
     */
    public static double b(final Double d) {
        return d.doubleValue();
    }

    /**
     * Converts an int-array into an Integer-array.
     * @param intArray int[] to be converted to Integer[]
     * @return Integer[]
     */
    public static Integer[] i2I(final int[] intArray) {
        final Integer[] integerArray = new Integer[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            integerArray[i] = I(intArray[i]);
        }
        return integerArray;
    }

    /**
     * Converts an Integer-array into an int-array.
     * @param integerArray Integer[] to be converted to int[]
     * @return int[]
     */
    public static int[] I2i(final Integer[] integerArray) {
        int[] intArray = new int[integerArray.length];
        int pos = 0;
        for (Integer i : integerArray) {
            if (null != i) {
                intArray[pos++] = i.intValue();
            }
        }
        if (pos != intArray.length) {
            final int[] tmpArray = new int[pos];
            System.arraycopy(intArray, 0, tmpArray, 0, pos);
            intArray = tmpArray;
        }
        return intArray;
    }

    /**
     * Converts an Integer-list into an int-array.
     * @param integerCollection List of Integers to be converted to int[]
     * @return int[]
     */
    public static int[] I2i(final Collection<Integer> integerCollection) {
        int[] intArray = new int[integerCollection.size()];
        int pos = 0;
        for (final Integer i : integerCollection) {
            if (null != i) {
                intArray[pos++] = i.intValue();
            }
        }
        if (pos != intArray.length) {
            final int[] tmpArray = new int[pos];
            System.arraycopy(intArray, 0, tmpArray, 0, pos);
            intArray = tmpArray;
        }
        return intArray;
    }

    public static byte[] B2b(final Collection<Byte> byteCollection) {
        byte[] byteArray = new byte[byteCollection.size()];
        int pos = 0;
        for (final Byte b : byteCollection) {
            if (null != b) {
                byteArray[pos++] = b.byteValue();
            }
        }
        if (pos != byteArray.length) {
            final byte[] tmpArray = new byte[pos];
            System.arraycopy(byteArray, 0, tmpArray, 0, pos);
            byteArray = tmpArray;
        }
        return byteArray;
    }

    /**
     * Converts a long-array into a Long-array.
     * @param longArray long[] to be converted to Long[]
     * @return Long[]
     */
    public static Long[] l2L(final long[] longArray) {
        final Long[] longerArray = new Long[longArray.length];
        for (int i = 0; i < longArray.length; i++) {
            longerArray[i] = L(longArray[i]);
        }
        return longerArray;
    }

    /**
     * Conversts an objec-array into a Boolean-array
     * @param source
     * @return
     */
    public static Boolean[] O2B(final Object[] source) {
        final Boolean[] target = new Boolean[source.length];
        for (int i = 0; i < source.length; i++) {
            target[i] = (Boolean) source[i];
        }
        return target;
    }

    /**
     * Conversta an Object-array into a Number-array
     * @param source
     * @return
     */
    public static Number[] O2N(final Object[] source) {
        final Number[] target = new Number[source.length];
        for (int i = 0; i < source.length; i++) {
            target[i] = (Number) source[i];
        }
        return target;
    }

    /**
     * Converst an Object-array into a String-array
     * @param source
     * @return
     */
    public static String[] O2S(final Object[] source) {
        final String[] target = new String[source.length];
        for (int i = 0; i < source.length; i++) {
            target[i] = (String) source[i];
        }
        return target;
    }

    /**
     * Converts an Object-array into a Long-array
     * @param source
     * @return
     */
    public static Long[] O2L(final Object[] source) {
        final Long[] target = new Long[source.length];
        for (int i = 0; i < source.length; i++) {
            target[i] = (Long) source[i];
        }
        return target;
    }

    /**
     * Converts a collection of integers into an int-array
     */
    public static int[] Coll2i(final Collection<Integer> collection){
    	final int[] results = new int[collection.size()];
    	int position = 0;
    	for(final Integer value : collection) {
            results[position++] = value.intValue();
        }
    	return results;
    }

    // Type Coercion

    public static int a2i(final Object anything) {
        if (anything == null) {
            throw new NullPointerException("Can't convert null into integer");
        }
        if (Integer.class.isInstance(anything)){
            return ((Integer) anything).intValue();
        }
        if (Byte.class.isInstance(anything)) {
            return ((Byte) anything).intValue();
        }
        if (Long.class.isInstance(anything)) {
            return ((Long) anything).intValue();
        }
        if (String.class.isInstance(anything)) {
            return Integer.parseInt((String) anything);
        }

        throw new ClassCastException("I don't know how to turn "+anything+" of class "+anything.getClass().getName()+" into an int.");
    }

    public static boolean a2b(final Object anything) {
        if (anything == null) {
            throw new NullPointerException("Can't convert null into boolean");
        }
        if (Boolean.class.isInstance(anything)){
            return ((Boolean) anything).booleanValue();
        }

        if (String.class.isInstance(anything)) {
            return Boolean.parseBoolean((String) anything);
        }

        throw new ClassCastException("I don't know how to turn "+anything+" of class "+anything.getClass().getName()+" into a boolean.");
    }

    /**
     * Returns a {@code Boolean} instance representing the specified {@code boolean} value.
     * <p>
     * See details {@link Boolean#valueOf(boolean) here}.
     *
     * @param  b A boolean value.
     * @return A {@code Boolean} instance representing {@code b}
     */
    public static Boolean valueOf(boolean b) {
        return (b ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Returns an {@code Integer} instance representing the specified {@code int} value.
     * <p>
     * See details {@link Integer#valueOf(int) here}.
     *
     * @param  i An {@code int} value.
     * @return An {@code Integer} instance representing {@code i}
     */
    public static Integer valueOf(int i) {
        return Integer.valueOf(i);
    }

    /**
     * Returns a {@code Long} instance representing the specified {@code long} value.
     * <p>
     * See details {@link Long#valueOf(long) here}.
     *
     * @param  l A long value.
     * @return A {@code Long} instance representing {@code l}.
     */
    public static Long valueOf(long l) {
        return Long.valueOf(l);
    }

    /**
     * Returns a {@code Float} instance representing the specified {@code float} value.
     * <p>
     * See details {@link Float#valueOf(float) here}.
     *
     * @param  f A float value.
     * @return A {@code Float} instance representing {@code f}.
     */
    public static Float valueOf(float f) {
        return Float.valueOf(f);
    }

    /**
     * Returns a {@code Double} instance representing the specified {@code double} value.
     * <p>
     * See details {@link Double#valueOf(double) here}.
     *
     * @param  d A double value.
     * @return A {@code Double} instance representing {@code d}.
     */
    public static Double valueOf(double d) {
        return Double.valueOf(d);
    }

}
