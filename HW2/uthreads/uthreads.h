/**
 * Simple cooperative user-level threading package.
 *
 * Created by Godmar Back for CS 3214 Fall 2011.
 */

#include <ucontext.h>
#include "list.h"

/* Thread States. */
enum thread_states { READY, RUNNING, BLOCKED, DEAD };

/* A thread body. */
typedef void (* uthreads_body_function_t)(void *);

/* A uthreads thread control block. */
typedef struct thread_control_block {
    enum thread_states state;       // thread state
    ucontext_t  context;            // last saved context
    uthreads_body_function_t body;  // body
    void *arg0;                     // argument passed to body
    struct list_elem elem;          // list element
} * uthreads_t;

/* Create a new uthread. */
uthreads_t uthreads_create_thread(uthreads_body_function_t body, void *arg0);

/* Yield the CPU. */
void uthreads_yield(void);

/* Initialize uthreads.  Must be called before any other uthreads* function. */
void uthreads_init(void);

/* Return the current thread. */
uthreads_t uthreads_current();

/* Wait for a thread to exit. */
void uthreads_join(uthreads_t t);

/* Block this thread.  Used internally only. */
void uthreads_block();

/* Unblock a thread.  Used internally only. */
void uthreads_unblock(uthreads_t t);

/**
 * Support for mutexes.
 */
typedef struct uthreads_mutex {
    uthreads_t holder;          // if NULL, mutex is available; else thread holding mutex
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

