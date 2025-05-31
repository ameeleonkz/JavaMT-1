package threadpool;

import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool implements CustomExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final BlockingQueue<Runnable> taskQueue;
    private final List<Thread> workerThreads;
    private final int minSpareThreads;
    private RejectedExecutionHandler rejectedExecutionHandler;
    private volatile boolean isShutdown = false;
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.taskQueue = new ArrayBlockingQueue<>(queueSize);
        this.workerThreads = new ArrayList<>();
        this.minSpareThreads = minSpareThreads;
        this.rejectedExecutionHandler = (task, pool) -> {
            throw new RuntimeException("Task rejected: " + task.toString());
        };

        for (int i = 0; i < corePoolSize; i++) {
            createWorkerThread();
        }
    }

    public CustomThreadPool(ThreadPoolConfig config) {
        this.corePoolSize = config.getCorePoolSize();
        this.maxPoolSize = config.getMaxPoolSize();
        this.keepAliveTime = config.getKeepAliveTime();
        this.timeUnit = config.getTimeUnit();
        this.taskQueue = new ArrayBlockingQueue<>(config.getQueueSize());
        this.workerThreads = new ArrayList<>();
        this.minSpareThreads = config.getMinSpareThreads();
        this.rejectedExecutionHandler = config.getRejectedExecutionHandler();

        for (int i = 0; i < corePoolSize; i++) {
            createWorkerThread();
        }
    }

    @Override
    public synchronized void execute(Runnable task) {
        if (isShutdown) {
            rejectedExecutionHandler.rejectedExecution(task, this);
            return;
        }

        int taskId = taskCounter.incrementAndGet();
        int queueSize = taskQueue.size();
        int activeCount = activeThreads.get();
        
        System.out.println("[Pool] Task accepted into queue #" + taskId + ": " + task.toString() + 
                          " (Queue: " + queueSize + "/" + (taskQueue.size() + taskQueue.remainingCapacity()) + 
                          ", Active threads: " + activeCount + "/" + maxPoolSize + ")");

        if (!taskQueue.offer(task)) {
            if (activeCount < maxPoolSize) {
                createWorkerThread();
                if (!taskQueue.offer(task)) {
                    System.err.println("[Pool] Failed to add task #" + taskId + " even after creating new thread");
                    rejectedExecutionHandler.rejectedExecution(task, this);
                } else {
                    System.out.println("[Pool] Task #" + taskId + " accepted after creating new thread");
                }
            } else {
                System.err.println("[Pool] Queue full, rejecting task #" + taskId);
                rejectedExecutionHandler.rejectedExecution(task, this);
            }
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    private void createWorkerThread() {
        String threadName = "CustomPool-worker-" + threadCounter.incrementAndGet();
        WorkerThread workerThread = new WorkerThread(this, threadName);
        Thread thread = new Thread(workerThread, threadName);
        
        synchronized (this) {
            workerThreads.add(thread);
        }
        
        thread.start();
        activeThreads.incrementAndGet();
        System.out.println("[ThreadFactory] Creating new thread: " + threadName);
    }

    public Runnable getTask() {
        try {
            if (isShutdown) {
                return taskQueue.poll();
            }
            return taskQueue.poll(keepAliveTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        System.out.println("[Pool] Shutdown initiated");
        
        synchronized (this) {
            for (Thread thread : workerThreads) {
                thread.interrupt();
            }
        }
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        System.out.println("[Pool] Immediate shutdown initiated");
        
        List<Runnable> remainingTasks = new ArrayList<>();
        taskQueue.drainTo(remainingTasks);
        
        synchronized (this) {
            for (Thread thread : workerThreads) {
                thread.interrupt();
            }
        }
        
        // Возвращаем список незавершенных задач (если нужно)
        System.out.println("[Pool] " + remainingTasks.size() + " tasks were not executed");
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long timeoutNanos = unit.toNanos(timeout);
        long start = System.nanoTime();
        
        synchronized (this) {
            for (Thread thread : workerThreads) {
                long elapsed = System.nanoTime() - start;
                long remaining = timeoutNanos - elapsed;
                if (remaining <= 0) {
                    return false;
                }
                thread.join(unit.convert(remaining, TimeUnit.NANOSECONDS));
                if (thread.isAlive()) {
                    return false;
                }
            }
        }
        return true;
    }

    public void onWorkerTerminated() {
        activeThreads.decrementAndGet();
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        this.rejectedExecutionHandler = handler;
    }

    public int getActiveThreads() {
        return activeThreads.get();
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public int getQueueSize() {
        return taskQueue.remainingCapacity() + taskQueue.size();
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, timeUnit);
    }

    public boolean removeOldestTask() {
        return taskQueue.poll() != null;
    }

    public boolean tryExecuteWithTimeout(Runnable task, long timeoutMs) {
        try {
            return taskQueue.offer(task, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}