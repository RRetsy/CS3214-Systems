/*
 * Misinterpreting pointers by reference vs. arrays of pointers.
 * Part 3.
 *
 * Spring 2011.
 */
#include <stdio.h>
#include <stdlib.h>

void process_raw_cmdline(char **arr) 
{
    arr[0] = "a";
    arr[1] = "b";
}

int
main()
{
    /* Pass a heap-allocated pointer */
    char ** cline = malloc(sizeof (char *));
    * cline = "abc";
    process_raw_cmdline(cline);
    printf("cline = %s\n", *cline);
    return 0;
}
