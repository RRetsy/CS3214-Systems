#!/usr/bin/python
#
# Check that build and output of threadpool exercise match expected output
#
# Written for CS3214 Fall 2009 by G. Back (godmar@gmail.com)
# Last Updated Fall 2011
#

import re, os, sys, subprocess, operator, signal

exe = "./threadpool_test"
helgrind = ["/home/courses/cs3214/valgrind-3.7.0-install/bin/valgrind", "--tool=helgrind"]

print "Checking correctness of threadpool exercise."
print "Compiling..."

if os.system("make clean " + exe):
    raise Exception("make failed, run 'make clean " + exe + "' to see why")

if not os.access(exe, os.X_OK):
    raise Exception(exe + " did not build")

print "Ok."

hex = "[0-9A-Fa-f]{8,16}"
print "Checking that threadpool.o does not use global variables...",
allowedsymbols = [ "future_free", "future_get", \
                   "thread_pool_new", "thread_pool_shutdown", 
                   "thread_pool_submit" ] 

symbols = subprocess.Popen(["nm", "threadpool.o"], stdout=subprocess.PIPE)\
    .communicate()[0].split("\n")

for sym in symbols:
    if sym == "" or re.match("\s+U (\S+)", sym):
        continue

    m = re.match(hex + " (\S) (\S+)", sym)
    if not m:
        raise Exception("unexpected line in nm:\n" + sym)

    if m.group(1) == "t":
        continue
    # ignore symbols produced by 'assert()'
    if m.group(1) == "r" and m.group(2).startswith("__PRETTY_FUNCTION__"):
        continue
    if m.group(1) == "T":
        if m.group(2) in allowedsymbols:
            continue

        raise Exception("threadpool.o defines global function '" + m.group(2) 
                        + "'\nallowed functions are: " + str(allowedsymbols));

    raise Exception("threadpool.o must not define any global or "
                    + "static variables, but you define: " + m.group(2))

print "Ok."

# test scenarios (#threads, #tasks)
threads2tasks = [ (4, 4), (4, 2), (4, 1), \
                  (4, 8), (4, 12), \
                  (2, 2), (2, 4), (2, 8), \
                  (1, 2), (1, 4), (1, 8), \
                  (3, 1), (3, 2), (3, 3), (3, 4), (3, 5) \
                ]

for (threads, tasks) in threads2tasks:
    print "Testing", threads, "threads and", tasks, "tasks"
    sp = subprocess.Popen([exe, str(threads), str(tasks)],
                           stdout=subprocess.PIPE)
    output = sp.communicate()[0]

    if sp.returncode < 0:
        signames = dict((k, v) for v, k in signal.__dict__.iteritems() if v.startswith('SIG'))
        signum = -sp.returncode
        raise Exception("Program terminated with signal %d (%s)\n" % (signum, signames[signum]))

    match = re.match("Main thread: 0x("+hex+")\n(.*)\n"\
        "Done\.", output, re.MULTILINE | re.DOTALL)
    if not match:
        raise Exception("Output did not match expected format:\n" + output)

    mainthread = match.group(1)
    futures = []
    for future in match.group(2).split("\n"):
        m = re.match("Future #(\d+) Thread #0x("+hex+") "\
                 "start=(\d+)\.(\d+) end=(\d+)\.(\d+)", future)
        if not m:
            raise Exception("Future did not match expected format:\n" + future)

        futures.append((m.group(1), m.group(2), 
            float(m.group(3)) + float(m.group(4)) / 1e6,
            float(m.group(5)) + float(m.group(6)) / 1e6))

    # print futures

    # consistency checks
    # Futures should be printed in increasing order
    print "Checking correct order of futures...",

    # all is available only in Python 2.5 and up
    def all(l):
        return reduce(lambda x, y: x and y, l, True)
    if not all([ int(futures[i][0]) < int(futures[i+1][0]) \
                for i in range(len(futures)-1) ]):
        raise Exception("Futures are out of order:\n" + output)

    print "Ok."

    print "Checking that correct number of threads were used...",
    threadsused = len(set([ f[1] for f in futures ] + [ mainthread ]))
    threadsexpected = min(threads, tasks) + 1
    if threadsexpected != threadsused:
        raise Exception("Thread pool used " + str(threadsused) 
            + " distinct threads including the main thread, should have used " 
            + str(threadsexpected) + ":\n" + output)

    print "Ok."

    # each pair within each 'threads'-sized chunk of tasks 
    # should have executed in parallel.
    print "Checking tasks were executed in parallel...",
    def inparallel((start0, end0), (start1, end1)):
        return start1 < end0 and start0 < end1

    def allpairs(l):
        return reduce(operator.add, [ [(l[i], e) for e in l[i+1:]] \
                                      for i in range(len(l)-1) ], [])

    for i in range(0, len(futures), threads):
        for (f1, f2) in allpairs(futures[i:i+threads]):
            if not inparallel(f1[2:4], f2[2:4]):
                raise Exception("Future #" + f1[0] + " and #" + f2[0] 
                              + " did not run in parallel:\n" + output)

    print "Ok."

print "You have met minimum requirements.\n"

print "Now checking for race conditions using helgrind"
for (threads, tasks) in threads2tasks:
    cmd = helgrind + [exe, str(threads), str(tasks)]
    print "Running", " ".join(cmd), "...",
    sp = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stderr = sp.communicate()[1]
    if not re.search("ERROR SUMMARY: 0 errors from 0 contexts", stderr):
        raise Exception("Your program contains race conditions:\n" + stderr)

    print "Ok."

print "Congratulations, you've passed the race condition checker tests.\n"

print "Testing whether your thread pool implementation runs Quicksort"
quicksort = "quicksort"
if os.system("make clean " + quicksort):
    print "make failed, run 'make clean " + quicksort + "' to see why"
    sys.exit(1)

sp = subprocess.Popen("./quicksort -q -s 1 1000000".split(" "), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
stdout = sp.communicate()[0]
if not re.search("Processed 14 segments of sizes: 6316 19753 62328 62520 68838 82083 104576 141938 152189 256767 325607 450365 592305 674390", stdout):
    print "Quicksort did not produce the expected result."
else:
    print "Quicksort appears to work, you can now start measuring parallel speedup."

