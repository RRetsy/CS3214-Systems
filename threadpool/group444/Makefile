#
# Treat warnings as errors. This seems to be the only way to 
# convince some students of the importance of ensuring that
# their code compiles without warnings before starting to debug.
#
# Do not change this line.  We will not use your copy of the Makefile 
# we will use *this* Makefile to run check.py when grading.
#
#CFLAGS=-Wall -O3 -Werror
CFLAGS=-Wall -O1 -g -Werror
LDLIBS=-lpthread

# Use make's default rules
all: threadpool_test quicksort

threadpool_test: threadpool_test.o threadpool.o list.o

quicksort: quicksort.o threadpool.o list.o

clean:
	rm -f *.o threadpool_test quicksort

