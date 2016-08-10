/**
 * Support for mutexes.
 */
typedef struct uthreads_mutex {
    uthreads_t holder;          // if NULL, mutex is available; 
                                // else denotes thread holding mutex
    struct list waiters;        // waiting threads, if any.
} *uthreads_mutex_t;

/* Initialize this mutex. */
void uthreads_mutex_init(uthreads_mutex_t m);

/* Acquire this mutex. */
void uthreads_mutex_lock(uthreads_mutex_t m);

/* Release this mutex. */
void uthreads_mutex_unlock(uthreads_mutex_t m);

/**
 * Support for semaphores.
 */
typedef struct uthreads_sem {
    int count;                  // value of semaphore.  Always non-negative.
    struct list waiters;        // waiting threads, if any.
} * uthreads_sem_t;

/* Initialize this semaphore. */
void uthreads_sem_init(uthreads_sem_t s, int initial);

/* Wait on this semaphore. */
void uthreads_sem_wait(uthreads_sem_t s);

/* Post ('signal') this semaphore. */
void uthreads_sem_post(uthreads_sem_t s);

