package threadpool;

public class DefaultRejectedExecutionHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable task, CustomThreadPool threadPool) {
        System.err.println("[Rejected] Task " + task.toString() + " was rejected due to overload!");
        throw new RuntimeException("Task rejected: " + task.toString());
    }
}