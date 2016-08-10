#include "uthreads.h"

void 
uthreads_sem_init(uthreads_sem_t s, int initial)
{
    /* your implementation goes here. */
    s->count = initial;
    list_init(&s->waiters);
}

void 
uthreads_sem_post(uthreads_sem_t s)
{
    /* your implementation goes here. */
	//increment, wake up waiter if any
	s->count++;

    if (!list_empty(&s->waiters))
    {
        struct list_elem * e = list_pop_back(&s->waiters);
        uthreads_t t = list_entry (e, struct thread_control_block, elem);
        uthreads_unblock(t);
    }
}

void 
uthreads_sem_wait(uthreads_sem_t s)
{
    /* your implementation goes here. */
    //decrement or wait
    if (s->count == 0)
    {
        uthreads_t curThread = uthreads_current();
        list_push_front(&s->waiters, &curThread->elem);
        uthreads_block();
    }
    s->count--;
}