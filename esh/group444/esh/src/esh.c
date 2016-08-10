/*
 * esh - the 'pluggable' shell.
 *
 * Developed by Godmar Back for CS 3214 Fall 2009
 * Virginia Tech.
 */
#include <stdio.h>
#include <readline/readline.h>
#include <unistd.h>
#include <sys/wait.h>
#include <signal.h>
#include <fcntl.h>
#include <assert.h>
#include "esh.h"

struct list job_list;
int jid;

static void
usage(char *progname)
{
	//added a comment to commit
    printf("Usage: %s -h\n"
        " -h            print this help\n"
        " -p  plugindir directory from which to load plug-ins\n",
        progname);

    exit(EXIT_SUCCESS);
}

/* Build a prompt by assembling fragments from loaded plugins that 
 * implement 'make_prompt.'
 *
 * This function demonstrates how to iterate over all loaded plugins.
 */
static char *
build_prompt_from_plugins(void)
{
    char *prompt = NULL;
    struct list_elem * e = list_begin(&esh_plugin_list);

    for (; e != list_end(&esh_plugin_list); e = list_next(e)) {
        struct esh_plugin *plugin = list_entry(e, struct esh_plugin, elem);

        if (plugin->make_prompt == NULL)
            continue;

        /* append prompt fragment created by plug-in */
        char * p = plugin->make_prompt();
        if (prompt == NULL) {
            prompt = p;
        } else {
            prompt = realloc(prompt, strlen(prompt) + strlen(p) + 1);
            strcat(prompt, p);
            free(p);
        }
    }

    /* default prompt */
    if (prompt == NULL)
        prompt = strdup("esh> ");

    return prompt;
}

/* The shell object plugins use.
 * Some methods are set to defaults.
 */
struct esh_shell shell =
{
    .build_prompt = build_prompt_from_plugins,
    .readline = readline,       /* GNU readline(3) */ 
    .parse_command_line = esh_parse_command_line /* Default parser */
};

/*
The main function to run the shell
*/
int
main(int ac, char *av[])
{
    jid = 0;
    int opt;

    /*create the list of plugins as well as initialize the jobs list*/
    list_init(&esh_plugin_list);
    list_init(&job_list);
    /* Process command-line arguments. See getopt(3) */
    while ((opt = getopt(ac, av, "hp:")) > 0) {
        switch (opt) {
        case 'h':
            usage(av[0]);
            break;

        case 'p':
            esh_plugin_load_from_directory(optarg);
            break;
        }
    }

    //load plugins
    esh_plugin_initialize(&shell);
    //set initial process group id
    setpgid(0, 0);

    //set signal handler
    esh_signal_sethandler(SIGCHLD, handle_child);

    //save state
    struct termios *shell_tty = esh_sys_tty_init();

    //give the terminal control to the shell
    give_term_control(getpgrp(), shell_tty);



    /* Read/eval loop. */
    for (;;) {
        /* Do not output a prompt unless shell's stdin is a terminal */
        char * prompt = isatty(0) ? shell.build_prompt() : NULL;
        char * cmdline = shell.readline(prompt);
        free (prompt);

        if (cmdline == NULL)  /* User typed EOF */
            break;\

        struct esh_command_line * cline = shell.parse_command_line(cmdline);
        free (cmdline);
        if (cline == NULL)                  /* Error in command line */
            continue;

        if (list_empty(&cline->pipes)) {    /* User hit enter */
            esh_command_line_free(cline);
            continue;
        }
        
        run(cline, shell_tty);
        esh_command_line_free(cline);
    }
    return 0;
}

