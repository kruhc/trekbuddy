/* Copyright 2006 Wolfgang S. Kechel

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.hecl;

/**
 * The <code>FloatThing</code> class represents a Thing that contains
 * a float value.
 */
public
//#if cldc == 1.0
abstract
//#endif
class FloatThing extends FractionalThing {
    public String thingclass() {
	return "float";
    }

    /**
     * Creates a new <code>FloatThing</code> instance equal to 0.
     *
     */
//#if javaversion >= 1.5 || cldc > 1.0
    public FloatThing() {
	set(0);
    }

    /**
     * Creates a new <code>FloatThing</code> instance with value i.
     * 
     * @param d
     *            a <code>float</code> value
     */
    public FloatThing(float f) {
	set(f);
    }

    /**
     * Creates a new <code>FloatThing</code> instance from boolean b where true
     * is 1 and false is 0.
     *
     * @param b
     *            a <code>boolean</code> value
     */
    public FloatThing(boolean b) {
        set(b == true ? 1 : 0);
    }

    /**
     * Creates a new <code>FloatThing</code> instance from string s.
     * 
     * @param s
     *            a <code>String</code> value
     */
    public FloatThing(String s) {
        set(Float.parseFloat(s));
    }

    /**
     * The <code>create</code> method creates and returns a newly allocated
     * Thing with a FloatThing internal representation.
     * 
     * @param d
     *            a <code>float</code> value
     * @return a <code>Thing</code> value
     */
    public static Thing create(float d) {
        return new Thing(new FloatThing(d));
    }

    /**
     * The <code>create</code> method creates and returns a newly allocated
     * Thing with a FloatThing internal representation.
     * 
     * @param b
     *            an <code>boolean</code> value
     * @return a <code>Thing</code> value
     */
    public static Thing create(boolean b) {
        return new Thing(new FloatThing(b));
    }

    /**
     * <code>set</code> transforms the given Thing into a FloatThing,
     * internally.
     * 
     * @param thing
     *            a <code>Thing</code> value
     * @exception HeclException
     *                if an error occurs
     */
    private static void set(Thing thing) throws HeclException {
        RealThing realthing = thing.getVal();

        if (realthing instanceof FloatThing)
	    return;

	if(NumberThing.isNumber(realthing)) {
	    // It's already a number
	    thing.setVal(new FloatThing(((NumberThing)realthing).floatValue()));
	} else {
	    /* Otherwise, try and parse the string representation. */
            thing.setVal(new FloatThing(thing.toString()));
	}
    }

    /**
     * <code>get</code> attempts to fetch a float value from a Thing.
     *
     * @param thing
     *            a <code>Thing</code> value
     * @return a <code>float</code> value
     * @exception HeclException
     *                if an error occurs
     */
    public static float get(Thing thing) throws HeclException {
        set(thing);
	return ((FloatThing)thing.getVal()).floatValue();
    }


    public byte byteValue() {
	return (byte)val;
    }

    public short shortValue() {
	return (short)val;
    }

    public int intValue() {
	return (int)val;
    }

    public long longValue() {
	return (long)val;
    }

    public float floatValue() {
	return val;
    }

    public double doubleValue() {
	return (double)val;
    }

    /**
     * <code>set</code> sets the internal value of a FloatThing to i.
     * 
     * @param d
     *            a <code>float</code> value
     */
    public void set(float d) {
        val = d;
    }

    /**
     * <code>deepcopy</code> makes a copy.
     * 
     * @return a <code>RealThing</code> value
     */
    public RealThing deepcopy() {
        return new FloatThing(val);
    }

    /**
     * <code>getStringRep</code> creates a string representation of the
     * FloatThing.
     * 
     * @return a <code>String</code> value
     */
    public String getStringRep() {
        return Float.toString(val);
    }


    private float val;
//#endif
}
