/*
 * mm.c This is an efficient implementation of a dynamic memory allocator using segregated
 * free lists. It provides support for several methods/functions for allocation/release
 * of memory.
 */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <stddef.h>

#include "mm.h"
#include "memlib.h"
#include "config.h"            

/*********************************************************
 * NOTE TO STUDENTS: Before you do anything else, please
 * provide your team information in the following struct.
 ********************************************************/
team_t team = {
    /* Team name */
    "such malloc",
    /* First member's full name */
    "Evan Lobeto",
    /* First member's SLO (@cs.vt.edu) email address */
    "elobeto@vt.edu",
    /* Second member's full name (leave blank if none) */
    "Kevin Ellis",
    /* Second member's SLO (@cs.vt.edu) email address (leave blank if none) */
    "kevellis@vt.edu"
};

//Macros
//size of a word is 4 bytes
#define WSIZE     4       
//size of a double is 8 bytes
#define DSIZE     8       
//size of a page (1<<12 == 4096) bytes
#define CHUNKSIZE (1<<12) 
//number of lists 
#define NUMLISTS     20      
//The buffer size (128) for reallocation
#define BUFFER  (1<<7)    
//Takes two numbers (x and y) and returns the max/min of them
#define MAX(x, y) ((x) > (y) ? (x) : (y)) 
#define MIN(x, y) ((x) < (y) ? (x) : (y)) 

//puts the size and allocation tag into a word so that it can be stored in a block
#define PACK(size, alloc) ((size) | (alloc))

//replace "GET(p)" with whatever was referenced at that location
#define GET(p)            (*(unsigned int *)(p))
// Preserve reallocation bit
#define PUT_TAG(p, val)       (*(unsigned int *)(p) = (val) | GET_TAG(p))
// Clear reallocation bit
#define PUT(p, val) (*(unsigned int *)(p) = (val))

//sets the pointer (p) to whatever ptr represented
#define SET_PTR(p, ptr) (*(unsigned int *)(p) = (unsigned int)(ptr))

//Gets the pointer to the address of a block's header segment and footer segment
#define HDRP(ptr) ((char *)(ptr) - WSIZE)
#define FTRP(ptr) ((char *)(ptr) + GET_SIZE(HDRP(ptr)) - DSIZE)
//Adjusts the allocation tag of a block
#define SET_TAG(p)   (*(unsigned int *)(p) = GET(p) | 0x2)
#define UNSET_TAG(p) (*(unsigned int *)(p) = GET(p) & ~0x2)

// These three functions retrieve the size of the block, if it can be allocated, 
//or if it already is allocated.
#define GET_SIZE(p)  (GET(p) & ~0x7)
#define GET_ALLOC(p) (GET(p) & 0x1)
#define GET_TAG(p)   (GET(p) & 0x2)

//Point to the next and previous blocks (in reference from ptr)
#define NEXT_BLKP(ptr) ((char *)(ptr) + GET_SIZE((char *)(ptr) - WSIZE))
#define PREV_BLKP(ptr) ((char *)(ptr) - GET_SIZE((char *)(ptr) - DSIZE))

/* Address of free block's predecessor and successor entries */
#define PRED_PTR(ptr) ((char *)(ptr))
#define SUCC_PTR(ptr) ((char *)(ptr) + WSIZE)
//This represents the offset of a line for pointers        
#define OFFSET   4 
//These two list pointers represent the block's predecessor and successor on the list
#define PRED_LIST(ptr) (*(char **)(ptr))
#define SUCC_LIST(ptr) (*(char **)(SUCC_PTR(ptr)))


/*
 * Global variables
 */
void *free_lists[NUMLISTS]; //Array of pointers to segregated free lists
char *pblock;    //Pointer to prologue block

//Variables for checking function 
int line_count; // Running count of operations performed


/*
 * Function prototypes
 */
static void *extend_heap(size_t size);
static void *coalesce(void *ptr);
static void place(void *ptr, size_t adj_size);
static void insert(void *ptr, size_t size);
static void delete(void *ptr);
static void insert_helper(int size, void* ptr, void* insert_ptr, void* search_ptr);

//static void mm_check(void *ptr, int size);

/* 
 * Starts up the memory allocator. Creates the heap and the 'prologue block' for it
 */