/*
The bulk of the logic of the shell happens here
*/
void run(struct esh_command_line * cline, struct termios *shell_tty)
{
    //get pipeline of commands    
    struct list_elem * e = list_begin (&cline->pipes);
    struct esh_pipeline *pipes = list_entry(e, struct esh_pipeline, elem);

    //Gets command to check for built-in
    struct list_elem * f = list_begin (&pipes->commands);
    struct esh_command *cmd = list_entry(f, struct esh_command, elem);

    //Check plugins
    struct list_elem * p = list_begin(&esh_plugin_list);
    for (; p != list_end(&esh_plugin_list); p = list_next(p))
    {
        struct esh_plugin *plugin = list_entry(p, struct esh_plugin, elem);
        if (plugin->process_builtin)
        {
           if (plugin->process_builtin(cmd)) 
            {
                continue;
            }
        }
    }
    
    //delcare the pid
    pid_t pid;

    //Handle built-in commands
    if (is_built_in_cmd(cmd->argv[0]))
    {
        handle_built_in(pipes);
        return;
    }
    else
    {    //heres a comment
         //separate pipe from cline to add to jobs list
        e = list_pop_front(&cline->pipes);
        list_push_back(&job_list, e);
        
        //save shell state
        esh_sys_tty_save(&pipes->saved_tty_state);

        //otherwise execute normal commands
        jid++;
        if (list_empty(&job_list))
        {
            jid = 1;
        }

        //set the job id
        pipes->jid = jid;
        pipes->pgrp = -1;

        //handle piping from command to command in pipeline
        int firstPipe[2];
        int secondPipe[2];
        bool pipelinedCmd = false;

        //Make sure to set whether or not this command is part of a piped command
        if (list_size(&pipes->commands) > 1)
        {
            pipelinedCmd = true;
        }

        struct list_elem * l;
        //Loop through the list of commands and pipe appropriately
        for (l = list_begin(&pipes->commands); l != list_end(&pipes->commands); l = list_next(l))
        {
            //get the command from the list of commands in reference to l
            struct esh_command * command = list_entry(l, struct esh_command, elem);
            //If the command is part of a piped command, and it is not the last command,
            //pipe it to the next command
            if (pipelinedCmd && (list_next(l) != list_tail(&pipes->commands)))
            {
                //create the pipe for both of the pipes
                pipe(secondPipe);
                pipe(firstPipe);
            }

            //launch child process. block signals to parent temporarily
            esh_signal_block(SIGCHLD);
            if ((pid = fork()) == 0)
            {
                pid = getpid();
                command->pid = pid;

                //set process group for command
                if (pipes->pgrp == -1)
                {
                    pipes->pgrp = pid;
                }
                setpgid(pid, pipes->pgrp);

                //check for background jobs
                if (pipes->bg_job)
                {
                   pipes->status = BACKGROUND;
                }
                else //set the jobs status to foreground
                {
                    give_term_control(pipes->pgrp, &pipes->saved_tty_state);
                    pipes->status = FOREGROUND;
                    
                }

                //handle middle piping cases
                if (pipelinedCmd && (l != list_begin(&pipes->commands)))
                {
                    //set up pipes
                    close(firstPipe[1]);
                    dup2(firstPipe[0], 0);
                    close(firstPipe[0]);
                }

                if (pipelinedCmd && (list_next(l) != list_tail(&pipes->commands)))
                {
                    //set up pipes
                    close(secondPipe[0]);
                    dup2(secondPipe[1], 1);
                    close(secondPipe[1]);
                }

                //check for IO redirect
                if (command->iored_input != NULL)
                {
                    int inFile = open(command->iored_input, O_RDONLY);
                    if (dup2(inFile, 0) < 0)
                    {
                        esh_sys_fatal_error("Error dup2\n");
                    }

                    close(inFile);
                }

                if (command->iored_output != NULL)
                {
                    //open the file descriptor
                    int outFile;
                    if (command->append_to_output)
                    {
                        outFile = open(command->iored_output,  O_WRONLY | O_APPEND | O_CREAT, S_IRUSR | S_IRGRP | S_IWGRP | S_IWUSR);
                    }
                    else
                    {
                        outFile = open(command->iored_output,  O_WRONLY | O_CREAT, S_IRUSR | S_IRGRP | S_IWGRP | S_IWUSR);                 
                    }
                    if (dup2(outFile, 1) < 0)
                    {
                        esh_sys_fatal_error("Error dup2\n");
                    }
                    //close the file descriptor
                    close(outFile);                
                }

                //execute the command
                if (execvp(command->argv[0], command->argv) < 0)
                {
                    esh_sys_fatal_error("Error exec'ing process\n");
                }

            }

            //check fork result
            else if (pid < 0)
            {
                    esh_sys_fatal_error("Error in forking child\n");
            }

            //handle control on parent side
            else
            {
                if (pipes->pgrp == -1)
                {
                    pipes->pgrp = pid;
                }
                //move child into its pgrp
                setpgid(pid, pipes->pgrp);

                //give control if not bg
                if (!pipes->bg_job)
                {
                    give_term_control(pipes->pgrp, &pipes->saved_tty_state);
                }

                //if we are not at the beginning we have to close the first pipe (both ends)
                if (pipelinedCmd && (l != list_begin(&pipes->commands)))
                {
                    //close pipes
                    close(firstPipe[0]);
                    close(firstPipe[1]);
                }

                if (pipelinedCmd && (list_next(l) != list_tail(&pipes->commands)))
                {
                   //set pipes from prev command to next command
                    firstPipe[0] = secondPipe[0];
                    firstPipe[1] = secondPipe[1];
                }

                //if we are at the end we need to close all of the pipes
                if (pipelinedCmd && (list_next(l) == list_tail(&pipes->commands)))
                {
                    //close all pipes
                    close(secondPipe[0]);
                    close(secondPipe[1]);
                    close(firstPipe[0]);
                    close(firstPipe[1]);

                }
            }
        }
    }

    //set current job to background if bg_job
    if (pipes->bg_job)
    {
        esh_sys_tty_save(&pipes->saved_tty_state);
        printf("[%d] %d\n", pipes->jid, pipes->pgrp);
        pipes->status = BACKGROUND;
        
    }

    if (!pipes->bg_job)
    {
        wait_on_job(pid, shell_tty);
    }

    esh_signal_unblock(SIGCHLD);
    
}

