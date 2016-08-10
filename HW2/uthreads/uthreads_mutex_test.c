#include <stdio.h>
#include "uthreads.h"

struct uthreads_mutex lock1, lock2;

static void
thread_func(void *arg)
{
    printf("Running thread %ld\n", (long)arg);
    uthreads_mutex_lock(&lock1);
    printf("Locked lock 1 thread %ld\n", (long)arg);
    uthreads_mutex_lock(&lock2);
    printf("Locked lock 2 thread %ld\n", (long)arg);
    uthreads_mutex_unlock(&lock2);
    uthreads_mutex_unlock(&lock1);
}

int
main()
{
    uthreads_init();

    uthreads_mutex_init(&lock1);
    uthreads_mutex_init(&lock2);

    uthreads_mutex_lock(&lock1);
    uthreads_mutex_lock(&lock2);
    printf("Creating t1\n");
    uthreads_t thread1 = uthreads_create_thread(thread_func, (void *)1);
    uthreads_yield();
    printf("Creating t2\n");
    uthreads_t thread2 = uthreads_create_thread(thread_func, (void *)2);
    uthreads_yield();

    uthreads_mutex_unlock(&lock2);
    uthreads_mutex_unlock(&lock1);

    printf("Join 1\n");
    uthreads_join(thread1);

    printf("Join 2\n");
    uthreads_join(thread2);
    printf("Done\n");
    return 0;
}
