/*
 * Accessing locations outside the allocated area (1)
 */
#include <stdio.h>
#include <stdlib.h>

int
main()
{
#define N 10

    int * p = malloc(N * sizeof(*p));
    p[N] = 0;       // out of bounds
    p[-1] = 0;      // out of bounds
    return 0;
}
