// Automatically generated by xmlvm2csharp (do not edit).

using org.xmlvm;
namespace java.lang {
public class Double: global::java.lang.Number,global::java.lang.Comparable {

static Double() {
    @static();
}

private static long _fserialVersionUID = -9172774392245257468L;

private double _fvalue;

public static double _fMAX_1VALUE = 1.7976931348623157E308D;

public static double _fMIN_1VALUE = 4.9E-324D;

public static double _fMIN_1NORMAL = 2.2250738585072014E-308D;

public static double _fNaN = global::System.Double.NaN;

public static double _fPOSITIVE_1INFINITY = global::System.Double.PositiveInfinity;

public static int _fMAX_1EXPONENT = 1023;

public static int _fMIN_1EXPONENT = -1022;

public static double _fNEGATIVE_1INFINITY = global::System.Double.NegativeInfinity;

public static global::java.lang.Class _fTYPE;

public static int _fSIZE = 64;

public static void @static(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: void <clinit>()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r0.i = 0;
    _r0_o = new global::org.xmlvm._nArrayAdapter<double>(new double[_r0.i]);
    _r0_o = ((global::java.lang.Object) _r0_o).getClass();
    _r0_o = ((global::java.lang.Class) _r0_o).getComponentType();
    global::java.lang.Double._fTYPE = (global::java.lang.Class) _r0_o;
    return;
//XMLVM_END_WRAPPER[java.lang.Double: void <clinit>()]
}

public void @this(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: void <init>(double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r0_o = this;
    _r1.d = n1;
    ((global::java.lang.Number) _r0_o).@this();
    ((global::java.lang.Double) _r0_o)._fvalue = _r1.d;
    return;
//XMLVM_END_WRAPPER[java.lang.Double: void <init>(double)]
}

public void @this(global::java.lang.String n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: void <init>(java.lang.String)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r3_o = n1;
    _r0.d = global::java.lang.Double.parseDouble((global::java.lang.String) _r3_o);
    ((global::java.lang.Double) _r2_o).@this((double) _r0.d);
    return;
//XMLVM_END_WRAPPER[java.lang.Double: void <init>(java.lang.String)]
}

public virtual int compareTo(global::java.lang.Double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: int compareTo(java.lang.Double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nElement _r4;
    _r4.i = 0;
    _r4.l = 0;
    _r4.f = 0;
    _r4.d = 0;
    global::System.Object _r4_o = null;
    global::org.xmlvm._nElement _r5;
    _r5.i = 0;
    _r5.l = 0;
    _r5.f = 0;
    _r5.d = 0;
    global::System.Object _r5_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r4_o = this;
    _r5_o = n1;
    _r0.d = ((global::java.lang.Double) _r4_o)._fvalue;
    _r2.d = ((global::java.lang.Double) _r5_o)._fvalue;
    _r0.i = global::java.lang.Double.compare((double) _r0.d, (double) _r2.d);
    return _r0.i;
//XMLVM_END_WRAPPER[java.lang.Double: int compareTo(java.lang.Double)]
}

public override sbyte byteValue(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: byte byteValue()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.i = (int) _r0.d;
    _r0.i = (_r0.i << 24) >> 24;
    return (sbyte) _r0.i;
//XMLVM_END_WRAPPER[java.lang.Double: byte byteValue()]
}

public static long doubleToLongBits(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: long doubleToLongBits(double)]
    if (global::System.Double.IsNaN(n1)) {
        return 0x7FF8000000000000L;
    }
    return global::System.BitConverter.ToInt64(global::System.BitConverter.GetBytes(n1),0);
//XMLVM_END_WRAPPER[java.lang.Double: long doubleToLongBits(double)]
}

public static long doubleToRawLongBits(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: long doubleToRawLongBits(double)]
      throw new org.xmlvm._nNotYetImplementedException("native/wrapper method not yet implemented");
//XMLVM_END_WRAPPER[java.lang.Double: long doubleToRawLongBits(double)]
}

public override double doubleValue(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: double doubleValue()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    return _r0.d;
//XMLVM_END_WRAPPER[java.lang.Double: double doubleValue()]
}

public override bool equals(global::java.lang.Object n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: boolean equals(java.lang.Object)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nElement _r4;
    _r4.i = 0;
    _r4.l = 0;
    _r4.f = 0;
    _r4.d = 0;
    global::System.Object _r4_o = null;
    global::org.xmlvm._nElement _r5;
    _r5.i = 0;
    _r5.l = 0;
    _r5.f = 0;
    _r5.d = 0;
    global::System.Object _r5_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r4_o = this;
    _r5_o = n1;
    if (_r5_o == _r4_o) goto label26;
    _r0.i = ((_r5_o != null) && (_r5_o is global::java.lang.Double)) ? 1 : 0;
    if (_r0.i == 0) goto label24;
    _r0.d = ((global::java.lang.Double) _r4_o)._fvalue;
    _r0.l = global::java.lang.Double.doubleToLongBits((double) _r0.d);
    _r5_o = _r5_o;
    _r2.d = ((global::java.lang.Double) _r5_o)._fvalue;
    _r2.l = global::java.lang.Double.doubleToLongBits((double) _r2.d);
    _r0.i = _r0.l > _r2.l ? 1 : (_r0.l == _r2.l ? 0 : -1);
    if (_r0.i == 0) goto label26;
    label24:;
    _r0.i = 0;
    label25:;
    return _r0.i!=0;
    label26:;
    _r0.i = 1;
    goto label25;
//XMLVM_END_WRAPPER[java.lang.Double: boolean equals(java.lang.Object)]
}

public override float floatValue(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: float floatValue()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.f = (float) _r0.d;
    return _r0.f;
//XMLVM_END_WRAPPER[java.lang.Double: float floatValue()]
}

public override int hashCode(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: int hashCode()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nElement _r4;
    _r4.i = 0;
    _r4.l = 0;
    _r4.f = 0;
    _r4.d = 0;
    global::System.Object _r4_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r4_o = this;
    _r0.d = ((global::java.lang.Double) _r4_o)._fvalue;
    _r0.l = global::java.lang.Double.doubleToLongBits((double) _r0.d);
    _r2.i = 32;
    _r2.l = (long) ((ulong) _r0.l) >> (0x3f & (_r2.i));
    _r0.l = _r0.l ^ _r2.l;
    _r0.i = (int) _r0.l;
    return _r0.i;
//XMLVM_END_WRAPPER[java.lang.Double: int hashCode()]
}

public override int intValue(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: int intValue()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.i = (int) _r0.d;
    return _r0.i;
//XMLVM_END_WRAPPER[java.lang.Double: int intValue()]
}

public virtual bool isInfinite(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: boolean isInfinite()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.i = global::java.lang.Double.isInfinite((double) _r0.d) ? 1 : 0;
    return _r0.i!=0;
//XMLVM_END_WRAPPER[java.lang.Double: boolean isInfinite()]
}

public static bool isInfinite(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: boolean isInfinite(double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2.d = n1;
    _r0.d = global::System.Double.PositiveInfinity;
    _r0.i = _r2.d > _r0.d ? 1 : (_r2.d == _r0.d ? 0 : -1);
    if (_r0.i == 0) goto label14;
    _r0.d = global::System.Double.NegativeInfinity;
    _r0.i = _r2.d > _r0.d ? 1 : (_r2.d == _r0.d ? 0 : -1);
    if (_r0.i == 0) goto label14;
    _r0.i = 0;
    label13:;
    return _r0.i!=0;
    label14:;
    _r0.i = 1;
    goto label13;
//XMLVM_END_WRAPPER[java.lang.Double: boolean isInfinite(double)]
}

public virtual bool isNaN(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: boolean isNaN()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.i = global::java.lang.Double.isNaN((double) _r0.d) ? 1 : 0;
    return _r0.i!=0;
//XMLVM_END_WRAPPER[java.lang.Double: boolean isNaN()]
}

public static bool isNaN(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: boolean isNaN(double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r1.d = n1;
    _r0.i = _r1.d > _r1.d ? 1 : (_r1.d == _r1.d ? 0 : -1);
    if (_r0.i == 0) goto label6;
    _r0.i = 1;
    label5:;
    return _r0.i!=0;
    label6:;
    _r0.i = 0;
    goto label5;
//XMLVM_END_WRAPPER[java.lang.Double: boolean isNaN(double)]
}

public static double longBitsToDouble(long n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: double longBitsToDouble(long)]
    return global::System.BitConverter.ToDouble(global::System.BitConverter.GetBytes(n1),0);
//XMLVM_END_WRAPPER[java.lang.Double: double longBitsToDouble(long)]
}

public override long longValue(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: long longValue()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.l = (long) _r0.d;
    return _r0.l;
//XMLVM_END_WRAPPER[java.lang.Double: long longValue()]
}

public static double parseDouble(global::java.lang.String n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: double parseDouble(java.lang.String)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = n1;
    _r0.d = global::org.apache.harmony.luni.util.FloatingPointParser.parseDouble((global::java.lang.String) _r2_o);
    return _r0.d;
//XMLVM_END_WRAPPER[java.lang.Double: double parseDouble(java.lang.String)]
}

public override short shortValue(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: short shortValue()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0.i = (int) _r0.d;
    _r0.i = (_r0.i << 16) >> 16;
    return (short) _r0.i;
//XMLVM_END_WRAPPER[java.lang.Double: short shortValue()]
}

public override global::System.Object toString(){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: java.lang.String toString()]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r2_o = this;
    _r0.d = ((global::java.lang.Double) _r2_o)._fvalue;
    _r0_o = global::java.lang.Double.toString((double) _r0.d);
    return (global::java.lang.String) _r0_o;
//XMLVM_END_WRAPPER[java.lang.Double: java.lang.String toString()]
}

public static global::System.Object toString(double n1){
    return org.xmlvm._nUtil.toJavaString(n1.ToString());
}

public static global::System.Object valueOf(global::java.lang.String n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: java.lang.Double valueOf(java.lang.String)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r3_o = n1;
    _r0_o = new global::java.lang.Double();
    _r1.d = global::java.lang.Double.parseDouble((global::java.lang.String) _r3_o);
    ((global::java.lang.Double) _r0_o).@this((double) _r1.d);
    return (global::java.lang.Double) _r0_o;
//XMLVM_END_WRAPPER[java.lang.Double: java.lang.Double valueOf(java.lang.String)]
}

public static int compare(double n1, double n2){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: int compare(double, double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nElement _r4;
    _r4.i = 0;
    _r4.l = 0;
    _r4.f = 0;
    _r4.d = 0;
    global::System.Object _r4_o = null;
    global::org.xmlvm._nElement _r5;
    _r5.i = 0;
    _r5.l = 0;
    _r5.f = 0;
    _r5.d = 0;
    global::System.Object _r5_o = null;
    global::org.xmlvm._nElement _r6;
    _r6.i = 0;
    _r6.l = 0;
    _r6.f = 0;
    _r6.d = 0;
    global::System.Object _r6_o = null;
    global::org.xmlvm._nElement _r7;
    _r7.i = 0;
    _r7.l = 0;
    _r7.f = 0;
    _r7.d = 0;
    global::System.Object _r7_o = null;
    global::org.xmlvm._nElement _r8;
    _r8.i = 0;
    _r8.l = 0;
    _r8.f = 0;
    _r8.d = 0;
    global::System.Object _r8_o = null;
    global::org.xmlvm._nElement _r9;
    _r9.i = 0;
    _r9.l = 0;
    _r9.f = 0;
    _r9.d = 0;
    global::System.Object _r9_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r6.d = n1;
    _r8.d = n2;
    _r5.i = 63;
    _r4.i = 1;
    _r3.i = 0;
    _r2.i = -1;
    _r0.i = _r6.d > _r8.d ? 1 : (_r6.d == _r8.d ? 0 : -1);
    if (_r0.i <= 0) goto label11;
    _r0.i = _r4.i;
    label10:;
    return _r0.i;
    label11:;
    _r0.i = _r8.d > _r6.d ? 1 : (_r8.d == _r6.d ? 0 : -1);
    if (_r0.i <= 0) goto label17;
    _r0.i = _r2.i;
    goto label10;
    label17:;
    _r0.i = _r6.d > _r8.d ? 1 : (_r6.d == _r8.d ? 0 : -1);
    if (_r0.i != 0) goto label29;
    _r0.d = 0.0D;
    _r0.i = _r0.d > _r6.d ? 1 : (_r0.d == _r6.d ? 0 : -1);
    if (_r0.i == 0) goto label29;
    _r0.i = _r3.i;
    goto label10;
    label29:;
    _r0.i = global::java.lang.Double.isNaN((double) _r6.d) ? 1 : 0;
    if (_r0.i == 0) goto label45;
    _r0.i = global::java.lang.Double.isNaN((double) _r8.d) ? 1 : 0;
    if (_r0.i == 0) goto label43;
    _r0.i = _r3.i;
    goto label10;
    label43:;
    _r0.i = _r4.i;
    goto label10;
    label45:;
    _r0.i = global::java.lang.Double.isNaN((double) _r8.d) ? 1 : 0;
    if (_r0.i == 0) goto label53;
    _r0.i = _r2.i;
    goto label10;
    label53:;
    _r0.l = global::java.lang.Double.doubleToRawLongBits((double) _r6.d);
    _r2.l = global::java.lang.Double.doubleToRawLongBits((double) _r8.d);
    _r0.l = _r0.l >> (0x3f & _r5.i);
    _r2.l = _r2.l >> (0x3f & _r5.i);
    _r0.l = _r0.l - _r2.l;
    _r0.i = (int) _r0.l;
    goto label10;
//XMLVM_END_WRAPPER[java.lang.Double: int compare(double, double)]
}

public static global::System.Object valueOf(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: java.lang.Double valueOf(double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r1.d = n1;
    _r0_o = new global::java.lang.Double();
    ((global::java.lang.Double) _r0_o).@this((double) _r1.d);
    return (global::java.lang.Double) _r0_o;
//XMLVM_END_WRAPPER[java.lang.Double: java.lang.Double valueOf(double)]
}

public static global::System.Object toHexString(double n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: java.lang.String toHexString(double)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nElement _r3;
    _r3.i = 0;
    _r3.l = 0;
    _r3.f = 0;
    _r3.d = 0;
    global::System.Object _r3_o = null;
    global::org.xmlvm._nElement _r4;
    _r4.i = 0;
    _r4.l = 0;
    _r4.f = 0;
    _r4.d = 0;
    global::System.Object _r4_o = null;
    global::org.xmlvm._nElement _r5;
    _r5.i = 0;
    _r5.l = 0;
    _r5.f = 0;
    _r5.d = 0;
    global::System.Object _r5_o = null;
    global::org.xmlvm._nElement _r6;
    _r6.i = 0;
    _r6.l = 0;
    _r6.f = 0;
    _r6.d = 0;
    global::System.Object _r6_o = null;
    global::org.xmlvm._nElement _r7;
    _r7.i = 0;
    _r7.l = 0;
    _r7.f = 0;
    _r7.d = 0;
    global::System.Object _r7_o = null;
    global::org.xmlvm._nElement _r8;
    _r8.i = 0;
    _r8.l = 0;
    _r8.f = 0;
    _r8.d = 0;
    global::System.Object _r8_o = null;
    global::org.xmlvm._nElement _r9;
    _r9.i = 0;
    _r9.l = 0;
    _r9.f = 0;
    _r9.d = 0;
    global::System.Object _r9_o = null;
    global::org.xmlvm._nElement _r10;
    _r10.i = 0;
    _r10.l = 0;
    _r10.f = 0;
    _r10.d = 0;
    global::System.Object _r10_o = null;
    global::org.xmlvm._nElement _r11;
    _r11.i = 0;
    _r11.l = 0;
    _r11.f = 0;
    _r11.d = 0;
    global::System.Object _r11_o = null;
    global::org.xmlvm._nElement _r12;
    _r12.i = 0;
    _r12.l = 0;
    _r12.f = 0;
    _r12.d = 0;
    global::System.Object _r12_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r11.d = n1;
    _r0.i = _r11.d > _r11.d ? 1 : (_r11.d == _r11.d ? 0 : -1);
    if (_r0.i == 0) goto label7;
    // Value=NaN
    _r11_o = new global::java.lang.String();
    ((global::java.lang.String)_r11_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)78)), unchecked((char) unchecked((uint) 97)), unchecked((char) unchecked((uint) 78))}));
    label6:;
    return (global::java.lang.String) _r11_o;
    label7:;
    _r0.d = global::System.Double.PositiveInfinity;
    _r0.i = _r11.d > _r0.d ? 1 : (_r11.d == _r0.d ? 0 : -1);
    if (_r0.i != 0) goto label16;
    // Value=Infinity
    _r11_o = new global::java.lang.String();
    ((global::java.lang.String)_r11_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)73)), unchecked((char) unchecked((uint) 110)), unchecked((char) unchecked((uint) 102)), unchecked((char) unchecked((uint) 105)), unchecked((char) unchecked((uint) 110)), unchecked((char) unchecked((uint) 105)), unchecked((char) unchecked((uint) 116)), unchecked((char) unchecked((uint) 121))}));
    goto label6;
    label16:;
    _r0.d = global::System.Double.NegativeInfinity;
    _r0.i = _r11.d > _r0.d ? 1 : (_r11.d == _r0.d ? 0 : -1);
    if (_r0.i != 0) goto label25;
    // Value=-Infinity
    _r11_o = new global::java.lang.String();
    ((global::java.lang.String)_r11_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)45)), unchecked((char) unchecked((uint) 73)), unchecked((char) unchecked((uint) 110)), unchecked((char) unchecked((uint) 102)), unchecked((char) unchecked((uint) 105)), unchecked((char) unchecked((uint) 110)), unchecked((char) unchecked((uint) 105)), unchecked((char) unchecked((uint) 116)), unchecked((char) unchecked((uint) 121))}));
    goto label6;
    label25:;
    _r11.l = global::java.lang.Double.doubleToLongBits((double) _r11.d);
    _r0.l = -9223372036854775808L;
    _r0.l = _r0.l & _r11.l;
    _r2.l = 0L;
    _r0.i = _r0.l > _r2.l ? 1 : (_r0.l == _r2.l ? 0 : -1);
    if (_r0.i == 0) goto label68;
    _r0.i = 1;
    label39:;
    _r1.l = 9218868437227405312L;
    _r1.l = _r1.l & _r11.l;
    _r3.i = 52;
    _r1.l = (long) ((ulong) _r1.l) >> (0x3f & (_r3.i));
    _r3.l = 4503599627370495L;
    _r11.l = _r11.l & _r3.l;
    _r3.l = 0L;
    _r3.i = _r1.l > _r3.l ? 1 : (_r1.l == _r3.l ? 0 : -1);
    if (_r3.i != 0) goto label73;
    _r3.l = 0L;
    _r3.i = _r11.l > _r3.l ? 1 : (_r11.l == _r3.l ? 0 : -1);
    if (_r3.i != 0) goto label73;
    if (_r0.i == 0) goto label70;
    // Value=-0x0.0p0
    _r11_o = new global::java.lang.String();
    ((global::java.lang.String)_r11_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)45)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 120)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 46)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 112)), unchecked((char) unchecked((uint) 48))}));
    goto label6;
    label68:;
    _r0.i = 0;
    goto label39;
    label70:;
    // Value=0x0.0p0
    _r11_o = new global::java.lang.String();
    ((global::java.lang.String)_r11_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)48)), unchecked((char) unchecked((uint) 120)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 46)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 112)), unchecked((char) unchecked((uint) 48))}));
    goto label6;
    label73:;
    _r3_o = new global::java.lang.StringBuilder();
    _r4.i = 10;
    ((global::java.lang.StringBuilder) _r3_o).@this((int) _r4.i);
    if (_r0.i == 0) goto label157;
    // Value=-0x
    _r0_o = new global::java.lang.String();
    ((global::java.lang.String)_r0_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)45)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 120))}));
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r0_o);
    label87:;
    _r4.l = 0L;
    _r0.i = _r1.l > _r4.l ? 1 : (_r1.l == _r4.l ? 0 : -1);
    if (_r0.i != 0) goto label175;
    // Value=0.
    _r0_o = new global::java.lang.String();
    ((global::java.lang.String)_r0_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)48)), unchecked((char) unchecked((uint) 46))}));
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r0_o);
    _r0.i = 13;
    _r10.i = _r0.i;
    _r0.l = _r11.l;
    _r11.i = _r10.i;
    label103:;
    _r4.l = 0L;
    _r12.i = _r0.l > _r4.l ? 1 : (_r0.l == _r4.l ? 0 : -1);
    if (_r12.i == 0) goto label118;
    _r4.l = 15L;
    _r4.l = _r4.l & _r0.l;
    _r6.l = 0L;
    _r12.i = _r4.l > _r6.l ? 1 : (_r4.l == _r6.l ? 0 : -1);
    if (_r12.i == 0) goto label163;
    label118:;
    _r12_o = global::java.lang.Long.toHexString((long) _r0.l);
    _r4.l = 0L;
    _r0.i = _r0.l > _r4.l ? 1 : (_r0.l == _r4.l ? 0 : -1);
    if (_r0.i == 0) goto label143;
    _r0.i = ((global::java.lang.String) _r12_o).length();
    if (_r11.i <= _r0.i) goto label143;
    _r0.i = ((global::java.lang.String) _r12_o).length();
    _r11.i = _r11.i - _r0.i;
    label139:;
    _r0.i = _r11.i + -1;
    if (_r11.i != 0) goto label168;
    label143:;
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r12_o);
    // Value=p-1022
    _r11_o = new global::java.lang.String();
    ((global::java.lang.String)_r11_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)112)), unchecked((char) unchecked((uint) 45)), unchecked((char) unchecked((uint) 49)), unchecked((char) unchecked((uint) 48)), unchecked((char) unchecked((uint) 50)), unchecked((char) unchecked((uint) 50))}));
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r11_o);
    label151:;
    _r11_o = ((global::java.lang.StringBuilder) _r3_o).toString();
    goto label6;
    label157:;
    // Value=0x
    _r0_o = new global::java.lang.String();
    ((global::java.lang.String)_r0_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)48)), unchecked((char) unchecked((uint) 120))}));
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r0_o);
    goto label87;
    label163:;
    _r12.i = 4;
    _r0.l = (long) ((ulong) _r0.l) >> (0x3f & (_r12.i));
    _r11.i = _r11.i + -1;
    goto label103;
    label168:;
    _r11.i = 48;
    ((global::java.lang.StringBuilder) _r3_o).append((char) _r11.i);
    _r11.i = _r0.i;
    goto label139;
    label175:;
    // Value=1.
    _r0_o = new global::java.lang.String();
    ((global::java.lang.String)_r0_o).@this(new global::org.xmlvm._nArrayAdapter<char>(new char[] {unchecked((char) unchecked((uint)49)), unchecked((char) unchecked((uint) 46))}));
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r0_o);
    _r0.i = 13;
    _r4.l = _r11.l;
    _r11.i = _r0.i;
    label184:;
    _r6.l = 0L;
    _r12.i = _r4.l > _r6.l ? 1 : (_r4.l == _r6.l ? 0 : -1);
    if (_r12.i == 0) goto label199;
    _r6.l = 15L;
    _r6.l = _r6.l & _r4.l;
    _r8.l = 0L;
    _r12.i = _r6.l > _r8.l ? 1 : (_r6.l == _r8.l ? 0 : -1);
    if (_r12.i == 0) goto label244;
    label199:;
    _r12_o = global::java.lang.Long.toHexString((long) _r4.l);
    _r6.l = 0L;
    _r0.i = _r4.l > _r6.l ? 1 : (_r4.l == _r6.l ? 0 : -1);
    if (_r0.i == 0) goto label224;
    _r0.i = ((global::java.lang.String) _r12_o).length();
    if (_r11.i <= _r0.i) goto label224;
    _r0.i = ((global::java.lang.String) _r12_o).length();
    _r11.i = _r11.i - _r0.i;
    label220:;
    _r0.i = _r11.i + -1;
    if (_r11.i != 0) goto label249;
    label224:;
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r12_o);
    _r11.i = 112;
    ((global::java.lang.StringBuilder) _r3_o).append((char) _r11.i);
    _r11.l = 1023L;
    _r11.l = _r1.l - _r11.l;
    _r11_o = global::java.lang.Long.toString((long) _r11.l);
    ((global::java.lang.StringBuilder) _r3_o).append((global::java.lang.String) _r11_o);
    goto label151;
    label244:;
    _r12.i = 4;
    _r4.l = (long) ((ulong) _r4.l) >> (0x3f & (_r12.i));
    _r11.i = _r11.i + -1;
    goto label184;
    label249:;
    _r11.i = 48;
    ((global::java.lang.StringBuilder) _r3_o).append((char) _r11.i);
    _r11.i = _r0.i;
    goto label220;
