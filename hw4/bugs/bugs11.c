/*
 * Accessing locations outside the allocated area (3)
 */
#include <stdio.h>
#include <stdlib.h>

#define N 10
int a[N];

int
main()
{
    a[N] = 0;       // error: out of range
    a[-1] = 0;      // error: out of range
    return 0;
}
