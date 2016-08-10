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

#Implementing tests here

# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 2

#open vim
c.sendline("vim firstTestEx.txt")

# send SIGTSTP to vim
c.sendcontrol('z')

#shell should pick up that vim was stopped and respond with job status
(jobid, statusmsg, cmdline) = \
        shellio.parse_regular_expression(c, def_module.job_status_regex)
assert statusmsg == def_module.jobs_status_msg['stopped'], "Shell did not report stopped job"

#move job into foreground
c.sendline(def_module.builtin_commands['fg'] % jobid)

#when moving a job in the foreground, bash outputs its command line
assert c.expect_exact(cmdline) == 0, "Shell did not report the job moved into the foreground"

#end vim
c.sendintr()
c.sendline("ZZ")

time.sleep(2)

#ensure expected prompt
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

#reopen vim and create file 'testingVim.txt'
c.sendline("vim testingVim.txt")
time.sleep(1)
#send vim the insert signal
c.sendline("i")
#add a line of text
c.sendline("Here is a line of text for vim to write")
#close vim
c.sendintr()
c.sendline(":wq")
time.sleep(2)

#ensure expected prompt
assert c.expect(def_module.prompt) == 0, "Shell did not print expected prompt"

#ensure file was written to as desired
ioFile = open('testingVim.txt', 'r')
ioLine = ioFile.readlines()

assert ioLine[1] == "Here is a line of text for vim to write\n", "Line did not match"

#close
ioFile.close()

#cleanup
os.remove('testingVim.txt')

# end the shell program by sending it an end-of-file character
c.sendline("exit");

# ensure that no extra characters are output after exiting
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"


#the test was successful
shellio.success()
