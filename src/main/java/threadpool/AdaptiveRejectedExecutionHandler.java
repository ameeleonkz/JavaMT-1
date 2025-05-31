package threadpool;

import java.util.concurrent.atomic.AtomicLong;

import threadpool.CustomThreadPool;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Адаптивный обработчик отказов - наиболее сбалансированный подход
 * 
 * Преимущества:
 * - Автоматически адаптируется к нагрузке
 * - Предотвращает потерю критических задач
 * - Защищает от переполнения памяти
 * - Обеспечивает graceful degradation
 * 
 * Недостатки:
 * - Более сложная логика
 * - Требует тонкой настройки параметров
 * - Может влиять на производительность при высокой нагрузке
 */
public class AdaptiveRejectedExecutionHandler implements RejectedExecutionHandler {
    
    private final AtomicLong totalRejections = new AtomicLong(0);
    private final AtomicInteger consecutiveRejections = new AtomicInteger(0);
    private final AtomicLong lastRejectionTime = new AtomicLong(0);
    
    // Конфигурационные параметры
    private final int maxConsecutiveRejections;
    private final long adaptationWindow; // мс
    private final int maxCallerRunsPerWindow;
    private final AtomicInteger callerRunsInWindow = new AtomicInteger(0);
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());
    
    public AdaptiveRejectedExecutionHandler() {
        this(5, 1000, 3); // разумные значения по умолчанию
    }
    
    public AdaptiveRejectedExecutionHandler(int maxConsecutiveRejections, 
                                         long adaptationWindow, 
                                         int maxCallerRunsPerWindow) {
        this.maxConsecutiveRejections = maxConsecutiveRejections;
        this.adaptationWindow = adaptationWindow;
        this.maxCallerRunsPerWindow = maxCallerRunsPerWindow;
    }

    @Override
    public void rejectedExecution(Runnable task, CustomThreadPool threadPool) {
        long currentTime = System.currentTimeMillis();
        totalRejections.incrementAndGet();
        
        // Обновляем окно адаптации
        updateAdaptationWindow(currentTime);
        
        // Определяем стратегию на основе текущего состояния
        RejectionStrategy strategy = determineStrategy(currentTime, threadPool);
        
        switch (strategy) {
            case CALLER_RUNS:
                handleCallerRuns(task, threadPool);
                break;
                
            case DISCARD_OLDEST:
                handleDiscardOldest(task, threadPool);
                break;
                
            case BLOCK_WITH_TIMEOUT:
                handleBlockWithTimeout(task, threadPool);
                break;
                
            case ABORT:
            default:
                handleAbort(task, threadPool);
                break;
        }
        
        updateRejectionMetrics(currentTime);
    }
    
    private enum RejectionStrategy {
        CALLER_RUNS, DISCARD_OLDEST, BLOCK_WITH_TIMEOUT, ABORT
    }
    
    private RejectionStrategy determineStrategy(long currentTime, CustomThreadPool threadPool) {
        int consecutive = consecutiveRejections.get();
        int callerRunsUsed = callerRunsInWindow.get();
        
        // Если система перегружена, но еще можем использовать caller-runs
        if (consecutive < maxConsecutiveRejections && callerRunsUsed < maxCallerRunsPerWindow) {
            return RejectionStrategy.CALLER_RUNS;
        }
        
        // Если очередь полна, пытаемся освободить место
        if (threadPool.getQueueSize() > 0) {
            return RejectionStrategy.DISCARD_OLDEST;
        }
        
        // В критических ситуациях - кратковременная блокировка
        if (consecutive < maxConsecutiveRejections * 2) {
            return RejectionStrategy.BLOCK_WITH_TIMEOUT;
        }
        
        // Последняя мера - отказ
        return RejectionStrategy.ABORT;
    }
    
    private void handleCallerRuns(Runnable task, CustomThreadPool threadPool) {
        if (!threadPool.isShutdown()) {
            callerRunsInWindow.incrementAndGet();
            System.out.println("[Adaptive] Executing task in caller thread due to overload");
            
            try {
                task.run();
                consecutiveRejections.set(0); // Сбрасываем счетчик при успешном выполнении
            } catch (Exception e) {
                System.err.println("[Adaptive] Error executing task in caller thread: " + e.getMessage());
            }
        }
    }
    
    private void handleDiscardOldest(Runnable task, CustomThreadPool threadPool) {
        try {
            // Пытаемся удалить старую задачу и добавить новую
            if (threadPool.removeOldestTask()) {
                threadPool.execute(task);
                System.out.println("[Adaptive] Discarded oldest task to make room for new one");
                consecutiveRejections.decrementAndGet();
            } else {
                handleAbort(task, threadPool);
            }
        } catch (Exception e) {
            handleAbort(task, threadPool);
        }
    }
    
    private void handleBlockWithTimeout(Runnable task, CustomThreadPool threadPool) {
        try {
            // Кратковременная блокировка с таймаутом
            boolean accepted = threadPool.tryExecuteWithTimeout(task, 100); // 100ms timeout
            if (accepted) {
                System.out.println("[Adaptive] Task accepted after brief wait");
                consecutiveRejections.set(0);
            } else {
                handleAbort(task, threadPool);
            }
        } catch (Exception e) {
            handleAbort(task, threadPool);
        }
    }
    
    private void handleAbort(Runnable task, CustomThreadPool threadPool) {
        System.err.println("[Adaptive] Task rejected - system overloaded. " +
                          "Total rejections: " + totalRejections.get() + 
                          ", Consecutive: " + consecutiveRejections.get());
        throw new RuntimeException("Task rejected due to system overload: " + task.toString());
    }
    
    private void updateAdaptationWindow(long currentTime) {
        long windowStart = windowStartTime.get();
        if (currentTime - windowStart > adaptationWindow) {
            windowStartTime.set(currentTime);
            callerRunsInWindow.set(0);
        }
    }
    
    private void updateRejectionMetrics(long currentTime) {
        lastRejectionTime.set(currentTime);
        consecutiveRejections.incrementAndGet();
    }
    
    // Геттеры для мониторинга
    public long getTotalRejections() { return totalRejections.get(); }
    public int getConsecutiveRejections() { return consecutiveRejections.get(); }
    public long getLastRejectionTime() { return lastRejectionTime.get(); }
}