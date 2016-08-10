CC=gcc
CFLAGS= -l pthread -D_REENTRANT -lrt

all: sysstatd
clean all: webserver.c csapp.c sbuf.c list.c
	$(CC) $(CFLAGS) -o sysstatd webserver.c csapp.c sbuf.c list.c
	rm -f *.o *~
sysstatd: webserver.c csapp.c sbuf.c list.c
	$(CC) $(CFLAGS) -o sysstatd webserver.c csapp.c sbuf.c list.c
clean:
	rm -f *~ *.o 
	
