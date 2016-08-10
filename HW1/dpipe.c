#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <stdlib.h>

#define READ 0
#define WRITE 1
/**
 *This is an implemtation of dpipe for HW1 question 3.
  @author: elobeto
 * */
int main(int argc, char **argv)
{
	int status;
	int pid1, pid2;
	int endpid1, endpid2;

	//create argument arrays
	char *wc_args[] = {argv[1], NULL};
	char *gnetcat_args[] = {argv[2], argv[3], argv[4], NULL};

	//create pipes
	int topPipe[2];
	int bottomPipe[2];
	pipe(topPipe);
	pipe(bottomPipe);

	//fork and exec first program
	if((pid1 = fork()) == 0)
	{
		printf("%s\n", "Starting wc:");
		//redirecting I/o
		dup2(topPipe[WRITE], 1);
		dup2(bottomPipe[READ], 0);

		close(topPipe[READ]);
		close(topPipe[WRITE]);
		close(bottomPipe[READ]);
		close(bottomPipe[WRITE]);

		execvp(wc_args[0], wc_args);
	}
	//fork and exec second program
	else
	{
		if((pid2 = fork()) == 0)
		{
			printf("%s\n", "Starting gnetcat:");
			//redirecting I/O
			dup2(topPipe[READ], 0);
			dup2(bottomPipe[WRITE], 1);

			close(topPipe[READ]);
			close(topPipe[WRITE]);
			close(bottomPipe[READ]);
			close(bottomPipe[WRITE]);

			execvp(gnetcat_args[0], gnetcat_args);
		}
	}

	//close pipes
	close(topPipe[READ]);
	close(topPipe[WRITE]);
	close(bottomPipe[READ]);
	close(bottomPipe[WRITE]);
	
	//wait for children to finish
	endpid1 = wait(&status);
	endpid2 = wait(&status);

	//exit
	exit(0);
}
