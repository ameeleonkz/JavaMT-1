package threadpool;

public enum RejectionPolicy {
    ABORT,           // Бросить исключение (по умолчанию)
    CALLER_RUNS,     // Выполнить в текущем потоке
    DISCARD,         // Молча отбросить задачу
    DISCARD_OLDEST,  // Отбросить самую старую задачу
    BLOCK,           // Заблокировать до освобождения места
    ADAPTIVE         // Адаптивная стратегия
}