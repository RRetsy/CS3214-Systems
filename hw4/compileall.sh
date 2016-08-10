for FILE in bugs*.c
do
    echo gcc -O2 -g -o `basename $FILE .c`.O2 -Wall $FILE
    gcc -O2 -g -o `basename $FILE .c`.O2 -Wall $FILE
    echo gcc -g -o `basename $FILE .c`.O0 -Wall $FILE
    gcc -g -o `basename $FILE .c`.O0 -Wall $FILE
done
