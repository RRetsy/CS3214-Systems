#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
#include "esh-sys-utils.h"

/*
 * This program demonstrates that calling 'printf'
 * (or puts, for that matter, which is what gcc 
 * quietly changes the call to).
 * is not safe from within a signal handler that's
 * invoked asynchronously (like SIGCHLD's is)
 * 
 * Start the program, and you'll see it "get stuck"
 * after a while.  Attach gdb to it (gdb ./signaldeadlock)
 * "attach <pid>" in gdb, where pid is the process id.
 *
 * @author Godmar Back <godmar@gmail.com>
 * Written for CS 3214 Fall 2009, Virginia Tech
 */
static void
catch_child(int signo, siginfo_t* info, void* context)
{
    /* this call may reenter printf/puts! Bad! */
    printf("Child exited!\n");
    /* reap all children */
    while (waitpid(-1, NULL, WNOHANG) > 0)
        continue;
}

int
main()
{
    esh_signal_sethandler(SIGCHLD, catch_child);

    int i;
    for (i = 0; i < 100000; i++) {
        if (fork() == 0)
            exit(0);

        /* the child is running, it may exit
         * any time.... SIGCHLD may arrive
         * while in printf/puts
         * (this outputs character by character
         * to increase the chance for the deadlock
         * to occur so we don't have to wait long.)
         */
        char *p;
        for (p = "Child started\n"; *p; p++) {
            esh_signal_block(SIGCHLD);
            printf("%c", *p);
            esh_signal_unblock(SIGCHLD);
        }
    }
    return 0;
}