/*
Waits for jobs based on the pid of the child and restores the shell state
*/
void wait_on_job(pid_t pidChild, struct termios *shell_tty)
{
    int status;
    pid_t pid;

    if ((pid = waitpid(pidChild, &status, WUNTRACED)) > 0) 
    {
        give_term_control(getpgrp(), shell_tty);
        job_status(pid, status);
    }
}

/*
Makes sure the command is built in
*/
bool is_built_in_cmd(char* cmd)
{
    if (strncmp(cmd, "jobs", 4) == 0 || strncmp(cmd, "fg", 2) == 0 || strncmp(cmd, "bg", 2) == 0 ||
        strncmp(cmd, "kill", 4) == 0 || strncmp(cmd, "stop", 4) == 0)
    {
        return true;
    }
    return false;
}

/*
Gets job from job id
*/
struct esh_pipeline * get_job_from_jid(int jid)
{
    struct list_elem * e = list_begin(&job_list);

    for (; e != list_end(&job_list); e = list_next(e))
    {
        struct esh_pipeline *pipeElem = list_entry(e, struct esh_pipeline, elem);

        if (pipeElem->jid == jid)
        {
            return pipeElem;
        }
    }
    return NULL;
}

/*
Handles all built in commands
*/
void handle_built_in(struct esh_pipeline* pipe)
{
    struct list_elem * f = list_begin (&pipe->commands);
    struct esh_command *cmd = list_entry(f, struct esh_command, elem);

    if (strncmp(cmd->argv[0], "jobs", 4) == 0)
    {
        handle_jobs();
    }
    if (strncmp(cmd->argv[0], "kill\0", 5) == 0)
    {
        handle_kill(get_job_id(pipe));
    }
    if (strncmp(cmd->argv[0], "stop", 4) == 0)
    {
        handle_stop(get_job_id(pipe));
    }
    if (strncmp(cmd->argv[0], "fg", 2) == 0)
    {
        handle_fg(get_job_id(pipe));
    }
    if (strncmp(cmd->argv[0], "bg", 2) == 0)
    {
        handle_bg(get_job_id(pipe));
    }
}

