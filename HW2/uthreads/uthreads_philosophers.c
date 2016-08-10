#include "uthreads.h"
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <time.h>

#define N 5
struct uthreads_sem forks[N];
struct uthreads_sem seated;

static void
philosopher(void *_num)
{
    long i, num = (long)_num;
    for (i = 0; i < 3; i++) {
        uthreads_sem_wait(&seated);
        uthreads_sem_wait(&forks[i]);
        uthreads_sem_wait(&forks[(i+1)%N]);
        printf("Philosopher %d is eating round #%d\n", num, i);
        uthreads_sem_post(&forks[i]);
        uthreads_sem_post(&forks[(i+1)%N]);
        uthreads_sem_post(&seated);
        uthreads_yield();
    }
}

int
main(int ac, char *av[])
{
    long i;
    uthreads_t t[N];
    struct uthreads_sem sems[N];

    uthreads_init();
    uthreads_sem_init(&seated, 4);
    for (i = 0; i < N; i++)
        uthreads_sem_init(forks + i, 1);

    for (i = 0; i < N; i++)
        t[i] = uthreads_create_thread(philosopher, (void *)i);

    for (i = 0; i < N; i++)
        uthreads_join(t[i]);

    return 0;
}
