package threadpool;

import java.util.Queue;
import java.util.LinkedList;

public class LoadBalancer {
    private final int numberOfQueues;
    private final Queue<Runnable>[] taskQueues;
    private int currentQueueIndex;

    @SuppressWarnings("unchecked")
    public LoadBalancer(int numberOfQueues) {
        this.numberOfQueues = numberOfQueues;
        this.taskQueues = new Queue[numberOfQueues];
        for (int i = 0; i < numberOfQueues; i++) {
            taskQueues[i] = new LinkedList<>();
        }
        this.currentQueueIndex = 0;
    }

    public synchronized void distributeTask(Runnable task) {
        taskQueues[currentQueueIndex].offer(task);
        currentQueueIndex = (currentQueueIndex + 1) % numberOfQueues;
    }

    public synchronized Runnable getNextTask(int queueIndex) {
        if (!taskQueues[queueIndex].isEmpty()) {
            return taskQueues[queueIndex].poll();
        }
        return null;
    }

    public int getNumberOfQueues() {
        return numberOfQueues;
    }
}