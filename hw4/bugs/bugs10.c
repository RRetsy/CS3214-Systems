/*
 * Accessing uninitialized bits.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

struct boundary_tag {
    unsigned a:1;
    unsigned size:31;
};

static void 
bitwise(struct boundary_tag *t)
{
    printf("tag.size = %d\n", t->size);
    printf("tag.a = %d\n", t->a);
}

int
main()
{
    struct boundary_tag t = { .size = 3 };  // leaves .a uninitialized
    bitwise(&t);

    struct boundary_tag *tp = malloc(sizeof *tp);
    tp->a = 0;  // leaves .size uninitialized
    bitwise(tp);
    return 0;
}
