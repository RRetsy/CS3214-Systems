#!/usr/bin/python
#
# test reverse string
#

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

# spawn an instance of the shell
c = pexpect.spawn(def_module.shell, drainpty=True, logfile=logfile, args=['-p','plugins/'])
atexit.register(force_shell_termination, shell_process=c)

# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 2

#test with valid numbers
c.sendline("reverse hello")
assert c.expect("olleh") == 0, "Wrong answer!"

#test with invalid numbers

# end the shell program by sending it an end-of-file character
c.sendline("exit");
# ensure that no extra characters are output after exiting
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"

# the test was successful
shellio.success()
