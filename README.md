# README.md

# Custom Thread Pool

Этот проект представляет собой реализацию пула потоков с настраиваемыми параметрами, который позволяет эффективно управлять распределением задач между потоками. Пул потоков разработан с учетом высоких нагрузок и гибкости в настройках.

## Установка

1. Соберите проект с помощью Maven:
   ```
   mvn clean install
   ```

2. Запустите проект с помощью Maven:
   ```
   mvn compile exec:java -DskipTests -Dexec.mainClass=Main > log 2>&1
   ```

## Дополнительно

Почему адаптивная стратегия наиболее сбалансирована:

Преимущества:
- Адаптивность - автоматически подстраивается под нагрузку
- Graceful degradation - постепенное снижение производительности вместо резкого отказа
- Защита от потери данных - критические задачи выполняются в caller thread
- Back-pressure - естественное замедление отправителей при перегрузке
- Мониторинг - встроенные метрики для анализа производительности

Недостатки:

- Сложность - более сложная логика, требующая тестирования
- Настройка - требует тонкой настройки параметров для конкретного приложения
- Overhead - дополнительные проверки могут влиять на производительность
- Непредсказуемость - поведение может изменяться в зависимости от нагрузки

Для критических систем рекомендуется использовать AdaptiveRejectedExecutionHandler

## Анализ производительности

=== Тест с 50 задачами ===

--- Light CPU ---
Custom ThreadPool   :  10,04 ms avg (throughput:   378,56 tasks/sec) [3 runs]
ThreadPoolExecutor  :  10,00 ms avg (throughput:   383,55 tasks/sec) [3 runs]
FixedThreadPool(4)  :  10,00 ms avg (throughput:   383,50 tasks/sec) [3 runs]
CachedThreadPool    :  10,05 ms avg (throughput:   732,99 tasks/sec) [3 runs]

--- Light I/O ---
Custom ThreadPool   :  23,11 ms avg (throughput:   162,99 tasks/sec) [3 runs]
ThreadPoolExecutor  :  23,09 ms avg (throughput:   164,82 tasks/sec) [3 runs]
FixedThreadPool(4)  :  23,03 ms avg (throughput:   165,45 tasks/sec) [3 runs]
CachedThreadPool    :  22,73 ms avg (throughput:  1918,52 tasks/sec) [3 runs]