int mm_init(void)
{

  //count the lists
  int list_count = NUMLISTS;

  //Start up the free lists and set them all to null initially
  for (; list_count >= 0; list_count--) {
    free_lists[list_count] = NULL;
  }
  //the start of the heap
  char *heap_start; 
  //Get some allocated memory for the empty heap
  heap_start = mem_sbrk(4 * WSIZE);  
  //aligns the beginning of the heap
  PUT(heap_start, 0); 
  //throws in the prologue's header into the heap
  PUT(heap_start + (1 * WSIZE), PACK(DSIZE, 1)); 
  //throws in the prologue's footer into the heap
  PUT(heap_start + (2 * WSIZE), PACK(DSIZE, 1)); 
  //throws in the final bit
  PUT(heap_start + (3 * WSIZE), PACK(0, 1)); 
  //create the prologue block, by pointing it at the place we packed in the prologue earlier    
  pblock = DSIZE + heap_start;

  extend_heap(CHUNKSIZE);
    
  
  // Variables for checking function
  line_count = OFFSET;
    
  return 0;
}

/* 
 * Allocates a new block of memory of size size. Returns a pointer to the location
 * of the beginning of the memory. It aligns blocks of memory so that there are always certain
 *  amounts of blocks, with padding. 
 */
void *mm_malloc(size_t size)
{
  //Adjusted block size
  size_t adj_size;
  // The size we need to enlarge the heap by if there is no current fit      
  size_t ex_size;
  void *ptr = NULL;
  
  
  //return null if the size isn't above zero
  if (size == 0)
  {
    return NULL;
  }
  //Make the block size appropriate to contain tags and the size of the block
  if (size <= DSIZE)
    {
      adj_size = DSIZE * 2;
    }
   else //align the block while also creating enough room for header and footer
    {
      adj_size = DSIZE * ( (size + (DSIZE) + (DSIZE - 1) ) / DSIZE);
    }
  
    //Reset the size variable to be a temporary counter that we can shift over
    //bit by bit, so that we will either get to the correct list, or find that
    //there is no valid list available
    size = adj_size;


    int list = 0;

  while (list < NUMLISTS)
  {
    if ((list == NUMLISTS - 1) || ((size <= 1) && (free_lists[list] != NULL)))
    {

      ptr = free_lists[list];
      // Ignore blocks that are too small or marked with the reallocation bit
      while ( (ptr != NULL)
        && ( (GET_TAG(HDRP(ptr))) || (adj_size > GET_SIZE(HDRP(ptr)))))
      {
        ptr = PRED_LIST(ptr);
      }
      //stop the loop if the pointer points to a valid list
      if (ptr != NULL)
      {
        break;
      }

    }
    
    size >>= 1;
    list++;
  }
  
  //if the pointer still isn't pointing to a valid list, we have to create a large enough block
  if (ptr == NULL) 
  {
    ex_size = MAX(adj_size, CHUNKSIZE);
    if ((ptr = extend_heap(ex_size)) == NULL)
    {
      return NULL;
    }
  }
  
  //put the block into the list
  place(ptr, adj_size);
  
    //Checks the heap 
    line_count++;
    //mm_check(ptr, checksize);
  return ptr;
}

/*
 * Adjusts the free list, adds a block back to the lis then calls coalesce
 * to make the free list correct again.
 */
void mm_free(void *ptr)
{
  //Size of block
  size_t size = GET_SIZE(HDRP(ptr));
  
  //Adjust the allocation status in boundary tags
  PUT_TAG(HDRP(ptr), PACK(size, 0));
  PUT_TAG(FTRP(ptr), PACK(size, 0));

  //Unset the reallocation tag on the next block
  UNSET_TAG(HDRP(NEXT_BLKP(ptr)));
  
  //Insert new block into proper list
  insert(ptr, size);

  coalesce(ptr);
  

  // Checks the heap
    line_count++;
    //mm_check( ptr, size);
 
  return;
}

/*
 * Reallocates a block. If required, it will also extend the heap
 * by ex_size. this also assures that the next time we reallocate, we
 * won't have to extend it.
 */
