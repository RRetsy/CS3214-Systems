#include "uthreads.h"
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <time.h>

struct uthreads_mutex lock;
static volatile int shutdown;

static void
contender(void *_counter)
{
    long volatile * counter = _counter;

    while (!shutdown) {
        uthreads_mutex_lock(&lock);
        counter[0]++;
        uthreads_mutex_unlock(&lock);
        uthreads_yield();
    }
}

int
main(int ac, char *av[])
{
    int i, N = ac < 2 ? 2 : atoi(av[1]);
    long threadngotlock[N];
    uthreads_t t[N];

    uthreads_init();
    uthreads_mutex_init(&lock);

    for (i = 0; i < N; i++) {
        t[i] = uthreads_create_thread(contender, threadngotlock + i);
    }

    time_t start = time(NULL);
    // spin until next second
    while (time(NULL) == start)
        uthreads_yield();

    start = time(NULL);
    printf("Created %d threads, time = %ld\n", N, start);
    for (i = 0; i < N; i++)
        threadngotlock[i] = 0;

    while (time(NULL) - start < 3)
        uthreads_yield();

    printf("Took %ld seconds\n", time(NULL) - start);
    for (i = 0; i < N; i++)
        printf("Thread %d acquired lock %ld many times\n", i, threadngotlock[i]);

    shutdown = 1;
    for (i = 0; i < N; i++)
        uthreads_join(t[i]);

    return 0;
}
