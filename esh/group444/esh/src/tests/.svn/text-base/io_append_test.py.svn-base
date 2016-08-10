#!/usr/bin/python
#
# Block header comment
#
#
import sys, imp, atexit, time
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


#assert 1 == 0, "Unimplemented functionality"
# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 2

#write to a file
c.sendline('echo hi > testAppend.txt')

#assert expected prompt
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

time.sleep(1)
#ensure file was written to as desired
appendfile = open('testAppend.txt', 'r')
appendLine = appendfile.readlines()

assert appendLine[0] == "hi\n", "File was not echoed to"

#close file
appendfile.close()

#send an append command
c.sendline("echo appended >> testAppend.txt")

time.sleep(1)
#validate append
appendfile = open('testAppend.txt', 'r')
appendLine = appendfile.readlines()

assert appendLine[0] == "hi\n", "Previous echo was absent"
assert appendLine[1] == "appended\n", "Second echo was absent"

#close file
appendfile.close()

#cleanup
os.remove('testAppend.txt')

# end the shell program by sending it an end-of-file character
c.sendline("exit");

# ensure that no extra characters are output after exiting
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"

shellio.success()
