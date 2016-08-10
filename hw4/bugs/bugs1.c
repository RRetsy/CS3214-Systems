/*
 * Referencing uninitialized data.
 */

#include <stdio.h>

int
main()
{
    int uninitialized;
    printf("%d\n", uninitialized);
    return 0;
}
