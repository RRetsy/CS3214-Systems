#!/usr/bin/python
#
# group436_oddeven_test.py: tests the oddeven plugin
# 
# Test the oddeven plugin for valid and invalid inputs
#
# Marcus Tedesco & Michael Chang
#
# NOTE: This test may need to be located in the src directory to run

import sys, imp, atexit
sys.path.append("/home/courses/cs3214/software/pexpect-dpty/");
import pexpect, shellio, signal, time, os, re, proc_check

#Ensure the shell process is terminated
def force_shell_termination(shell_process):
	c.close(force=True)

#pulling in the regular expression and other definitions
definitions_scriptname = sys.argv[1]
def_module = imp.load_source('', definitions_scriptname)
logfile = None
if hasattr(def_module, 'logfile'):
    logfile = def_module.logfile

# spawn an instance of bash.  PS1 is the env variable from which bash
# draws its prompt
c = pexpect.spawn(def_module.shell, drainpty=True, logfile=logfile)
atexit.register(force_shell_termination, shell_process=c)

# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 2

#test oddeven with valid input
c.sendline("oddeven 1234")
assert c.expect("1234 is even") == 0, "oddeven did not print even"
c.sendline("oddeven 3333")
assert c.expect("3333 is odd") == 0, "oddeven did not print odd"
c.sendline("oddeven -3333")
assert c.expect("-3333 is odd") == 0, "oddeven did not print odd for a negative number"
c.sendline("oddeven 0")
assert c.expect("0 is neither odd nor even") == 0, "oddeven did not print 0 is neither odd nor even"

#test oddeven with invalid input
c.sendline("oddeven helloworld") #string
assert c.expect("Input argument must enter an integer") == 0, "oddeven did not print correct error message (string)"
c.sendline("oddeven 9999-111") #'-' not in correct spot
assert c.expect("Input argument must enter an integer") == 0, "oddeven did not print correct error message ('-' not correct placement)"
c.sendline("oddeven 55.77") #decimal number
assert c.expect("Input argument must enter an integer") == 0, "oddeven did not print correct error message (decimal number)"
c.sendline("oddeven") #no 2nd argument
assert c.expect("Must enter an integer as second argument") == 0, "oddeven did not print correct error message (empty argument)"

# exit
c.sendline("exit");
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"

shellio.success()
