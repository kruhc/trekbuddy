﻿using System;
using System.IO;
using System.IO.IsolatedStorage;
using System.Runtime.CompilerServices;

namespace org.xmlvm
{
    public static class VMExtensions
    {
        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static java.lang.String toJava(this string str)
        {
            return _nUtil.toJavaString(str);
        }

        [MethodImplAttribute(MethodImplOptions.NoInlining)]
        public static string toCSharp(this java.lang.String str)
        {
            return _nUtil.toNativeString(str);
        }

        public static java.lang.Exception ToJavaException(this Exception exception)
        {
            java.lang.Exception je;

            if (exception is IOException || exception is IsolatedStorageException)
            {
                je = new java.io.IOException();
            }
            else if (exception is EndOfStreamException)
            {
                je = new java.io.EOFException();
            }
            else if (exception is ArgumentException)
            {
                je = new java.lang.IllegalArgumentException();
            }
            else if (exception is UnauthorizedAccessException)
            {
                je = new java.lang.IllegalThreadStateException();
            }
            else if (exception is FormatException)
            {
                je = new java.lang.NumberFormatException();
            }
            else if (exception is NullReferenceException)
            {
                je = new java.lang.NullPointerException();
            }
            else if (exception is ArithmeticException)
            {
                je = new java.lang.ArithmeticException();
            }
            else if (exception is IndexOutOfRangeException)
            {
                je = new java.lang.ArrayIndexOutOfBoundsException();
            }
            else if (exception is InvalidCastException)
            {
                je = new java.lang.ClassCastException();
            }
            else
            {
                je = new java.lang.RuntimeException();
            }

            java.lang.String message = new java.lang.String();
            message.@this(new org.xmlvm._nArrayAdapter<char>(exception.ToString().ToCharArray()));
            je.@this(message);

            // TODO strack trace
            // TODO cause

            return je;
        }
    }
}
