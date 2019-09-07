package org.microemu.android.util;

public final class WaitableRunnable<T> implements Runnable {

    private final Getter<T> child;
    private final Object lock;
    private T result;
    private boolean done;

    public static interface Getter<T> {
        public T execute();
    }

    public WaitableRunnable(Getter<T> child, Object lock) {
        this.child = child;
        this.lock = lock;
    }

    public void run() {
        synchronized (lock) {
            try {
                result = child.execute();
            } catch (Throwable t) {
                // TODO
            } finally {
                done = true;
                lock.notify();
            }
        }
    }

    public T getValue() {
        final T result;
        synchronized (lock) {
            while (!done) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            result = this.result;
        }
        return result;
    }
}
