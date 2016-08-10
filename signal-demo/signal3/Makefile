#
# A simple Makefile to build 'quad3'
#
#  to insure symbol are added for gdb to see		
CFLAGS= -g  -O0

OBJECTS=rngs.o esh-sys-utils.o main.o
HEADERS=rngs.h esh-sys-utils.h

default: quad3 


# build the application 
quad3:   esh-sys-utils.o rngs.o main.o 
	$(CC) $(CFLAGS) -o quad3  esh-sys-utils.o rngs.o main.o  

esh-sys-utils.o: esh-sys-utils.c esh-sys-utils.h
	$(CC) $(CFLAGS) -c esh-sys-utils.c

rngs.o : rngs.c rngs.h
	$(CC) $(CFLAGS)  -c rngs.c

main.o: main.c
	$(CC) $(CFLAGS) -c main.c

clean:
	rm -f $(OBJECTS) quad3
