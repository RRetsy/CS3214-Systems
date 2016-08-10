#include <string.h>
#include <stdio.h>
#define __USE_GNU 1
#define __USE_LARGEFILE64 1
#include <dirent.h>
#include <dlfcn.h>
/* Skip files with this prefix */
#define INVISIBLE "invisible_"
/* Define function pointer type that matches the signature of readdir */
typedef struct dirent * (*readdir_t)(DIR *dir);
/* Intercept readdir. If the call would return a directory entry
* whose name starts with INVISIBLE, return the next entry instead.
* Otherwise, return the original directory entry.
*/
struct dirent *readdir(DIR *dir)
{
#include "readdirsolution.c"
}
