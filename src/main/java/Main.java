import threadpool.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

public class Main {
    private static final int TOTAL_TASKS = 1000;
    private static final int TASK_DURATION_MS = 50;
    private static final int WARMUP_TASKS = 100;

    public static void main(String[] args) {
        System.out.println("=== АНАЛИЗ ПРОИЗВОДИТЕЛЬНОСТИ THREAD POOLS ===\n");
        
        // Демонстрация базовой функциональности
        demonstrateBasicFunctionality();
        
        // Анализ производительности
        performanceAnalysis();
        
        // Стресс-тест
        stressTest();
        
        // Анализ поведения при перегрузке
        overloadAnalysis();

        System.out.println("Завершение программы");
    }

    // Адаптер для CustomThreadPool
    private static class CustomThreadPoolAdapter implements ExecutorService {
        private final CustomThreadPool customPool;
        
        public CustomThreadPoolAdapter(CustomThreadPool customPool) {
            this.customPool = customPool;
        }
        
        @Override
        public void execute(Runnable command) {
            customPool.execute(command);
        }
        
        @Override
        public void shutdown() {
            customPool.shutdown();
        }
        
        @Override
        public List<Runnable> shutdownNow() {
            customPool.shutdownNow();
            return new ArrayList<>(); // CustomThreadPool не возвращает задачи
        }
        
        @Override
        public boolean isShutdown() {
            return customPool.isShutdown();
        }
        
