package api.lang;

public class RuntimeException extends java.lang.RuntimeException {

    private Throwable cause;

    public RuntimeException(String message, Throwable cause) {
//#ifdef __ANDROID__
        super(message, cause);
//#else
        super(message);
        this.cause = cause;
//#endif
    }

//#ifndef __ANDROID__

    public Throwable getCause() {
        return cause;
    }

    public void printStackTrace() {
        super.printStackTrace();
        if (cause != null) {
            System.err.print("Caused by: ");
            cause.printStackTrace();
        }
    }

//#endif
    
}
