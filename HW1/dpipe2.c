#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <stdlib.h>

#define READ 0
#define WRITE 1

int main(int argc, char **argv)
{
	int status;
	int pid1, pid2;
	int endpid1, endpid2;

	char *wc_args[] = {argv[1], NULL};
	char *gnetcat_args[] = {argv[2], argv[3], argv[4], NULL};

	int topPipe[2];
	int bottomPipe[2];
	pipe(topPipe);
	pipe(bottomPipe);

	if((pid1 = fork()) == 0)
	{
		printf("%s\n", "Starting wc:");
		dup2(topPipe[WRITE], 1);
		dup2(bottomPipe[READ], 0);

		close(topPipe[READ]);
		close(topPipe[WRITE]);
		close(bottomPipe[READ]);
		close(bottomPipe[WRITE]);

		execvp(wc_args[0], wc_args);
	}
	else
	{
		if((pid2 = fork()) == 0)
		{
			printf("%s\n", "Starting gnetcat:");
			dup2(topPipe[READ], 0);
			dup2(bottomPipe[WRITE], 1);

			close(topPipe[READ]);
			close(topPipe[WRITE]);
			close(bottomPipe[READ]);
			close(bottomPipe[WRITE]);

			execvp(gnetcat_args[0], gnetcat_args);
		}
	}

	close(topPipe[READ]);
	close(topPipe[WRITE]);
	close(bottomPipe[READ]);
	close(bottomPipe[WRITE]);

	endpid1 = wait(&status);
	endpid2 = wait(&status);
}
