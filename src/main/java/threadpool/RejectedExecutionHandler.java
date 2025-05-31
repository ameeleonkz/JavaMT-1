package threadpool;

public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable task, CustomThreadPool threadPool);
}