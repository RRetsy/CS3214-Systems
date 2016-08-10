/*
 * Accessing an uninitialized location on the stack
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

static void 
stack_underrun(void)
{
    int buf[6], *p = buf;

    printf("%d\n", *--p);
}

int
main()
{
    stack_underrun();
    return 0;
}
