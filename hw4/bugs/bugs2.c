/* 
 * Returning an address to a local variable
 */
#include <stdio.h>

int *
returnlocal_obvious(void)
{
    int x = 42;
    return &x;  // error: address refers to local var about to be deallocated
}

int
main()
{
    int * p = returnlocal_obvious();
    printf("%d\n", *p);

    return 0;
}
