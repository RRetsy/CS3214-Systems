/* 
 * Simple, 32-bit and 64-bit clean allocator based on implicit free
 * lists, first fit placement, and boundary tag coalescing, as described
 * in the CS:APP2e text. Blocks must be aligned to doubleword (8 byte) 
 * boundaries. Minimum block size is 16 bytes. 
 *
 * This version is loosely based on 
 * http://csapp.cs.cmu.edu/public/ics2/code/vm/malloc/mm.c
 * but unlike the book's version, it does not use C preprocessor 
 * macros or explicit bit operations.
 */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <assert.h>

#include "mm.h"
#include "memlib.h"

struct boundary_tag {
    int inuse:1;        // inuse bit
    int size:31;        // size of block, in words
};

/* FENCE is used for heap prologue/epilogue. */
const struct boundary_tag FENCE = { .inuse = 1, .size = 0 };

/* A C struct describing the beginning of each block. 
 * For implicit lists, used and free blocks have the same 
 * structure, so one struct will suffice for this example.
 * If each block is aligned at 4 mod 8, each payload will
 * be aligned at 0 mod 8.
 */
struct block {
    struct boundary_tag header; /* offset 0, at address 4 mod 8 */
    char payload[0];            /* offset 4, at address 0 mod 8 */
};

/*
 * If NEXT_FIT defined use next fit search, else use first fit search 
 */
#define NEXT_FITx

/* Basic constants and macros */
#define WSIZE       4       /* Word and header/footer size (bytes) */
#define DSIZE       8       /* Doubleword size (bytes) */
#define MIN_BLOCK_SIZE_WORDS 4 /* Minimum block size in words */
#define CHUNKSIZE  (1<<10)  /* Extend heap by this amount (words) */

#define MAX(x, y) ((x) > (y)? (x) : (y))  

/* Global variables */
static struct block *heap_listp = 0;  /* Pointer to first block */  
#ifdef NEXT_FIT
static struct block *rover;           /* Next fit rover */
#endif

/* Function prototypes for internal helper routines */
static struct block *extend_heap(size_t words);
static void place(struct block *bp, size_t asize);
static struct block *find_fit(size_t asize);
static struct block *coalesce(struct block *bp);

/* Given a block, obtain previous's block footer.
   Works for left-most block also. */
static struct boundary_tag * prev_blk_footer(struct block *blk) {
    return &blk->header - 1;
}

/* Return if block is free */
static bool blk_free(struct block *blk) { 
    return !blk->header.inuse; 
}

/* Return size of block is free */
static size_t blk_size(struct block *blk) { 
    return blk->header.size; 
}

/* Given a block, obtain pointer to previous block.
   Not meaningful for left-most block. */
static struct block *prev_blk(struct block *blk) {
    struct boundary_tag *prevfooter = prev_blk_footer(blk);
    assert(prevfooter->size != 0);
    return (struct block *)((size_t *)blk - prevfooter->size);
}

/* Given a block, obtain pointer to next block.
   Not meaningful for right-most block. */
static struct block *next_blk(struct block *blk) {
    assert(blk_size(blk) != 0);
    return (struct block *)((size_t *)blk + blk->header.size);
}

/* Given a block, obtain its footer boundary tag */
static struct boundary_tag * get_footer(struct block *blk) {
    return (void *)((size_t *)blk + blk->header.size) 
                   - sizeof(struct boundary_tag);
}

/* Set a block's size and inuse bit in header and footer */
static void set_header_and_footer(struct block *blk, int size, int inuse) {
    blk->header.inuse = inuse;
    blk->header.size = size;
    * get_footer(blk) = blk->header;    /* Copy header to footer */
}

/* Mark a block as used and set its size. */
static void mark_block_used(struct block *blk, int size) {
    set_header_and_footer(blk, size, 1);
}

/* Mark a block as free and set its size. */
static void mark_block_free(struct block *blk, int size) {
    set_header_and_footer(blk, size, 0);
}

/* 
 * mm_init - Initialize the memory manager 
 */
int mm_init(void) 
{
    /* Create the initial empty heap */
    struct boundary_tag * initial = mem_sbrk(2 * sizeof(struct boundary_tag));
    if (initial == (void *)-1)
        return -1;

    /* We use a slightly different strategy than suggested in the book.
     * Rather than placing a min-sized prologue block at the beginning
     * of the heap, we simply place two fences.
     * The consequence is that coalesce() must call prev_blk_footer()
     * and not prev_blk() - prev_blk() cannot be called on the left-most
     * block.
     */
    initial[0] = FENCE;                     /* Prologue footer */
    heap_listp = (struct block *)&initial[1];
    initial[1] = FENCE;                     /* Epilogue header */

#ifdef NEXT_FIT
    rover = heap_listp;
#endif

    /* Extend the empty heap with a free block of CHUNKSIZE bytes */
    if (extend_heap(CHUNKSIZE) == NULL) 
        return -1;
    return 0;
}

/* 
 * mm_malloc - Allocate a block with at least size bytes of payload 
 */
