#include <stdio.h>
#include "uthreads.h"

static void
thread_func(void *arg)
{
    printf("Running thread %ld\n", (long)arg);
}

int
main()
{
    uthreads_init();

    printf("Creating t1\n");
    uthreads_t thread1 = uthreads_create_thread(thread_func, (void *)1);

    printf("Creating t2\n");
    uthreads_t thread2 = uthreads_create_thread(thread_func, (void *)2);

    printf("Join 1\n");
    uthreads_join(thread1);

    printf("Join 2\n");
    uthreads_join(thread2);

    printf("Done\n");
    return 0;
}
