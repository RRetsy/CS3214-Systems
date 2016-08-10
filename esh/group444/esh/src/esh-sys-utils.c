/*
 * esh - the 'extensible' shell.
 *
 * Utility functions for system calls.
 *
 * Developed by Godmar Back for CS 3214 Fall 2009
 * Virginia Tech.
 */

#include <termios.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <stdarg.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <signal.h>
#include <assert.h>

#include "esh-sys-utils.h"

static const char rcsid [] = "$Id: esh-sys-utils.c,v 1.4 2011/01/21 20:13:06 cs3214 Exp $";

/* Utility function for esh_sys_fatal_error and esh_sys_error */
static void 
vesh_sys_error(char *fmt, va_list ap)
{
    char errmsg[1024];

    strerror_r(errno, errmsg, sizeof errmsg);
    vfprintf(stderr, fmt, ap);
    fprintf(stderr, "%s\n", errmsg);
}

/* Print information about the last syscall error */
void 
esh_sys_error(char *fmt, ...) 
{
    va_list ap;
    va_start(ap, fmt);
    vesh_sys_error(fmt, ap);
    va_end(ap);
}

/* Print information about the last syscall error and then exit */
void
esh_sys_fatal_error(char *fmt, ...) 
{
    va_list ap;
    va_start(ap, fmt);
    vesh_sys_error(fmt, ap);
    va_end(ap);
    exit(EXIT_FAILURE);
}

static int terminal_fd = -1;           /* the controlling terminal */
static struct termios saved_tty_state;  /* the state of the terminal when shell
                                           was started. */

/* Initialize tty support.  Return pointer to saved initial terminal state */
struct termios *
esh_sys_tty_init(void)
{
    char *tty;
    assert(terminal_fd == -1 || !!!"esh_sys_tty_init already called");

    terminal_fd = open(tty = ctermid(NULL), O_RDWR);
    if (terminal_fd == -1)
        esh_sys_fatal_error("opening controlling terminal %s failed: ", tty);

    esh_sys_tty_save(&saved_tty_state);
    return &saved_tty_state;
}

/* Save current terminal settings.
 * This function is used when a job is suspended.*/
void 
esh_sys_tty_save(struct termios *saved_tty_state)
{
    int rc = tcgetattr(terminal_fd, saved_tty_state);
    if (rc == -1)
        esh_sys_fatal_error("tcgetattr failed: ");
}

/* Restore terminal to saved settings.
 * This function is used when resuming a suspended job. */
void
esh_sys_tty_restore(struct termios *saved_tty_state)
{
    int rc;

retry:
    rc = tcsetattr(terminal_fd, TCSADRAIN, saved_tty_state);
    if (rc == -1) {
        /* tcsetattr, apparently, does not restart even with SA_RESTART,
         * so repeat call on EINTR. */
        if (errno == EINTR)
            goto retry;

        esh_sys_fatal_error("could not restore tty attributes tcsetattr: ");
    }
}

/* Get a file descriptor that refers to controlling terminal */
int 
esh_sys_tty_getfd(void)
{
    assert(terminal_fd != -1 || !!!"esh_sys_tty_init() must be called");
    return terminal_fd;
}

/* Return true if this signal is blocked */
bool 
esh_signal_is_blocked(int sig)
{
    sigset_t mask;
    if (sigprocmask(0, NULL, &mask) == -1)
        esh_sys_error("sigprocmask failed while retrieving current mask");

    return sigismember(&mask, sig);
}

/* Helper for esh_signal_block and esh_signal_unblock */
static bool
__mask_signal(int sig, int how)
{
    sigset_t mask, omask;
    sigemptyset(&mask);
    sigaddset(&mask, sig);
    if (sigprocmask(how, &mask, &omask) != 0)
        esh_sys_error("sigprocmask failed for %d/%d", sig, how);
    return sigismember(&omask, sig);
}

/* Block a signal. Returns true it was blocked before */
bool 
esh_signal_block(int sig)
{
    return __mask_signal(sig, SIG_BLOCK);
}

/* Unblock a signal. Returns true it was blocked before */
bool 
esh_signal_unblock(int sig)
{
    return __mask_signal(sig, SIG_UNBLOCK);
}

/* Install signal handler for signal 'sig' */
void
esh_signal_sethandler(int sig, sa_sigaction_t handler)
{
    sigset_t emptymask;

    sigemptyset(&emptymask);
    struct sigaction sa = {
        .sa_sigaction = handler,
        /* do not block any additional signals (besides 'sig') when
         * signal handler is entered. */
        .sa_mask = emptymask,
        /* restart system calls when possible */
        .sa_flags = SA_RESTART | SA_SIGINFO
    };

    if (sigaction(sig, &sa, NULL) != 0)
        esh_sys_fatal_error("sigaction failed for signal %d", sig);
}
