/* Copyright 2004-2006 David N. Welton

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
 * The <code>StringThing</code> class is the internal representation of string
 * types. This is somewhat special, as all types in Hecl may be represented as
 * strings.
 *
 * @author <a href="mailto:davidw@dedasys.com">David N. Welton </a>
 * @version 1.0
 */
public final class StringThing implements RealThing {
    private Object val;

    /**
     * Creates a new, empty <code>StringThing</code> instance.
     *
     */
    public StringThing() {
	this((String)null);
    }

    /**
     * Creates a new <code>StringThing</code> instance from a string.
     *
     * @param s
     *            a <code>String</code> value
     */
    public StringThing(final String s) {
        val = s != null ? s : (Object)(new StringBuffer());
    }

    /**
     * Creates a new <code>StringThing</code> instance from a stringbuffer.
     *
     * @param sb
     *            a <code>StringBuffer</code> value
     */
    public StringThing(final StringBuffer sb) {
        val = sb;
    }

    public String thingclass() {
	return "string";
    }

    /**
     * The <code>create</code> method creates and returns a newly allocated
     * <code>Thing</code> with a <code>StringThing</code> internal representation.
     *
     * @param s A <code>String</code> value which may be <code>null</code>.
     * @return A <code>Thing</code> value
     */
    public static Thing create(final String s) {
	return new Thing(new StringThing(s != null ? s : ""));
    }
	    
    /**
     * The <code>setStringFromAny</code> method transforms the Thing into a
     * string type.
     *
     * @param thing
     *            a <code>Thing</code> value
     * @throws HeclException
     */
    private static void setStringFromAny(final Thing thing) { 
        RealThing realthing = thing.getVal();
        if (!(realthing instanceof StringThing)) {
            thing.setVal(new StringThing(thing.toString()));
        }
    }

    /**
     * <code>get</code> returns a string representation of a given Thing,
     * transforming the thing into a string type at the same time.
     *
     * @param thing
     *            a <code>Thing</code> value
     * @return a <code>String</code> value
     * @throws HeclException
     */
    public static String get(final Thing thing) {
        setStringFromAny(thing);
        return thing.toString();
    }

    /**
     * <code>deepcopy</code> copies the string.
     *
     * @return a <code>RealThing</code> value
     */
    public RealThing deepcopy() {
        return new StringThing(new StringBuffer(val.toString()));
    }

    /**
     * <code>getStringRep</code> returns its internal value.
     *
     * @return a <code>String</code> value
     */
    public String getStringRep() {
        if (val instanceof StringBuffer) {
            final String _val = val.toString();
            val = null; // gc hint
            val = _val;
        }
        return val.toString();
    }

    /**
     * <code>append</code> takes a character and appends it to the string.
     *
     * @param ch
     *            a <code>char</code> value
     */
    public void append(final char ch) {
        if (val instanceof StringBuffer) {
            ((StringBuffer) val).append(ch);
        } else { // val is String
            val = (new StringBuffer(val.toString())).append(ch);
        }
/*        
        if (val instanceof String) {
            val = new StringBuffer(val.toString());
        }
        ((StringBuffer) val).append(ch);
*/
    }

    /**
     * <code>append</code> appends a string to the string.
     *
     * @param str
     *            a <code>String</code> value
     */
    public void append(final String str) {
        if (val instanceof StringBuffer) {
            ((StringBuffer) val).append(str);
        } else { // val is String
            val = (new StringBuffer(val.toString())).append(str);
        }
/*    
        if (val instanceof String) {
            val = new StringBuffer(val.toString());
        }
        ((StringBuffer) val).append(str);
*/        
    }

    /*
     * HACK: reuse most frequent strings
     */
    static StringThing reuse(final StringThing str) {
        if ("set".equals(str.getStringRep())) {
            return SET;
        }
        return str;
    }
    static StringThing SET = new StringThing("set");
}