void *mm_realloc(void *ptr, size_t size)
{
  void *new_ptr = ptr;
  size_t adj_size = size;
  int bb;
  int rem;
  int ex_size;
  

  //If size == 0 then return NULL since cant alloc 0
  if (size == 0)
  {
    return NULL;
  }
  //Add alignment to new block
  if (adj_size <= DSIZE) 
  {
    adj_size = 2 * DSIZE;
  } 
  else 
  {
    adj_size = DSIZE * ((adj_size + (DSIZE) + (DSIZE - 1)) / DSIZE);
  }
  
  //Add overhead from header/footer etc to block size
  adj_size += BUFFER;
  
  //Calc block buffer
  bb = GET_SIZE(HDRP(ptr)) - adj_size;
  
  //Alloc more space if overhead is too large
  if (bb < 0) 
  {
    //Check if next block is free or epilogue 
    if (!GET_ALLOC(HDRP(NEXT_BLKP(ptr))) || !GET_SIZE(HDRP(NEXT_BLKP(ptr)))) 
    {
      rem = GET_SIZE(HDRP(ptr)) + GET_SIZE(HDRP(NEXT_BLKP(ptr))) - adj_size;
      if (rem < 0) 
      {
        ex_size = MAX(-rem, CHUNKSIZE);
        if (extend_heap(ex_size) == NULL)
        {
          return NULL;
        }
        rem += ex_size;
      }
      
      delete(NEXT_BLKP(ptr));
      //Header
      PUT(HDRP(ptr), PACK(adj_size + rem, 1));
      //Footer
      PUT(FTRP(ptr), PACK(adj_size + rem, 1));
    } 
    else 
    {
      new_ptr = mm_malloc(adj_size - DSIZE);
      memmove(new_ptr, ptr, MIN(size, adj_size));
      mm_free(ptr);
    }
    bb = GET_SIZE(HDRP(new_ptr)) - adj_size;
  }  

  //Tag next block if buffer overhead drops below 2x min
  if (bb < 2 * BUFFER)
  {
    SET_TAG(HDRP(NEXT_BLKP(new_ptr)));
  }


    //Checks the heap
    line_count++;
    //mm_check( ptr, size);

  return new_ptr;
}

/*
 * Extends the heap via the mem_sbrk system call. It puts the newly
 * requested block into the correct list.
 */
static void *extend_heap(size_t size)
{
  void *ptr;                   
  size_t words = size / WSIZE; 
  size_t adj_size;

  //Allocate an even number to ensure proper alignment
  adj_size = (words % 2) ? (words + 1) * WSIZE : words * WSIZE;
  
  //extend heap
  if ((long)(ptr = mem_sbrk(adj_size)) == -1)
  {
    return NULL;
  }
  //set header and footer
  PUT(HDRP(ptr), PACK(adj_size, 0));   
  PUT(FTRP(ptr), PACK(adj_size, 0));  
  PUT(HDRP(NEXT_BLKP(ptr)), PACK(0, 1));
  
  //insert into correct list
  insert(ptr, adj_size);
  
  //return and coalesce if needed
  return coalesce(ptr);
}

/*
 * Insert a block pointer into a segregated list. Lists are
 * segregated by byte size.
 */
static void insert(void *ptr, size_t size) {
  int list = 0;
  void *search_ptr = ptr;
  void *insert_ptr = NULL;
  
  //select list
  while ((list < NUMLISTS - 1) && (size > 1)) 
  {
    size >>= 1;
    list++;
  }
  
 //select list location
  search_ptr = free_lists[list];
  while ((search_ptr != NULL) && (size > GET_SIZE(HDRP(search_ptr)))) 
  {
    insert_ptr = search_ptr;
    search_ptr = PRED_LIST(search_ptr);
  }
  
  //set pred and succ
  insert_helper(list, ptr, insert_ptr, search_ptr);

  return;
}

/*
 * Insert helper function
 */
 static void insert_helper(int list, void* ptr, void* insert_ptr, void* search_ptr)
 {
  if (search_ptr != NULL) 
  {
    if (insert_ptr != NULL) 
    {
      SET_PTR(PRED_PTR(ptr), search_ptr); 
      SET_PTR(SUCC_PTR(search_ptr), ptr);
      SET_PTR(SUCC_PTR(ptr), insert_ptr);
      SET_PTR(PRED_PTR(insert_ptr), ptr);
    } 
    else 
    {
      SET_PTR(PRED_PTR(ptr), search_ptr); 
      SET_PTR(SUCC_PTR(search_ptr), ptr);
      SET_PTR(SUCC_PTR(ptr), NULL);
      
      //add block
      free_lists[list] = ptr;
    }
  } 
  else 
  {
    if (insert_ptr != NULL) 
    {
      SET_PTR(PRED_PTR(ptr), NULL);
      SET_PTR(SUCC_PTR(ptr), insert_ptr);
      SET_PTR(PRED_PTR(insert_ptr), ptr);
    } 
    else 
    {
      SET_PTR(PRED_PTR(ptr), NULL);
      SET_PTR(SUCC_PTR(ptr), NULL);
      
      //add block
      free_lists[list] = ptr;
    }
  }
 }

