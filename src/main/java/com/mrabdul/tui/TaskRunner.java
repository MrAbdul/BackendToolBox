package com.mrabdul.tui;
import java.util.concurrent.*;

public class TaskRunner implements AutoCloseable{

    private final ExecutorService pool;

    public TaskRunner() {
        this.pool = Executors.newCachedThreadPool(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("toolbox-worker-" + t.getId());
                return t;
            }
        });
    }

    public <T> Future<T> submit(Callable<T> task) {
        return pool.submit(task);
    }

    public Future<?> submit(Runnable task) {
        return pool.submit(task);
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
