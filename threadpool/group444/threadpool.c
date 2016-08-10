#include <semaphore.h>
#include "list.h"
#include <stdlib.h>
#include <stdbool.h>
#include <pthread.h>
#include "threadpool.h"

typedef struct future 
  {
	struct list_elem elem;
    	thread_pool_callable_func_t func;
	void * data;
	void * ret;
	sem_t sem;
  }future;

typedef struct thread_pool 
  {
   	struct list work_queue;
	pthread_t * tids;
	pthread_mutex_t lock;
	pthread_cond_t thresh;
	int numThreads;
	bool isShuttingDown;
  }thread_pool;

static void * thread(void * vargp)
{
	thread_pool * pool = (thread_pool *) vargp;
	
	while(1)
	{	
		pthread_mutex_lock(&pool->lock);
		if(pool->isShuttingDown)
		{	
			pthread_mutex_unlock(&pool->lock);
			pthread_exit(NULL);
		}

		//while list is empty
		while(list_empty(&pool->work_queue))
		{
			//wait for signal
			pthread_cond_wait(&pool->thresh, &pool->lock);
			if(pool->isShuttingDown)
			{
				pthread_mutex_unlock(&pool->lock);
				pthread_exit(NULL);
			}
		}

		//get a future
		struct list_elem * e = list_pop_back(&pool->work_queue);
		future * curr = list_entry(e, future, elem);
		pthread_cond_signal(&pool->thresh);
		pthread_mutex_unlock(&pool->lock);

		//run future
		curr->ret = (*curr->func)(curr->data);

		//update semaphore
		sem_post(&curr->sem);
	}
	return NULL;
}

/* Create a new thread pool with n threads. */
struct thread_pool * thread_pool_new(int nthreads)
{
	//create pool struct
	thread_pool * pool = malloc(sizeof(thread_pool));

	//init futures
	list_init(&pool->work_queue);

	//init thread array
	pool->tids = malloc(sizeof(pthread_t) * nthreads);

	//init lock and cond var
	pthread_mutex_init(&pool->lock, NULL);
	pthread_cond_init(&pool->thresh, NULL);

	//init thread counters
	pool->isShuttingDown = false;
	pool->numThreads = nthreads;

	//create threads
	int i;
	for(i = 0; i < nthreads; i ++)
	{
		pthread_create(&pool->tids[i], NULL, (void*)&thread, pool);
	}
	return pool;
}

/* Shutdown this thread pool.  May or may not execute already queued tasks. */
void thread_pool_shutdown(struct thread_pool * pool)
{
	//set flag and broadcast
	pthread_mutex_lock(&pool->lock);
	pool->isShuttingDown = true;
	pthread_cond_broadcast(&pool->thresh);
	pthread_mutex_unlock(&pool->lock);

	//wait for executing threads to complete
	//join all working threads
	int i;
	for(i = 0; i < pool->numThreads; i++)
	{
		pthread_join(pool->tids[i], NULL);
	}

	//cleanup
	pthread_mutex_destroy(&pool->lock);
	pthread_cond_destroy(&pool->thresh);
	free(pool->tids);
	free(pool);
}


/* Submit a callable to thread pool and return future.
 * The returned future can be used in future_get() and future_free()
 */
struct future * thread_pool_submit(
        struct thread_pool * pool, 
        thread_pool_callable_func_t callable, 
        void * callable_data)
{
	//create new future with parameters
	future * newFut = malloc(sizeof(future));
	newFut->func = callable;
	newFut->data = callable_data;
 
	//init semaphore
	sem_init(&newFut->sem, 0, 0);

	//add future to list
	pthread_mutex_lock(&pool->lock);
	list_push_front(&pool->work_queue, &newFut->elem);

	//signal
	pthread_cond_signal(&pool->thresh);
	pthread_mutex_unlock(&pool->lock);

	return newFut;
}

/* Make sure that thread pool has completed executing this callable,
 * then return result. */
void * future_get(struct future * theFuture)
{
	//when future is complete return the result
	sem_wait(&theFuture->sem);
	return theFuture->ret;
}

/* Deallocate this future.  Must be called after future_get() */
void future_free(struct future * theFuture)
{
	//free the future
	free(theFuture);
}