/*
 * Remove a free block pointer from a segregated list.
 */
static void delete(void *ptr) 
{
  int list = 0;
  size_t size = GET_SIZE(HDRP(ptr));
  
  //select list
  while ((list < NUMLISTS - 1) && (size > 1)) 
  {
    size >>= 1;
    list++;
  }
  
  if (PRED_LIST(ptr) != NULL) 
  {
    if (SUCC_LIST(ptr) != NULL) 
    {
      SET_PTR(SUCC_PTR(PRED_LIST(ptr)), SUCC_LIST(ptr));
      SET_PTR(PRED_PTR(SUCC_LIST(ptr)), PRED_LIST(ptr));
    } 
    else 
    {
      SET_PTR(SUCC_PTR(PRED_LIST(ptr)), NULL);
      free_lists[list] = PRED_LIST(ptr);
    }
  } 
  else 
  {
    if (SUCC_LIST(ptr) != NULL) 
    {
      SET_PTR(PRED_PTR(SUCC_LIST(ptr)), NULL);
    } 
    else 
    {
      free_lists[list] = NULL;
    }
  }
  
  return;
}

/*
 * Reorganizes the free blocks that are next to each other. If the address
 * of a free block ends where another one begins, they could be squished
 * into one block. Now we have to sort it back into an appropriate list.
 */
static void *coalesce(void *ptr)
{
  size_t prev_alloc = GET_ALLOC(HDRP(PREV_BLKP(ptr)));
  size_t next_alloc = GET_ALLOC(HDRP(NEXT_BLKP(ptr)));
  size_t size = GET_SIZE(HDRP(ptr));
  
  //return if nothing to coalesce (pred and succ are alloced)
  if (prev_alloc && next_alloc) 
  {
    return ptr;
  }
  
  //not if prev block is tagged
  if (GET_TAG(HDRP(PREV_BLKP(ptr))))
  {
    prev_alloc = 1;
  }
  //remove old block
  delete(ptr);
  
  //detect free blocks and merge
  if (prev_alloc && !next_alloc) 
  {
    delete(NEXT_BLKP(ptr));
    size += GET_SIZE(HDRP(NEXT_BLKP(ptr)));
    PUT_TAG(HDRP(ptr), PACK(size, 0));
    PUT_TAG(FTRP(ptr), PACK(size, 0));
  } 
  else if (!prev_alloc && next_alloc) 
  {
    delete(PREV_BLKP(ptr));
    size += GET_SIZE(HDRP(PREV_BLKP(ptr)));
    PUT_TAG(FTRP(ptr), PACK(size, 0));
    PUT_TAG(HDRP(PREV_BLKP(ptr)), PACK(size, 0));
    ptr = PREV_BLKP(ptr);
  } 
  else 
  {
    delete(PREV_BLKP(ptr));
    delete(NEXT_BLKP(ptr));
    size += GET_SIZE(HDRP(PREV_BLKP(ptr))) + GET_SIZE(HDRP(NEXT_BLKP(ptr)));
    PUT_TAG(HDRP(PREV_BLKP(ptr)), PACK(size, 0));
    PUT_TAG(FTRP(NEXT_BLKP(ptr)), PACK(size, 0));
    ptr = PREV_BLKP(ptr);
  }
  
  //adjust lists
  insert(ptr, size);
  
  return ptr;
}

/*
 * Properly sets the header pointer and the footer pointer for new blocks.
 * It will split the new blocks if there is enough space.
 *
 */
