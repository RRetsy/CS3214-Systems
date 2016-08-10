#include "uthreads.h"
#include "list.h"

void uthreads_mutex_init(uthreads_mutex_t m)
{
    /* your implementation goes here. */
    m->holder = NULL;
    list_init(&m->waiters);
}

void uthreads_mutex_lock(uthreads_mutex_t m)
{
    /* your implementation goes here. */
    if (m->holder == NULL)
    {
    	//unlocked
    	m->holder = uthreads_current();
    }
    else
    {
    	//already locked
    	uthreads_t curThread = uthreads_current();
    	list_push_front(&m->waiters, &curThread->elem);
    	uthreads_block();
    }
}

void uthreads_mutex_unlock(uthreads_mutex_t m)
{
    /* your implementation goes here. */
    m->holder = NULL;

    if (!(list_empty(&m->waiters)))
    {
    	struct list_elem * e = list_pop_back(&m->waiters);
        uthreads_t t = list_entry (e, struct thread_control_block, elem);
        uthreads_unblock(t);
    }
}