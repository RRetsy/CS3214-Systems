/*
 * Returning an address to a local variable through a pointer
 */
#include <stdio.h>

int *
returnlocal_nonobvious(void)
{
    int x = 42;
    int *y = &x;
    return y;       // error: returns address to local variable
}

int
main()
{
    int * p = returnlocal_nonobvious();
    printf("%d\n", *p);
    return 0;
}
