/*
 * Out of bounds access on a stack-allocated array
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

static void 
copy_and_print(char *p)
{
    char buf[6];        // one byte too short for "CS3214"
                        // 'C' 'S' '3' '2' '1' '4' '\0'

    strcpy(buf, p);
    printf("%s\n", buf);
}

int
main()
{
    copy_and_print("CS3214");
    return 0;
}
