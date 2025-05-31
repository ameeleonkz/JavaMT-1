package threadpool;

import java.util.concurrent.TimeUnit;

public class WorkerThread implements Runnable {
    private final CustomThreadPool threadPool;
    private final String threadName;
    private volatile boolean running = true;

    public WorkerThread(CustomThreadPool threadPool, String threadName) {
        this.threadPool = threadPool;
        this.threadName = threadName;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(threadName);
        System.out.println("[Worker] " + threadName + " started");
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Runnable task = threadPool.getTask();
                if (task != null) {
                    System.out.println("[Worker] " + threadName + " executes " + task.toString());
                    task.run();
                } else if (threadPool.isShutdown()) {
                    break;
                }
            } catch (Exception e) {
                System.err.println("[Worker] " + threadName + " error: " + e.getMessage());
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
        
        System.out.println("[Worker] " + threadName + " terminated");
        threadPool.onWorkerTerminated();
    }

    public void shutdown() {
        running = false;
    }
}