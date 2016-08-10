#include "uthreads.h"
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <time.h>

struct sempair {
    int n;
    uthreads_sem_t sigsem;
    uthreads_sem_t waitsem;
};

static void
thread_func(void *_spair)
{
    struct sempair *spair = _spair;
    printf("I'm thread %d, waiting for sem\n", spair->n);
    uthreads_sem_wait(spair->waitsem);
    printf("I'm thread %d, posting for sem\n", spair->n);
    uthreads_sem_post(spair->sigsem);
    free(spair);
}

#define N 10
int
main(int ac, char *av[])
{
    int i;
    uthreads_t t[N];
    struct uthreads_sem sems[N];

    uthreads_init();
    for (i = 0; i < N; i++)
        uthreads_sem_init(sems + i, 0);

    for (i = 0; i < N; i++) {
        struct sempair * p = malloc(sizeof *p);
        p->n = i;
        p->sigsem = sems + i;
        p->waitsem = &sems[(i + 1) % N];
        t[i] = uthreads_create_thread(thread_func, p);
    }

    uthreads_sem_post(&sems[0]);

    for (i = 0; i < N; i++)
        uthreads_join(t[i]);

    return 0;
}