/*
Handles the kill command
*/
void handle_kill(int jobId)
{
    struct esh_pipeline *pipe;
    pipe = get_job_from_jid(jobId);
    if (pipe != NULL)
    {
        kill(pipe->pgrp, SIGKILL);
        printf("Killed job: [%d]\n", jobId);
    }
}

/*
Handles the fg command
*/
void handle_fg(int jobId)
{
    esh_signal_block(SIGCHLD);

    //get job
    struct esh_pipeline *pipe;
    pipe = get_job_from_jid(jobId);

    //give control
    give_term_control(pipe->pgrp, &pipe->saved_tty_state);

    //continue if stopped
    if (pipe->status == STOPPED)
    {
        kill(pipe->pgrp, SIGCONT);
    }

    //move to foreground
    pipe->status = FOREGROUND;

    print_job(pipe);  

    //wait
    wait_on_job(pipe->pgrp, &pipe->saved_tty_state);
    esh_signal_unblock(SIGCHLD);    
}
/*
Handles the situation when the bg command is run
*/
void handle_bg(int jobId)
{
    struct esh_pipeline *pipe;
    pipe = get_job_from_jid(jobId);

    pipe->status = BACKGROUND;
    kill(pipe->pgrp, SIGCONT);

    //Save state
    esh_sys_tty_save(&pipe->saved_tty_state);

    print_job(pipe);
}

/*
Handles the situation when the stop command is run
*/
void handle_stop(int jobId)
{
    struct esh_pipeline *pipe;
    pipe = get_job_from_jid(jobId);

    kill(pipe->pgrp, SIGSTOP);
    printf("Stopped job: [%d]", jobId);
}

/*
Handles the situation when the jobs command is run
*/
void handle_jobs()
{
    int i = 1;
    char *status[] = {"Foreground", "Running", "Stopped", "Needs Terminal"};
    if (!list_empty(&job_list))
    {
        struct list_elem * e = list_begin(&job_list);
        struct esh_pipeline * job = list_entry(e, struct esh_pipeline, elem);

        for (;i <= list_size(&job_list); i++)
        {
            printf("[%d]  %s                 ",job->jid, status[job->status]);
            print_job(job);
            e = list_next(e);
            job = list_entry(e, struct esh_pipeline, elem);
        }
    }
}

/*
Handles the signal when SIGCHLD is signaled
*/
void handle_child(int sig, siginfo_t *info, void *_ctxt)
{
    assert (sig == SIGCHLD);
    int status;
    pid_t pid;

    while ((pid = waitpid(-1, &status, WUNTRACED|WNOHANG|WCONTINUED)) > 0) 
    {
        job_status(pid, status);
    }
}

/*
Gets the status of a job by passing in it's process id
*/
void job_status(pid_t pid, int status)
{
    if (pid > 0) 
    {
        struct list_elem *e;
        for (e = list_begin(&job_list); e != list_end(&job_list); e = list_next(e)) 
        {
            struct esh_pipeline *pipeline = list_entry(e, struct esh_pipeline, elem);

            if (pipeline->pgrp == pid)
             {
                if (WIFSTOPPED(status)) 
                {
                    if (WSTOPSIG(status) == 22) 
                    {
                        pipeline->status = STOPPED;
                    }

                    else 
                    {
                        pipeline->status = STOPPED;
                        printf("\n[%d]  Stopped                 ", pipeline->jid);
                        print_commands(job_list);
                    }
                }

                if (WTERMSIG(status) == 9) 
                {
                    list_remove(e);
                }

                // normal termination
                if (WIFEXITED(status))
                {
                    list_remove(e);
                }

                else if (WIFSIGNALED(status))
                {
                    list_remove(e);
                }

                if (list_empty(&job_list))
                {
                    jid = 0;
                }
            }
        }
    }

    else if (pid < 0)
    {
        esh_sys_fatal_error("Error waiting for process");
    }
}