void *mm_malloc(size_t size)
{
    size_t awords;      /* Adjusted block size in words */
    size_t extendwords;  /* Amount to extend heap if no fit */
    struct block *bp;      

    if (heap_listp == 0){
        mm_init();
    }
    /* Ignore spurious requests */
    if (size == 0)
        return NULL;

    /* Adjust block size to include overhead and alignment reqs. */
    size += 2 * sizeof(struct boundary_tag);    /* account for tags */
    size = (size + DSIZE - 1) & ~(DSIZE - 1);   /* align to double word */
    awords = MAX(MIN_BLOCK_SIZE_WORDS, size/WSIZE);
                                                /* respect minimum size */

    /* Search the free list for a fit */
    if ((bp = find_fit(awords)) != NULL) {
        place(bp, awords);
        return bp->payload;
    }

    /* No fit found. Get more memory and place the block */
    extendwords = MAX(awords,CHUNKSIZE);
    if ((bp = extend_heap(extendwords)) == NULL)  
        return NULL;
    place(bp, awords);
    return bp->payload;
} 

/* 
 * mm_free - Free a block 
 */
void mm_free(void *bp)
{
    if (bp == 0) 
        return;

    /* Find block from user pointer */
    struct block *blk = bp - offsetof(struct block, payload);
    if (heap_listp == 0) {
        mm_init();
    }

    mark_block_free(blk, blk_size(blk));
    coalesce(blk);
}

/*
 * coalesce - Boundary tag coalescing. Return ptr to coalesced block
 */
static struct block *coalesce(struct block *bp) 
{
    bool prev_alloc = prev_blk_footer(bp)->inuse;
    bool next_alloc = ! blk_free(next_blk(bp));
    size_t size = blk_size(bp);

    if (prev_alloc && next_alloc) {            /* Case 1 */
        return bp;
    }

    else if (prev_alloc && !next_alloc) {      /* Case 2 */
        mark_block_free(bp, size + blk_size(next_blk(bp)));
    }

    else if (!prev_alloc && next_alloc) {      /* Case 3 */
        bp = prev_blk(bp);
        mark_block_free(bp, size + blk_size(bp));
    }

    else {                                     /* Case 4 */
        mark_block_free(prev_blk(bp), 
                        size + blk_size(next_blk(bp)) + blk_size(prev_blk(bp)));
        bp = prev_blk(bp);
    }
#ifdef NEXT_FIT
    /* Make sure the rover isn't pointing into the free block */
    /* that we just coalesced */
    if ((rover > bp) && (rover < next_blk(bp))) 
        rover = bp;
#endif
    return bp;
}

/*
 * mm_realloc - Naive implementation of realloc
 */
void *mm_realloc(void *ptr, size_t size)
{
    size_t oldsize;
    void *newptr;

    /* If size == 0 then this is just free, and we return NULL. */
    if(size == 0) {
        mm_free(ptr);
        return 0;
    }

    /* If oldptr is NULL, then this is just malloc. */
    if(ptr == NULL) {
        return mm_malloc(size);
    }

    newptr = mm_malloc(size);

    /* If realloc() fails the original block is left untouched  */
    if(!newptr) {
        return 0;
    }

    /* Copy the old data. */
    struct block *oldblock = ptr - offsetof(struct block, payload);
    oldsize = blk_size(oldblock) * WSIZE;
    if(size < oldsize) oldsize = size;
    memcpy(newptr, ptr, oldsize);

    /* Free the old block. */
    mm_free(ptr);

    return newptr;
}

/* 
 * checkheap - We don't check anything right now. 
 */
void mm_checkheap(int verbose)  
{ 
}

/* 
 * The remaining routines are internal helper routines 
 */

/* 
 * extend_heap - Extend heap with free block and return its block pointer
 */
static struct block *extend_heap(size_t words) 
{
    void *bp;

    /* Allocate an even number of words to maintain alignment */
    words = (words + 1) & ~1;
    if ((long)(bp = mem_sbrk(words * WSIZE)) == -1)  
        return NULL;

    /* Initialize free block header/footer and the epilogue header.
     * Note that we scoop up the previous epilogue here. */
    struct block * blk = bp - sizeof(FENCE);
    mark_block_free(blk, words);
    next_blk(blk)->header = FENCE;

    /* Coalesce if the previous block was free */
    return coalesce(blk);
}

/* 
 * place - Place block of asize words at start of free block bp 
 *         and split if remainder would be at least minimum block size
 */
static void place(struct block *bp, size_t asize)
{
    size_t csize = blk_size(bp);

    if ((csize - asize) >= MIN_BLOCK_SIZE_WORDS) { 
        mark_block_used(bp, asize);
        bp = next_blk(bp);
        mark_block_free(bp, csize-asize);
    }
    else { 
        mark_block_used(bp, csize);
    }
}

/* 
 * find_fit - Find a fit for a block with asize words 
 */
static struct block *find_fit(size_t asize)
{

#ifdef NEXT_FIT 
    /* Next fit search */
    struct block *oldrover = rover;

    /* Search from the rover to the end of list */
    for ( ; blk_size(rover) > 0; rover = next_blk(rover))
        if (blk_free(rover) && (asize <= blk_size(rover)))
            return rover;

    /* search from start of list to old rover */
    for (rover = heap_listp; rover < oldrover; rover = next_blk(rover))
        if (blk_free(rover) && (asize <= blk_size(rover)))
            return rover;

    return NULL;  /* no fit found */
#else 
    /* First fit search */
    struct block *bp;

    for (bp = heap_listp; blk_size(bp) > 0; bp = next_blk(bp)) {
        if (blk_free(bp) && asize <= blk_size(bp)) {
            return bp;
        }
    }
    return NULL; /* No fit */
#endif
}

team_t team = {
    /* Team name */
    "Sample allocator using implicit lists",
    /* First member's full name */
    "Godmar Back",
    "gback@cs.vt.edu",
    /* Second member's full name (leave blank if none) */
    "",
    "",
};
