#!/usr/bin/python
#
# Block header comment
#
#
import sys, imp, atexit, os, time
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

#spawn an instance of the shell
c = pexpect.spawn(def_module.shell, drainpty=True, logfile=logfile)
atexit.register(force_shell_termination, shell_process=c)

c.timeout = 2

#assert 1 == 0, "Unimplemented functionality"
c.sendline("ls | sort > testPipe.txt")
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

c.sendline("ls | grep esh.c ")
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"
assert c.expect("esh.c") == 0, "Shell did not print expected output"

shellio.success()
