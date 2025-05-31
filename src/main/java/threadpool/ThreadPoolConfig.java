package threadpool;

import java.util.concurrent.TimeUnit;

public class ThreadPoolConfig {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final RejectedExecutionHandler rejectedExecutionHandler;

    private ThreadPoolConfig(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveTime = builder.keepAliveTime;
        this.timeUnit = builder.timeUnit;
        this.queueSize = builder.queueSize;
        this.minSpareThreads = builder.minSpareThreads;
        this.rejectedExecutionHandler = builder.rejectedExecutionHandler;
    }

    public static class Builder {
        private int corePoolSize = 1;
        private int maxPoolSize = 1;
        private long keepAliveTime = 60;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private int queueSize = 10;
        private int minSpareThreads = 0;
        private RejectedExecutionHandler rejectedExecutionHandler = new DefaultRejectedExecutionHandler();

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder keepAliveTime(long keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        public Builder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder minSpareThreads(int minSpareThreads) {
            this.minSpareThreads = minSpareThreads;
            return this;
        }

        public Builder rejectedExecutionHandler(RejectedExecutionHandler handler) {
            this.rejectedExecutionHandler = handler;
            return this;
        }

        public ThreadPoolConfig build() {
            return new ThreadPoolConfig(this);
        }
    }

    // Геттеры
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public long getKeepAliveTime() { return keepAliveTime; }
    public TimeUnit getTimeUnit() { return timeUnit; }
    public int getQueueSize() { return queueSize; }
    public int getMinSpareThreads() { return minSpareThreads; }
    public RejectedExecutionHandler getRejectedExecutionHandler() { return rejectedExecutionHandler; }
}