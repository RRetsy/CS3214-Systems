#!/bin/bash
echo "depth = nthreads"
echo "number of threads: (enter 0 to run 2,4,6,8,12,14,16 threads)"
read nthreads
echo "range: 2 will do +10 from depth, 0 will do only given depth)"
read range
 
if [ "$nthreads" -eq 0 ]
then
        for i in 16 14 12 8 6 4 2
        do
                if [ "$range" -eq 2 ]
                then
                        for j in {0..10}
                        do
                                dep=`expr $i + $j`
                                echo "running $i threads with depth $dep"
                                ./quicksort -s 42 -d $dep -n $i 300000000 | egrep -w "threads|qsort" >> out10.txt
                                echo " " >> out.txt
                        done
                else
                        echo "running $i with depth $nthreads"
                        ./quicksort -s 42 -d $nthreads -n $i 300000000 | egrep -w "threads|qsort" >> out10.txt
                        echo " " >> "out$nthreads.txt"
                fi
        done
else
        if [ "$range" -eq 2 ]
        then
                for j in {0..10}
                do
                        dep=`expr $nthreads + $j`
                        echo "running $nthreads threads with depth $dep"
                        ./quicksort -s 42 -d $dep -n $nthreads 300000000 | egrep -w "threads|qsort" >> out.txt
                        echo " " >> "out$nthreads.txt"
                done
        else
                echo "running $nthreads threads with depth $nthreads"
                ./quicksort -s 42 -d $nthreads -n $nthreads 300000000 | egrep -w "threads|qsort" >> out.txt
                echo " " >> "out$nthreads.txt"
        fi
fi
