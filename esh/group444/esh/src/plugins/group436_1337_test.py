#!/usr/bin/python
#
# group436_1337_test.py: tests the 1337 plugin
# 
# Takes a sentence as the input and prints the sentence in leet speak
#
# Marcus Tedesco & Michael Chang
#
###################################################################################
# NOTE: This test may need to be located in the src directory to run depending on #
#       the way you call the test driver                                          #
#                                                                                 #
#		I ran with '~cs3214/bin/stdriver.py -p plugins plugins.tst' from my src   #
#		directory with the plugins.tst also in the src directory				  #
###################################################################################

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

#test 1337 with invalid input
c.sendline("1337") #needs additional arguments
assert c.expect("/v\|_|$7 !/\/|D|_|7 @o|o|!7!0/\/@1 @|26|_|/v\3/\/7$ (must input additional arguments)") == 0, "1337 did not print correct error message"

#test 1337 with valid input
c.sendline("1337 a")	#1 letter
assert c.expect("@ ") == 0, "1337 did not print correctly"
c.sendline("1337 a b")	#2 letters and a space
assert c.expect("@ |3 ") == 0, "1337 did not print correctly"
c.sendline("1337 testing")	#one word
assert c.expect("73\$7\!\/\\\/6 ") == 0, "1337 did not print correctly"
c.sendline("1337 hello world!")	#two words and punctuation
assert c.expect("\]-\[3110 \\\/\\\/0|21o|\! ") == 0, "1337 did not print correctly"

# exit
c.sendline("exit");
assert c.expect_exact("exit\r\n") == 0, "Shell output extraneous characters"

shellio.success()
