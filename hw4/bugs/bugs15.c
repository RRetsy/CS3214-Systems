/*
 * Misinterpreting pointers by reference vs. arrays of pointers.
 * Part 2.
 *
 * Spring 2011.
 */
#include <stdio.h>

void process_raw_cmdline(char **arr) 
{
    arr[0] = "a";
    arr[1] = "b";
}

int
main()
{
    char * cushion = NULL;
    char * cline = "abc";
    process_raw_cmdline(&cline);
    printf("cline = %s\n", cline);
    return 0;
}