//XMLVM_END_WRAPPER[java.lang.Double: java.lang.String toHexString(double)]
}

public virtual int compareTo(global::java.lang.Object n1){
//XMLVM_BEGIN_WRAPPER[java.lang.Double: int compareTo(java.lang.Object)]
    global::org.xmlvm._nElement _r0;
    _r0.i = 0;
    _r0.l = 0;
    _r0.f = 0;
    _r0.d = 0;
    global::System.Object _r0_o = null;
    global::org.xmlvm._nElement _r1;
    _r1.i = 0;
    _r1.l = 0;
    _r1.f = 0;
    _r1.d = 0;
    global::System.Object _r1_o = null;
    global::org.xmlvm._nElement _r2;
    _r2.i = 0;
    _r2.l = 0;
    _r2.f = 0;
    _r2.d = 0;
    global::System.Object _r2_o = null;
    global::org.xmlvm._nExceptionAdapter _ex = null;
    _r1_o = this;
    _r2_o = n1;
    _r2_o = _r2_o;
    _r0.i = ((global::java.lang.Double) _r1_o).compareTo((global::java.lang.Double) _r2_o);
    return _r0.i;
//XMLVM_END_WRAPPER[java.lang.Double: int compareTo(java.lang.Object)]
}

//XMLVM_BEGIN_WRAPPER[java.lang.Double]
//XMLVM_END_WRAPPER[java.lang.Double]

} // end of class: Double

} // end of namespace: java.lang