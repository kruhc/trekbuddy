using System;
using System.Collections.Generic;

namespace java.util
{
    public class _Vector
    {
        private System.Collections.Generic.List<java.lang.Object> backend;

        public void @this()
        {
            @this(16);
        }

        public void @this(int capacity)
        {
            this.backend = new System.Collections.Generic.List<java.lang.Object>(capacity);
        }

        public void @this(int capacity, int increment)
        {
            @this(capacity);
        }

        public bool isEmpty()
        {
            return backend.Count == 0;
        }

        public void addElement(java.lang.Object element) 
        {
            backend.Add(element);
        }
    }
}
