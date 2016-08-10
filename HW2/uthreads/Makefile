all: uthreads_test uthreads_mutex_test uthreads_lock_fairness uthreads_sem_test uthreads_philosophers

UTHREADS_LIB_OBJS=uthreads.o uthreads_mutex.o uthreads_semaphore.o list.o

libuthreads.a: $(UTHREADS_LIB_OBJS) uthreads.h
	ar cr $@ $(UTHREADS_LIB_OBJS)
	ranlib $@

uthreads_test: uthreads_test.c libuthreads.a
	$(CC) -o $@ $< -L. -luthreads

uthreads_mutex_test: uthreads_mutex_test.c libuthreads.a
	$(CC) -o $@ $< -L. -luthreads

uthreads_lock_fairness: uthreads_lock_fairness.c libuthreads.a
	$(CC) -o $@ $< -L. -luthreads

uthreads_sem_test: uthreads_sem_test.c libuthreads.a
	$(CC) -o $@ $< -L. -luthreads

uthreads_philosophers: uthreads_philosophers.c libuthreads.a
	$(CC) -o $@ $< -L. -luthreads

check: all
	@./uthreads_mutex_test | diff - uthreads_mutex_test.out && echo Ok
	@./uthreads_sem_test | diff - uthreads_sem_test.out && echo Ok
	@./uthreads_philosophers | diff - uthreads_philosophers.out && echo Ok

clean:
	/bin/rm *.o uthreads_test uthreads_mutex_test uthreads_lock_fairness uthreads_sem_test uthreads_philosophers libuthreads.a