static void place(void *ptr, size_t adj_size)
{
  size_t psize = GET_SIZE(HDRP(ptr));
  size_t rem = psize - adj_size;
  
  //remove from list
  delete(ptr);
  
  if (rem >= 16) 
  {
    //split
    PUT_TAG(HDRP(ptr), PACK(adj_size, 1)); 
    PUT_TAG(FTRP(ptr), PACK(adj_size, 1));
    PUT(HDRP(NEXT_BLKP(ptr)), PACK(rem, 0)); 
    PUT(FTRP(NEXT_BLKP(ptr)), PACK(rem, 0));   
    insert(NEXT_BLKP(ptr), rem);
  } 
  else 
  {
    //no splitting
    PUT_TAG(HDRP(ptr), PACK(psize, 1)); 
    PUT_TAG(FTRP(ptr), PACK(psize, 1)); 
  }
  return;
}

/*
 * Heap consistency checker. Displays information on current
 * memory operation. Prints information on all blocks and free list
 * entries. Checks header and footer for consistency, as well as whether
 * a free block is positioned in the correct list.
 */
void mm_check(void* caller_ptr, int caller_size)
{
  int size;  // Size of block
  int alloc; // Allocation bit
  char *ptr = pblock + DSIZE;
  int block_count = 1;
  int count_size;
  int count_list;
  int loc;   // Location of block relative to first block
  //int caller_loc = (char *)caller_ptr - ptr;
  int list;
  char *scan_ptr;

  printf("%s\n","Checking heap...\n");

  while ((size = GET_SIZE(HDRP(ptr))) != 0) 
  {
    loc = ptr - pblock - DSIZE;
    
    alloc = GET_ALLOC(HDRP(ptr));
    
    // Print block information
      printf("%d: Block at location %d has size %d and allocation %d\n", block_count, loc, size, alloc);
      if (GET_TAG(HDRP(ptr))) 
      {
        printf("%d: Block at location %d is tagged\n", block_count, loc);
      }
    
    // Check consistency of size and allocation in header and footer
    if (size != GET_SIZE(FTRP(ptr))) 
    {
      printf("%d: Header size of %d does not match footer size of %d\n", block_count, size, GET_SIZE(FTRP(ptr)));
    }
    if (alloc != GET_ALLOC(FTRP(ptr))) 
    {
      printf("%d: Header allocation of %d does not match footer allocation "
        "of %d\n", block_count, alloc, GET_ALLOC(FTRP(ptr)));
    }
    
    // Check if free block is in the appropriate list
    if (!alloc) 
    {
      // Select segregated list
      list = NUMLISTS;
      count_size = size;
      while ((list > -1) && (count_size > 1)) 
      {
        count_size >>= 1;
        list--;
      }
      
      // Check list for free block
      scan_ptr = free_lists[list];
      while ((scan_ptr != NULL) && (scan_ptr != ptr)) 
      {
        scan_ptr = PRED_LIST(scan_ptr);
      }
      if (scan_ptr == NULL) 
      {
        printf("%d: Free block of size %d is not in list index %d\n", block_count, size, list);
      }
    }
    ptr = NEXT_BLKP(ptr);
    block_count++;
  }

  printf("%s\n","Checking lists...\n");

  // Check every list of free blocks for validity
  for (list = NUMLISTS; list > -1; list--) 
  {
    ptr = free_lists[list];
    block_count = 1;
    
    while (ptr != NULL) 
    {
      loc = ptr - pblock - DSIZE;
      size = GET_SIZE(HDRP(ptr));
      
      // Print free block information
        printf("%d %d: Free block at location %d has size %d\n", list, block_count, loc, size);
        if (GET_TAG(HDRP(ptr))) 
        {
          printf("%d %d: Block at location %d is tagged\n", list, block_count, loc);
        }
      
      // Check if free block is in the appropriate list
      count_list = 0;
      count_size = size;
      
      while ((count_list < NUMLISTS - 1) && (count_size > 1)) 
      {
        count_size >>= 1;
        count_list++;
      }
      if (list != count_list) 
      {
        printf("%d: Free block of size %d is in list %d instead of %d\n", loc, size, list, count_list);
      }
      
      // Check validity of allocation bit in header and footer
      if (GET_ALLOC(HDRP(ptr)) != 0) 
      {
        printf("%d: Free block has an invalid header allocation of %d\n", loc, GET_ALLOC(FTRP(ptr)));
      }
      if (GET_ALLOC(FTRP(ptr)) != 0) 
      {
        printf("%d: Free block has an invalid footer allocation of %d\n", loc, GET_ALLOC(FTRP(ptr)));
      }
      
      ptr = PRED_LIST(ptr);
      block_count++;
    }
  }
  
  printf("%s\n","Check completed\n");  
  return;
}
