#!/usr/bin/python
#
# Block header comment
#
#
import sys, imp, atexit, time, os
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
#create testIOin file with "roflTesting" in it
c.sendline("echo roflTesting > testIOIn.txt")

#check the prompt prints
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

#call wc with testIO file as input
c.sendline("wc < testIOIn.txt > results.txt")

#check the prompt prints
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

#ensure output is as expected
time.sleep(1)

ioInFile = open('results.txt', 'r')
ioInLine = ioInFile.readlines()
print ioInLine

assert ioInLine[0] == " 1  1 12\n", "results.txt contained incorrect output"

ioInFile.close()

#cleanup
os.remove('results.txt')
os.remove('testIOIn.txt')

# end the shell program by sending it an end-of-file character
c.sendline("exit");

# ensure that no extra characters are output after exiting
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"

shellio.success()
