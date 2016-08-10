/**
 * Simple cooperative user-level threading package.
 *
 * Created by Godmar Back for CS 3214 Fall 2011.
 */
#include <stdio.h>
#include <stdlib.h>

#include "uthreads.h"
#define UTHREAD_STACK_SIZE (128*1024)

/* Ready Queue. */
static struct list ready_queue;

/* TCB for main thread. */
static struct thread_control_block main_tcb; 

/* The current thread. */
static uthreads_t current;

/**
 * Schedule next ready thread.
 */
static void 
schedule(void) 
{
    if (list_empty(&ready_queue)) {
        printf("Empty ready queue. Deadlock?\n");
        abort();
    }

    struct thread_control_block *next;
    next = list_entry(list_pop_front(&ready_queue), struct thread_control_block, elem);
    if (current != next) {
        struct thread_control_block *prev = current;
        if (prev->state == RUNNING) // avoid resurrecting dead threads
            prev->state = READY;
        next->state = RUNNING;
        current = next;
        swapcontext(&prev->context, &next->context);
    }
}

/**
 * Add current thread to ready queue and let scheduler pick
 * a new thread from ready queue.
 */
void 
uthreads_yield(void) 
{
    list_push_back(&ready_queue, &current->elem);
    schedule();
}

/**
 * Initialize uthreads.
 */
void 
uthreads_init(void) 
{
    list_init(&ready_queue);
    main_tcb.state = RUNNING;
    current = &main_tcb;
}

/* Return current uthread. */
uthreads_t 
uthreads_current()
{
    return current;
}

/**
 * 'makecontext' supports only 'int' arguments, not allowing void * to be
 * passed.  Use 'newthread' as a singleton temporary to hold new threads
 * until they reach 'new_thread_body'.
 */
static struct thread_control_block *newthread;
static void new_thread_body(void) {
    struct thread_control_block * me = newthread;
    current = me; 
    uthreads_yield();       // wait until properly scheduled
    me->body(me->arg0);
    me->state = DEAD;
    schedule();
}

/**
 * Create a new uthread. 
 */
uthreads_t 
uthreads_create_thread(uthreads_body_function_t body, void * arg0)
{
    struct thread_control_block * tcb = malloc(sizeof *tcb);
    tcb->state = READY;
    tcb->body = body;
    tcb->arg0 = arg0;
    getcontext(&tcb->context);
    tcb->context.uc_stack.ss_sp = malloc(UTHREAD_STACK_SIZE);
    tcb->context.uc_stack.ss_size = UTHREAD_STACK_SIZE;
    makecontext(&tcb->context, (void (*)(void))new_thread_body, 0);

    list_push_back(&ready_queue, &current->elem);
    newthread = tcb;
    swapcontext(&current->context, &tcb->context);
    return tcb;
}

/**
 * Join a thread.
 */
void 
uthreads_join(struct thread_control_block * t)
{
    while (t->state != DEAD)
        uthreads_yield();
}

/**
 * Put current thread into blocked state.
 * Schedule another thread (if any is ready.)
 */
void 
uthreads_block()
{
    uthreads_t current = uthreads_current();
    current->state = BLOCKED;
    schedule();
}

/**
 * Unblock a given thread.
 * Places it in the READY state and ready queue.
 * Does not automatically schedule it.
 */
void 
uthreads_unblock(uthreads_t t)
{
    t->state = READY;
    list_push_back(&ready_queue, &t->elem);
}
