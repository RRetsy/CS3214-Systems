#!/usr/bin/python
#
# Block header comment
#
#

import sys, imp, atexit
sys.path.append("/home/courses/cs3214/software/pexpect-dpty/");
import pexpect, shellio, signal, time, os, re, proc_check

#Ensure the shell process is terminated
def force_shell_termination(shell_process):
        c.close(force=True)

#pulling in the regular expression and other definitions
definitions_scriptname = sys.argv[1]
plugin_dir = sys.argv[2]
def_module = imp.load_source('', definitions_scriptname)
logfile = None
if hasattr(def_module, 'logfile'):
    logfile = def_module.logfile

# spawn an instance of the shell
c = pexpect.spawn(def_module.shell + plugin_dir, drainpty=True, logfile=logfile)

atexit.register(force_shell_termination, shell_process=c)

# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 2

# run a command
c.sendline("stringToBinary test")

assert c.expect_exact("t: 01110100") == 0, "Shell did not print expected statement (1)"

c.sendline("stringToBinary")

assert c.expect_exact("Please Enter A String To Convert After 'stringToBinary'") == 0, "Shell did not print expected statement (1)"

shellio.success()
