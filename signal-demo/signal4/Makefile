#
# A simple Makefile to build 'sigbad' and 'sigsafe'
#
#  to insure symbol are added for gdb to see		
CFLAGS= -g -O0 


default: sigbad


# build the application 
sigbad:  esh-sys-utils.o sigbad.o 
	$(CC) $(CFLAGS) -o sigbad esh-sys-utils.o sigbad.o

esh-sys-utils.o: esh-sys-utils.c esh-sys-utils.h
	$(CC) $(CFLAGS) -c esh-sys-utils.c

sigbad.o: sigbad.c
	$(CC) $(CFLAGS) -c sigbad.c

safe: esh-sys-utils.o sigsafe.o
	$(CC) $(CFLAGS) -o sigsafe esh-sys-utils.o sigsafe.o  

sigsafe.o: sigsafe.c
	$(CC) $(CFLAGS) -c sigsafe.c

clean:
	rm -f sigbad sigsafe sigbad.o sigsafe.o esh-sys-utils.o
