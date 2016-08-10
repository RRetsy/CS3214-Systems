/*
 * Failing to initialize the trailing '\0' in a copy of a string
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

static void 
copy_and_print(char *p)
{
    char buf[6];

    strncpy(buf, p, 5);     // fails to append terminating '\0'
    printf("%s\n", buf);
}

int
main()
{
    copy_and_print("CS3214");
    return 0;
}
