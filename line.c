#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "err.h"

#define N_PROC 7

int child(void)
{
    printf("Child: my pid is %d, my parent's pid is %d\n", getpid(), getppid());
    return 0;
}

int main(int argc, char *argv[]) {
    pid_t pid;
    if (argc != 1 && atoi(argv[1]) > 0) {
        ASSERT_SYS_OK(pid = fork());

        if (getpid() != -1) {
            printf("Child: my pid is %d, my parent's pid is %d\n", getpid(), getppid());
        } else {
            printf("pid is 0 \n");
        }


        char buffer[2];
        int ret = snprintf(buffer, sizeof buffer, "%d", atoi(argv[1]) - 1);
        if (ret < 0 || ret >= (int)sizeof(buffer)) {
            fatal("snprintf failed");
        }

        char* args[] = {"4", NULL};

        // Wait for each child.
        printf("N_PROC - atoi(argv[1]) = %d\n", N_PROC - atoi(argv[1]));
        for (int i = 0; i < N_PROC - atoi(argv[1]); ++i)
            ASSERT_SYS_OK(wait(NULL));

        int result = execlp("line" ,"line", args, NULL);
        if (result == -1) {
            printf("\nfailed connection\n");
            return 1;
        }

        return 0;
    }
}