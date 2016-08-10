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
plugin_dir = sys.argv[2]
#spawn an instance of the shell
c = pexpect.spawn(def_module.shell + plugin_dir, drainpty=True, logfile=logfile)
c.logfile = sys.stdout
atexit.register(force_shell_termination, shell_process=c)


# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 5

workdir = os.getcwd()
print workdir
assert c.expect(workdir) == 0, "Incorrect Prompt"

c.sendline("exit")

shellio.success()
