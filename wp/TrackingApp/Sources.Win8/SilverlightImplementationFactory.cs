using System;

namespace com.codename1.impl
{
    internal class SilverlightImplementationFactory : ImplementationFactory
    {
        private static SilverlightImplementation implementation;

        internal static SilverlightImplementation Implementation
        {
            get
            {
                if (implementation == null)
                {
                    implementation = new SilverlightImplementation();
                }
                return implementation;
            }
        }

        public override object createImplementation()
        {
            return Implementation;
        }
    }
}