/**
 * Assign ownership of ther terminal to process group
 * pgrp, restoring its terminal state if provided.
 *
 * Before printing a new prompt, the shell should
 * invoke this function with its own process group
 * id (obtained on startup via getpgrp()) and a
 * sane terminal state (obtained on startup via
 * esh_sys_tty_init()).
 */
void give_term_control(pid_t pgrp, struct termios *pg_tty_state)
{
    esh_signal_block(SIGTTOU);
    int rc = tcsetpgrp(esh_sys_tty_getfd(), pgrp);
    if (rc == -1) 
    {
        esh_sys_fatal_error("tcsetpgrp: ");
    }

    if (pg_tty_state) 
    {
        esh_sys_tty_restore(pg_tty_state);
    }
    esh_signal_unblock(SIGTTOU);
}


/*
Prints out the commands when they need to be printed, when the jobs command is called
*/
void print_commands(struct list jobs)
{
    struct list_elem *e = list_begin(&jobs);
    struct esh_pipeline *pipeline = list_entry(e, struct esh_pipeline, elem);

    printf("(");
    for (e = list_begin(&pipeline->commands); e != list_end(&pipeline->commands); e = list_next(e)) 
    {

        struct esh_command *command = list_entry(e, struct esh_command, elem);

        char **argv = command->argv;
        while (*argv) {
            printf("%s ", *argv);
            argv++;
        }

        if (list_size(&pipeline->commands) > 1)
        {
            printf("| ");
        }
    }

    printf(")\n");
}

/*
Prints one job at a time to standard output. It
*/
void print_job(struct esh_pipeline *pipe)
{
    struct list_elem *e;
    printf("%s", "(");
    for (e = list_begin(&pipe->commands); e != list_end(&pipe->commands); e = list_next(e))
    {
        struct esh_command *cmd = list_entry(e, struct esh_command, elem);

        char **cmds = cmd->argv;
        while (*cmds) {
            printf("%s ", *cmds);
            fflush(stdout);
            cmds++;
        }
        //if there is more than one command
        if (1 < list_size(&pipe->commands))
        {
            printf("%s", "| ");
        }
    }

    printf("%s\n", ")");
}

/*
Gets the job (by returning a esh_pipeline) by the process group id
*/
struct esh_pipeline * get_job_from_pgrp(pid_t pgrp) 
{
    struct list_elem *e;
    for (e = list_begin(&job_list); e != list_end(&job_list); e = list_next(e))
    {
        struct esh_pipeline *job = list_entry(e, struct esh_pipeline, elem);
        if (job->pgrp == pgrp)
        {
            return job;
        }
    }

    return NULL;
}

/*
Gets the job id by passing in a job (esh_pipeline)
*/
int get_job_id(struct esh_pipeline * job)
{
    int jobId = -1;
    if (!list_empty(&job_list))
    {
        struct list_elem * f = list_begin (&job->commands);
        struct esh_command *cmd = list_entry(f, struct esh_command, elem);

        if (cmd->argv[1] == NULL)
        {
            struct list_elem *e = list_back(&job_list);
            struct esh_pipeline *pipe = list_entry(e, struct esh_pipeline, elem);

            jobId = pipe->jid;
        }
        else
        {
            if (strncmp(cmd->argv[1], "%", 1) == 0) 
            {
                char *temp = (char*) malloc(5);
                strcpy(temp, cmd->argv[1]+1);
                //ascii to integer job id
                jobId = atoi(temp);
                free(temp);
            }
            else 
            {
                //ascii to integer job id
                jobId = atoi(cmd->argv[1]);
            }
        }
    }
    return jobId;
}
