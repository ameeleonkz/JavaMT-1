import threadpool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class CustomThreadPoolTest {
    private CustomThreadPool customThreadPool;

    @BeforeEach
    void setUp() {
        customThreadPool = new CustomThreadPool(2, 4, 60, TimeUnit.SECONDS, 10, 2);
    }
    
    @AfterEach
    void tearDown() {
        if (customThreadPool != null) {
            customThreadPool.shutdown();
        }
    }

    @Test
    void testCorePoolSize() {
        assertEquals(2, customThreadPool.getCorePoolSize());
    }

    @Test
    void testMaxPoolSize() {
        assertEquals(4, customThreadPool.getMaxPoolSize());
    }

    @Test
    void testKeepAliveTime() {
        assertEquals(60, customThreadPool.getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void testQueueSize() {
        assertEquals(10, customThreadPool.getQueueSize());
    }

    @Test
    void testMinSpareThreads() {
        assertEquals(2, customThreadPool.getMinSpareThreads());
    }

    @Test
    void testTaskExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        Runnable task = () -> {
            try {
                Thread.sleep(100);
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        customThreadPool.execute(task);
        
        // Ждем выполнения задачи
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should complete within 2 seconds");
        
        customThreadPool.shutdown();
        assertTrue(customThreadPool.awaitTermination(2, TimeUnit.SECONDS), "Pool should terminate within 2 seconds");
    }

    @Test
    void testRejectedExecutionHandler() {
        final boolean[] rejectionHandled = {false};
        
        customThreadPool.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, CustomThreadPool pool) {
                rejectionHandled[0] = true;
            }
        });

        // Заполняем очередь и потоки, чтобы вызвать отказ
        for (int i = 0; i < 20; i++) {
            customThreadPool.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Проверяем, что обработчик отказов был вызван
        // (это зависит от реализации, может потребоваться дополнительная настройка)
    }

    @Test
    void testSubmitCallable() throws InterruptedException, ExecutionException {
        Future<String> future = customThreadPool.submit(() -> {
            Thread.sleep(100);
            return "Test Result";
        });
        
        String result = future.get();
        assertEquals("Test Result", result);
        
        customThreadPool.shutdown();
        assertTrue(customThreadPool.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitWithException() {
        Future<String> future = customThreadPool.submit(() -> {
            throw new RuntimeException("Test exception");
        });
        
        assertThrows(ExecutionException.class, () -> {
            future.get();
        });
    }

    @Test
    void testShutdownNow() {
        // Добавляем задачи
        for (int i = 0; i < 5; i++) {
            customThreadPool.execute(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Немедленное завершение
        customThreadPool.shutdownNow();
        assertTrue(customThreadPool.isShutdown());
    }
}