/**
Webserver.h contains appropriate function definitions and such
**/




/*
 * doit - handles HTTP request/response transactions
fd: the file descriptor that represents the client
 */
int doit(int fd);

/*
The thread routine that gets executed whenever a thread is created.
This routine continuously tries to take a file descriptor out of a list
and serve the client.
*/
void *thread(void *vargp);

/*
 * read_requesthdrs - read and parse HTTP request headers
 */
void read_requesthdrs(rio_t *rp);

/*
 * parse_uri - parse URI into filename and CGI args
 *             return 0 if dynamic content, 1 if static
 */
int parse_uri(char *uri, char *filename, char *cgiargs);

/*
 * serve_static - copy a file back to the client 
 */
void serve_static(int fd, char *filename, int filesize, int isPersistent);

/*
 * Serves the load requests
*/
void serve_load(char * uri, int fd, int isPersistent);

/*
 * Serves the meminfo requests
*/
void serve_meminfo(char * uri, int fd, int isPersistent);

/*
 * get_filetype - derive file type from file name
 */
void get_filetype(char *filename, char *filetype);

/*
Returns an error message to the client
fd: the file descriptor of the client
cause: why the message is being displayed
errnum: the status/error number
shortmsg: the message to be sent back
longmsg: longer message
isPersistent: if we are in http/1.1 or http/1.0
 */
void clienterror(int fd, char *cause, char *errnum, char *shortmsg, char *longmsg, int isPersistent);

/*
* Returns a JSON string representing the meminfo from /proc
*/
void get_meminfo(char * meminfoJson);

/*
* Returns a JSON string representing the loadavg from /proc
*/
void get_loadavg(char * loadavgJson);

/*
* Validates a callback argument to make sure it only contains valid characters
*/
bool validateCallback(char* args);

/*
* Busy wait for runloop
*/ 
void *runloop(void *vargp);

/*
Gets the relay address out of the command line arguments, returns if it is a relay mode
*/
int get_relay(int argc, char ** argv, char * relayHost, int * relayPort);

/*
Checks to see if we are supposed to call the loadavg function and show that as a result
*/
int check_load(char * uri);

/*
Checks to see if we are supposed to call the meminfo function and show that as a result
*/
int check_mem(char * uri);

/*
Gets the port out of the command line arguments
*/
int get_port(int argc, char ** argv);

/*
Gets the root directory out of the program parameters
*/
char * get_root(int argc, char ** argv);

/*
Checks to see if we are supposed to use the callback abilities of the server
*/
char * serve_callback(char * uri);

/*
* Returns size of alloc list
*/
int allocAnon();

/*
* returns 0 if list is empty, otherwise size blocks left if something was unmapped
*/
int freeAnon();
/*
Reports the message to the client, along with the appropriate headers
fd: the file descriptor of the client
cause: why the message is being displayed
errnum: the status/error number
shortmsg: the message to be sent back
longmsg: longer message
isPersistent: if we are in http/1.1 or http/1.0
*/
void clientInfo(int fd, char *cause, char *errnum, 
         char *shortmsg, char *longmsg, int isPersistent);

