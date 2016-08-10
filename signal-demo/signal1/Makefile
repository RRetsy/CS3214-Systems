#
# A simple Makefile to build 'quad1'
#
#  set flags for default debugging information for gdb and 
#  surpress optimization for easier tracing 		
CFLAGS= -g -O0 

OBJECTS=main.o rngs.o
HEADERS=rngs.h prime.h

default:  quad1


# build the application 
quad1:  rngs.o  main.o 
	$(CC) $(CFLAGS) -o quad1 rngs.o  main.o -lm  

rngs.o : rngs.c rngs.h
	$(CC) $(CFLAGS)  -c rngs.c

main.o: main.c
	$(CC) $(CFLAGS) -c main.c

clean:
	rm -f $(OBJECTS) quad1 
