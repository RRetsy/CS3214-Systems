/*
 * esh - the 'extensible' shell.
 * 
 * Utility functions for system calls.
 *
 * Developed by Godmar Back for CS 3214 Fall 2009
 * Virginia Tech.
 */

#include <stdbool.h>
#include <signal.h>

/* Print message to stderr, followed by information about current error. 
 * Use like 'printf' */
void esh_sys_error(char *fmt, ...);
void esh_sys_fatal_error(char *fmt, ...);

/* Get a file descriptor that refers to controlling terminal */
int esh_sys_tty_getfd(void);

/* Initialize tty support.  
 * Return pointer to static structure that saves initial state.
 * Restore this state via esh_sys_tty_restore() whenever the shell
 * takes back control of the terminal.
 */
struct termios * esh_sys_tty_init(void);

/* Save current terminal settings.
 * This function is used when a job is suspended.*/
void esh_sys_tty_save(struct termios *saved_tty_state);

/* Restore terminal to saved settings.
 * This function is used when resuming a suspended job. */
void esh_sys_tty_restore(struct termios *saved_tty_state);

/* Return true if this signal is blocked */
bool esh_signal_is_blocked(int sig);

/* Block a signal. Returns true it was blocked before */
bool esh_signal_block(int sig);

/* Unblock a signal. Returns true it was blocked before */
bool esh_signal_unblock(int sig);

/* Signal handler prototype */
typedef void (*sa_sigaction_t)(int, siginfo_t *, void *);

/* Install signal handler for signal 'sig' */
void esh_signal_sethandler(int sig, sa_sigaction_t handler);
