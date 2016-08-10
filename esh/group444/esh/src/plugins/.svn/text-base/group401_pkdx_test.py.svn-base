#!/usr/bin/python
#
# pkdx test. Tests the pkdx plugin for the extensible shell.
# This test case assumes that basic esh tests pass, therefore it
# will only test output and interactions with the 'pkdx' plugin.
#

import sys, imp, atexit
sys.path.append("/home/courses/cs3214/software/pexpect-dpty/");
import pexpect, shellio, signal, time, os, re, proc_check

#Ensure the shell process is terminated
def force_shell_termination(shell_process):
    c.close(force=True)

#pulling in the regular expression and other definitions
definitions_scriptname = sys.argv[1]
plugin_directory = sys.argv[2]

def_module = imp.load_source('', definitions_scriptname)
logfile = None
if hasattr(def_module, 'logfile'):
    logfile = def_module.logfile

#spawn an instance of the shell
c = pexpect.spawn(def_module.shell + plugin_directory, \
    drainpty=True, logfile=sys.stdout)
atexit.register(force_shell_termination, shell_process=c)
#c.logfile = sys.stdout

# set timeout for all following 'expect*' calls to 2 seconds
c.timeout = 2

# The esh loaded plugins, which means that the prompt may have changed.
# Therefore expecting the exact prompt is not valid.
# Assume that basic functionality such as prompt generation works, test pkdx.

# Send pkdx -h command (help)
c.sendline("pkdx -h")

assert c.expect("The esh pokedex!") == 0, \
    "pkdx did not properly print help (-h)"
assert c.expect("Usage:") == 0, \
    "pkdx did not properly print help (-h)"



# Send pkdx -l command (listing)
c.sendline("pkdx -l")

assert c.expect("001 - Bulbasaur") == 0, \
    "pkdx did not properly print pokedex list (-l)"
assert c.expect("002 - Ivysaur") == 0, \
    "pkdx did not properly print pokedex list (-l)"
assert c.expect("003 - Venusaur") == 0, \
    "pkdx did not properly print pokedex list (-l)"

# ...

assert c.expect("150 - Mewtwo") == 0, \
    "pkdx did not properly print pokedex list (-l)"
assert c.expect("151 - Mew") == 0, \
    "pkdx did not properly print pokedex list (-l)"


# Send pkdx -c command (credits)
c.sendline("pkdx -c")

assert c.expect("Plugin made by Artur Aguiar for the esh shell.") == 0, \
    "pkdx did not properly print credits (-c)"


# Main functionality:

# Send poke id query
c.sendline("pkdx 30")

# ASCII art starts with id and name line.
assert c.expect("030 - Nidorina") == 0, \
    "pkdx did not properly print poke id query for Nidorina"

# Description
assert c.expect("POKEMON:") == 0, \
    "pkdx did not properly print poke id query for Nidorina"
assert c.expect("When it senses danger, it raises all the barbs on its body.") == 0, \
    "pkdx did not properly print poke id query for Nidorina"

# Send poke id query
c.sendline("pkdx 77")

# ASCII art starts with id and name line.
assert c.expect("077 - Ponyta") == 0, \
    "pkdx did not properly print poke id query for Ponyta"

# Description
assert c.expect("Fire Horse Pok") == 0, \
    "pkdx did not properly print poke id query for Ponyta"
assert c.expect("As a newborn, it can barely stand. However, " + \
    "through galloping, its legs are made tougher and faster.") == 0, \
    "pkdx did not properly print poke id query for Ponyta"

shellio.success()