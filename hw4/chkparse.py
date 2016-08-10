#!/usr/bin/env python3

import os
import subprocess
import itertools

def chk_valgrind(filenr, optnr):
    out = subprocess.check_output("valgrind --leak-check=full -v ./bugs{}.O{} 2>&1 >/dev/null; :".format(filenr, optnr), 
                                  shell = True,
                                  universal_newlines = True)
    out = [o.split(' ', 1)[1] for o in out.split('\n') if o.startswith('==')]
    out = [o.strip() for o in out if not o.startswith(' ')]
    out = [o for o in out if o]
    out = [o for o in out if not "jump" in o]
    return out

os.chdir('bugs')
out = subprocess.check_output('./compileall.sh 2>&1 > /dev/null', 
                              shell = True, 
                              universal_newlines = True)

out = [o for o in out.split('\n') if o.startswith('bugs')]

for filenr in itertools.chain(range(1, 12), [14, 15, 16]):
    for onr in [0, 2]:
        this_out = [o for o in out if o.startswith('bugs{}.c'.format(filenr))]
        print("bugs{}/-O{}".format(filenr, onr))
        print("Detected by compiler: {}".format("yes" if this_out else "no"),
              end = '\n\t' if this_out else '\n')
        if this_out:
            print('\n\t'.join(this_out))
        valgrind_err = chk_valgrind(filenr, onr)
        print("Detected by valgrind: {}".format("yes" if valgrind_err else "no"), end = '\n\t')
        print('\n\t'.join('"{}"'.format(n) for n in valgrind_err) if valgrind_err else 'Reason:')
        print()
        
