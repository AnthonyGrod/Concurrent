#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "pipeline-utils.h"
#include "err.h"

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fatal("Usage: %s program_name [...]\n", argv[0]);
    }
    int i = 1;
    const char *program_name = argv[i];

    if (argc == 2) {
        ASSERT_SYS_OK(execlp(program_name, program_name, NULL));
    }

    int pipe_dsc[2];
    ASSERT_SYS_OK(pipe(pipe_dsc));

    pid_t pid = fork();
    ASSERT_SYS_OK(pid);
    while (!pid) {
        // Close the write descriptor.
        ASSERT_SYS_OK(close(pipe_dsc[1]));

        ASSERT_SYS_OK(dup2(pipe_dsc[0], STDIN_FILENO));

        // Close the read descriptor.
        ASSERT_SYS_OK(close(pipe_dsc[0]));

        ASSERT_SYS_OK(pipe(pipe_dsc));

        program_name = argv[++i];
        if (i < argc - 1) {
            pid = fork();
            ASSERT_SYS_OK(pid);
        } else
            break;
    }
    // Close the read descriptor.
    ASSERT_SYS_OK(close(pipe_dsc[0]));

    if (i < argc - 1) {
        ASSERT_SYS_OK(dup2(pipe_dsc[1], STDOUT_FILENO));
    }

    ASSERT_SYS_OK(close(pipe_dsc[1]));
    ASSERT_SYS_OK(execlp(program_name, program_name, NULL));
    return 0;
}