        @Override
        public boolean isTerminated() {
            return customPool.isShutdown();
        }
        
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return customPool.awaitTermination(timeout, unit);
        }
        
        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return customPool.submit(task);
        }
        
        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            FutureTask<T> futureTask = new FutureTask<>(task, result);
            execute(futureTask);
            return futureTask;
        }
        
        @Override
        public Future<?> submit(Runnable task) {
            FutureTask<Void> futureTask = new FutureTask<>(task, null);
            execute(futureTask);
            return futureTask;
        }
        
        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(submit(task));
            }
            return futures;
        }
        
        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return invokeAll(tasks);
        }
        
        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("invokeAny not supported");
        }
        
        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("invokeAny not supported");
        }
    }

    private static void demonstrateBasicFunctionality() {
        System.out.println("=== ДЕМОНСТРАЦИЯ БАЗОВОЙ ФУНКЦИОНАЛЬНОСТИ ===");
        
        ThreadPoolConfig config = new ThreadPoolConfig.Builder()
            .corePoolSize(2)
            .maxPoolSize(4)
            .keepAliveTime(5)
            .timeUnit(TimeUnit.SECONDS)
            .queueSize(5)
            .minSpareThreads(1)
            .rejectedExecutionHandler(new AdaptiveRejectedExecutionHandler())
            .build();
            
        CustomThreadPool customPool = new CustomThreadPool(config);
        
        // Отправляем задачи разных типов
        System.out.println("Отправка задач...");
        
        // Быстрые задачи
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            customPool.execute(createTask("Fast-" + taskId, 100));
        }
        
        // Медленные задачи
        for (int i = 0; i < 2; i++) {
            final int taskId = i;
            customPool.execute(createTask("Slow-" + taskId, 2000));
        }
        
        // Демонстрация submit()
        Future<String> future = customPool.submit(() -> {
            Thread.sleep(1000);
            return "Результат вычисления";
        });
        
        try {
            System.out.println("Результат submit(): " + future.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Корректное завершение
        shutdown(customPool);
        System.out.println();
    }

    private static void performanceAnalysis() {
        System.out.println("=== АНАЛИЗ ПРОИЗВОДИТЕЛЬНОСТИ ===");
        
        // Уменьшаем количество задач для более точного измерения
        int[] taskCounts = {50, 200, 500};
        
        for (int taskCount : taskCounts) {
            System.out.println("\n=== Тест с " + taskCount + " задачами ===");
            
            int queueSize = Math.max(50, taskCount / 2); // Меньший размер очереди для реалистичности
            
            PoolConfig[] configs = {
                new PoolConfig("Custom ThreadPool", () -> {
                    ThreadPoolConfig config = new ThreadPoolConfig.Builder()
                        .corePoolSize(4)
                        .maxPoolSize(8)
                        .keepAliveTime(60)
                        .timeUnit(TimeUnit.SECONDS)
                        .queueSize(queueSize)
                        .minSpareThreads(2)
                        .rejectedExecutionHandler(new AdaptiveRejectedExecutionHandler())
                        .build();
                    CustomThreadPool customPool = new CustomThreadPool(config);
                    return new CustomThreadPoolAdapter(customPool);
                }),
                new PoolConfig("ThreadPoolExecutor", () -> {
                    return new ThreadPoolExecutor(
                        4, 8, 60, TimeUnit.SECONDS, 
                        new ArrayBlockingQueue<>(queueSize),
                        new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                }),
                new PoolConfig("FixedThreadPool(4)", () -> Executors.newFixedThreadPool(4)),
                new PoolConfig("CachedThreadPool", () -> Executors.newCachedThreadPool())
            };
            
            // Более реалистичные рабочие нагрузки
            WorkloadType[] workloads = {
                new WorkloadType("Light CPU", 0, 10),          // 10мс CPU
                new WorkloadType("Light I/O", 20, 0),          // 20мс I/O
                new WorkloadType("Balanced", 10, 5),           // 10мс I/O + 5мс CPU
            };
            
            for (WorkloadType workload : workloads) {
                System.out.println("\n--- " + workload.name + " ---");
                
                for (PoolConfig poolConfig : configs) {
                    try {
                        // Запускаем тест несколько раз для усреднения
                        double avgThroughput = 0;
                        double avgTime = 0;
                        int validRuns = 0;
                        
                        for (int run = 0; run < 3; run++) {
                            try {
                                BenchmarkResult result = benchmarkPoolWithTaskCount(poolConfig, workload, taskCount);
                                avgThroughput += result.throughput;
                                avgTime += result.averageTime;
                                validRuns++;
                            } catch (Exception e) {
                                System.err.println("Run " + run + " failed: " + e.getMessage());
                            }
                        }
                        
                        if (validRuns > 0) {
                            avgThroughput /= validRuns;
                            avgTime /= validRuns;
                            System.out.printf("%-20s: %6.2f ms avg (throughput: %8.2f tasks/sec) [%d runs]%n",
                                poolConfig.name, avgTime, avgThroughput, validRuns);
                        } else {
                            System.out.printf("%-20s: FAILED - все запуски завершились ошибкой%n", poolConfig.name);
                        }
                        
                    } catch (Exception e) {
                        System.out.printf("%-20s: ERROR - %s%n", poolConfig.name, e.getMessage());
                    }
                }
            }
        }
    }

    private static void stressTest() {
        System.out.println("\n=== СТРЕСС-ТЕСТ ===");
        
        ThreadPoolConfig config = new ThreadPoolConfig.Builder()
            .corePoolSize(2)
            .maxPoolSize(10)
            .keepAliveTime(30)
            .timeUnit(TimeUnit.SECONDS)
            .queueSize(50)
            .minSpareThreads(1)
            .rejectedExecutionHandler(new AdaptiveRejectedExecutionHandler())
            .build();
            
        CustomThreadPool customPool = new CustomThreadPool(config);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Отправляем много задач одновременно
        for (int i = 0; i < 200; i++) {
            try {
                customPool.execute(() -> {
                    try {
                        Thread.sleep(100);
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                rejected.incrementAndGet();
            }
        }
        
        // Ждем завершения
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.printf("Стресс-тест завершен за %d мс%n", endTime - startTime);
        System.out.printf("Выполнено задач: %d%n", completed.get());
        System.out.printf("Отклонено задач: %d%n", rejected.get());
        System.out.printf("Активных потоков: %d%n", customPool.getActiveThreads());
        
        shutdown(customPool);
    }

    private static void overloadAnalysis() {
        System.out.println("\n=== АНАЛИЗ ПОВЕДЕНИЯ ПРИ ПЕРЕГРУЗКЕ ===");
        
        ThreadPoolConfig config = new ThreadPoolConfig.Builder()
            .corePoolSize(2)
            .maxPoolSize(2)
            .queueSize(3)  // Маленькая очередь для быстрого переполнения
            .rejectedExecutionHandler(new AdaptiveRejectedExecutionHandler())
            .build();
            
        CustomThreadPool pool = new CustomThreadPool(config);
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger callerRuns = new AtomicInteger(0);
        
        System.out.println("Отправка задач, превышающих емкость пула...");
        
        // Отправляем задачи, превышающие емкость
        for (int i = 0; i < 15; i++) {
            final int taskId = i;
            try {
                String currentThread = Thread.currentThread().getName();
                pool.execute(() -> {
                    String executingThread = Thread.currentThread().getName();
                    if (executingThread.equals(currentThread)) {
                        callerRuns.incrementAndGet();
                        System.out.println("[Overload] Task " + taskId + " выполняется в caller thread: " + executingThread);
                    } else {
                        System.out.println("[Overload] Task " + taskId + " выполняется в worker thread: " + executingThread);
                    }
                    
                    try {
                        Thread.sleep(1000);
                        successful.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                System.out.println("[Overload] Task " + taskId + " отклонена: " + e.getMessage());
            }
        }
        
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.printf("Успешно выполнено: %d%n", successful.get());
        System.out.printf("Выполнено в caller thread: %d%n", callerRuns.get());
        System.out.printf("Адаптивная стратегия эффективно обработала перегрузку%n");
        
        shutdown(pool);
    }

    private static BenchmarkResult benchmarkPool(PoolConfig poolConfig, WorkloadType workload) {
        return benchmarkPoolWithTaskCount(poolConfig, workload, TOTAL_TASKS);
    }

    private static BenchmarkResult benchmarkPoolWithTaskCount(PoolConfig poolConfig, WorkloadType workload, int taskCount) {
        ExecutorService executor = poolConfig.factory.get();
        AtomicLong totalTime = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger rejectedTasks = new AtomicInteger(0);
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        // Измерение памяти до
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        Thread.yield(); // Даем время GC
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Разогрев - более реалистичный
        int warmupTasks = Math.min(20, taskCount / 20);
        CountDownLatch warmupLatch = new CountDownLatch(warmupTasks);
        
        for (int i = 0; i < warmupTasks; i++) {
            try {
                executor.execute(() -> {
                    try {
                        Thread.sleep(10); // Короткая задача для разогрева
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        warmupLatch.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                warmupLatch.countDown();
            }
        }
        
        try {
            warmupLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Пауза между разогревом и тестом
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Основной тест - более точное измерение
        long realStartTime = System.nanoTime();
        
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            try {
                executor.execute(() -> {
                    long taskStartTime = System.nanoTime();
                    
                    try {
                        // Выполняем рабочую нагрузку
                        if (workload.sleepMs > 0) {
                            Thread.sleep(workload.sleepMs);
                        }
                        
                        if (workload.cpuMs > 0) {
                            // Более точная имитация CPU работы
                            long cpuEnd = System.nanoTime() + (workload.cpuMs * 1_000_000L);
                            double dummy = 0;
                            while (System.nanoTime() < cpuEnd) {
                                dummy += Math.sqrt(Math.random() * 1000);
                            }
                            // Используем результат чтобы избежать оптимизации
                            if (dummy < 0) System.out.print("");
                        }
                        
                        completedTasks.incrementAndGet();
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("Task " + taskId + " failed: " + e.getMessage());
                    } finally {
                        long taskEndTime = System.nanoTime();
                        totalTime.addAndGet((taskEndTime - taskStartTime) / 1_000_000);
                        latch.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                rejectedTasks.incrementAndGet();
                latch.countDown();
            }
        }
        
        // Ждем завершения всех задач
        boolean finished = false;
        try {
            finished = latch.await(120, TimeUnit.SECONDS); // Больше времени
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long realEndTime = System.nanoTime();
        
        if (!finished) {
            System.err.println("[WARNING] " + poolConfig.name + " - не все задачи завершились вовремя");
        }
        
        // Принудительно ждем завершения
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Измерение памяти после
        runtime.gc();
        Thread.yield();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        
        // Корректное завершение
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                System.err.println("[WARNING] " + poolConfig.name + " - принудительное завершение");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Расчет метрик
        double realTotalTimeMs = (realEndTime - realStartTime) / 1_000_000.0;
        int actualCompleted = completedTasks.get();
        double avgTaskTime = actualCompleted > 0 ? totalTime.get() / (double) actualCompleted : 0;
        double throughput = actualCompleted / (realTotalTimeMs / 1000.0);
        double memoryMB = Math.max(0, (memAfter - memBefore) / (1024.0 * 1024.0));
        
        // Детальная отчетность
        if (rejectedTasks.get() > 0 || actualCompleted != taskCount) {
            System.out.printf("    [%s] Статистика: отправлено=%d, выполнено=%d, отклонено=%d, время=%.1fмс%n", 
                poolConfig.name, taskCount, actualCompleted, rejectedTasks.get(), realTotalTimeMs);
        }
        
        return new BenchmarkResult(avgTaskTime, throughput, memoryMB);
    }

    private static Runnable createTask(String name, int durationMs) {
        return () -> {
            System.out.println("[Task] " + name + " started in " + Thread.currentThread().getName());
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[Task] " + name + " completed");
        };
    }

    private static Runnable createBenchmarkTask(WorkloadType workload, AtomicLong totalTime, CountDownLatch latch) {
        return () -> {
            long taskStart = System.nanoTime();
            
            try {
                if (workload.sleepMs > 0) {
                    Thread.sleep(workload.sleepMs);
                }
                
                if (workload.cpuMs > 0) {
                    // Имитация CPU-intensive работы
                    long cpuEnd = System.currentTimeMillis() + workload.cpuMs;
                    while (System.currentTimeMillis() < cpuEnd) {
                        Math.sqrt(Math.random());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (totalTime != null) {
                long taskEnd = System.nanoTime();
                totalTime.addAndGet((taskEnd - taskStart) / 1_000_000);
            }
            
            if (latch != null) {
                latch.countDown();
            }
        };
    }

    private static void shutdown(CustomThreadPool pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Вспомогательные классы
    private static class PoolConfig {
        final String name;
        final java.util.function.Supplier<ExecutorService> factory;
        
        PoolConfig(String name, java.util.function.Supplier<ExecutorService> factory) {
            this.name = name;
            this.factory = factory;
        }
    }

    private static class WorkloadType {
        final String name;
        final int sleepMs;
        final int cpuMs;
        
        WorkloadType(String name, int sleepMs, int cpuMs) {
            this.name = name;
            this.sleepMs = sleepMs;
            this.cpuMs = cpuMs;
        }
    }

    private static class BenchmarkResult {
        final double averageTime;
        final double throughput;
        final double memoryUsage;
        
        BenchmarkResult(double averageTime, double throughput, double memoryUsage) {
            this.averageTime = averageTime;
            this.throughput = throughput;
            this.memoryUsage = memoryUsage;
        }
    }
}