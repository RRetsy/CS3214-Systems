/* 
 * Accessing locations outside the allocated area. (2)
 */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

int
main()
{
    char * a = calloc(16, 1);
    char * b = calloc(16, 1);

    a[80] = '1';        // out of bounds 
    b[-80] = '2';       // out of bounds

    printf("a=%c b=%c\n", *a, *b);
    return 0;
}
