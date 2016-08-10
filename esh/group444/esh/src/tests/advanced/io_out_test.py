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


#assert 1 == 0, "Unimplemented functionality"
#create a testing file with a test string in it
c.sendline("echo firstEcho > testIOOut.txt")

#assert expected prompt
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

time.sleep(1)
#ensure file was written to as desired
ioOutFile = open('testIOOut.txt', 'r')
ioOutLineFirst = ioOutFile.readlines()

assert ioOutLineFirst[0] == "firstEcho\n", "File was not echoed to"

#close file
ioOutFile.close()

#echo again with the > switch. file should be overwritten with new echo
c.sendline("echo secondEcho > testIOOut.txt")

time.sleep(1)

#assert expected prompt
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

#check file contents
ioOutFile = open('testIOOut.txt', 'r')
ioOutLine = ioOutFile.readlines()

assert ioOutLine[0] == "secondEcho\n", "File was not echoed to"

#make sure there is only one line
assert len(ioOutLine) == 1, "File contains more than one line"

#close file
ioOutFile.close()

#clean up
os.remove('testIOOut.txt')

# end the shell program by sending it an end-of-file character
c.sendline("exit");

# ensure that no extra characters are output after exiting
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"

shellio.success()
