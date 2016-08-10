/*
 * Accessing uninitialized memory (1)
 */
#include <stdio.h>
#include <stdlib.h>

int
main()
{
    int * p = malloc(sizeof(*p));       // malloc does not initialize memory

    if (p[0] == 0)
        printf("p[0] is 0\n");
    else
        printf("p[0] is %d\n", *p);

    return 0;
}
