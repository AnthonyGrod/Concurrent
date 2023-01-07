#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>
#include <pthread.h>
#include <semaphore.h>
#include <sys/wait.h>
#include <sys/types.h>
#include "utils.h"
#include "err.h"

#define MAX_N_TASKS 4096
#define MAX_LINE 1024
#define MAX_ORDER_LINE 512
#define MAX_END_STATUS 128

typedef struct Task Task;
typedef struct Line Line;

struct Task {
    _Atomic pid_t task_pid;
    _Atomic int task_struct_index;
    _Atomic bool is_running;
    pthread_t thread;
    char **args;
};

struct Line {
    pthread_mutex_t mutex;
    int read_ds;
    char buffer[MAX_LINE];
};

Task tasks[MAX_N_TASKS];
Line lines[MAX_N_TASKS][2]; // lines[i][0] --> struktura dla out i-tego taska. lines[i][1] --> struktura dla err i-tego taska

char queue[MAX_N_TASKS * MAX_END_STATUS];
_Atomic bool executor_occupied = false;
_Atomic int queue_guard_index = 0;
pthread_mutex_t queue_mutex;
pthread_mutex_t occupied_mutex;
sem_t order_semaphore;

void task_error(int task_index) {
    pthread_mutex_lock(&lines[task_index][1].mutex);
    printf("Task %d stderr: '%s'.\n", task_index, lines[task_index][1].buffer);
    pthread_mutex_unlock(&lines[task_index][1].mutex);
}

void* line_read(void* line_struct) {
    Line* line_prop = (Line*) line_struct;
    char line[MAX_LINE];
    line[0] = '\0';
    FILE *file = fdopen(line_prop->read_ds, "r");
    while (fgets(line, MAX_LINE, file) != NULL) {
        pthread_mutex_lock(&(line_prop->mutex));
        strcpy(line_prop->buffer, line);
        strtok(line_prop->buffer, "\n");
        pthread_mutex_unlock(&(line_prop->mutex));
    }
    fclose(file);
    return NULL;
}

void task_out(int task_index) {
    pthread_mutex_lock(&lines[task_index][0].mutex);
    printf("Task %d stdout: '%s'.\n", task_index, lines[task_index][0].buffer);
    pthread_mutex_unlock(&lines[task_index][0].mutex);
}

void task_kill(int task_index) {
    kill(tasks[task_index].task_pid, SIGINT);
}

void print_queue() {
    printf("%.*s\n", queue_guard_index - 1, queue);
    queue_guard_index = 0;
}

void* run_task(void* task_struct) {
    Task *task = (Task*) task_struct;
    pid_t pid;
    task->is_running = true;
    int outfds[2], errfds[2];
    ASSERT_SYS_OK(pipe(outfds));
    ASSERT_SYS_OK(pipe(errfds));
    pid = fork();
    ASSERT_SYS_OK(pid);
    if (!pid) {
        // Child process
        ASSERT_SYS_OK(close(outfds[0]));
        ASSERT_SYS_OK(close(errfds[0]));
        ASSERT_SYS_OK(dup2(outfds[1], STDOUT_FILENO));
        ASSERT_SYS_OK(dup2(errfds[1], STDERR_FILENO));
        ASSERT_SYS_OK(close(outfds[1]));
        ASSERT_SYS_OK(close(errfds[1]));
        execvp(task->args[1], &(task->args[1]));
    }

    task->task_pid = pid;
    printf("Task %d started: pid %d.\n", task->task_struct_index, task->task_pid);
    ASSERT_SYS_OK(close(outfds[1]));
    ASSERT_SYS_OK(close(errfds[1]));
    pthread_mutex_init(&lines[task->task_struct_index][0].mutex, NULL);
    pthread_mutex_init(&lines[task->task_struct_index][1].mutex, NULL);
    lines[task->task_struct_index][0].read_ds = outfds[0];
    lines[task->task_struct_index][1].read_ds = errfds[0];

    pthread_t thread_out, thread_err;

    ASSERT_ZERO(pthread_create(&thread_out, NULL, line_read, &lines[task->task_struct_index][0]));
    ASSERT_ZERO(pthread_create(&thread_err, NULL, line_read, &lines[task->task_struct_index][1]));

    int status;
    sem_post(&order_semaphore);
    waitpid(pid, &status, 0);
    pthread_join(thread_out, NULL);
    pthread_join(thread_err, NULL);

    char end_buffer[MAX_END_STATUS];
    int end_status_length;
    if (WIFEXITED(status)) {
        snprintf(end_buffer, MAX_END_STATUS, "Task %d ended: status %d.\n", task->task_struct_index, WEXITSTATUS(status));
        end_status_length = snprintf(NULL, 0, "Task %d ended: status %d.\n", task->task_struct_index, WEXITSTATUS(status));
    }
    else {
        snprintf(end_buffer, MAX_END_STATUS, "Task %d ended: signalled.\n", task->task_struct_index);
        end_status_length = snprintf(NULL, 0, "Task %d ended: signalled.\n", task->task_struct_index);
    }
    pthread_mutex_lock(&queue_mutex);
    if (executor_occupied) {
        strcpy(queue + queue_guard_index, end_buffer);
        queue_guard_index += end_status_length;
    }
    else {
        printf("%s", end_buffer);
    }
    pthread_mutex_unlock(&queue_mutex);

    task->is_running = false;
    free_split_string(task->args);
    return NULL;
}

int main(void) {
    pthread_mutex_init(&queue_mutex, NULL);
    pthread_mutex_init(&occupied_mutex, NULL);
    sem_init(&order_semaphore, 0, 0);
    int task_index = 0;
    char buffer[MAX_ORDER_LINE];
    buffer[0] = '\0';
    queue[0] = '\0';
    while (read_line(buffer, MAX_ORDER_LINE, stdin)) {
        strtok(buffer, "\n");
        char** args = split_string(buffer);

        pthread_mutex_lock(&occupied_mutex);
        pthread_mutex_unlock(&occupied_mutex);
        if (strcmp(args[0], "run") == 0) {
            tasks[task_index].task_struct_index = task_index;
            tasks[task_index].args = args;
            ASSERT_ZERO(pthread_create(&(tasks[task_index].thread), NULL, run_task, &(tasks[task_index])));

            task_index++;
            sem_wait(&order_semaphore);
        }
        else if (strcmp(args[0], "err") == 0) {
            int task_read_index = atoi(args[1]);
            task_error(task_read_index);
            free_split_string(args);
        }
        else if (strcmp(args[0], "out") == 0) {
            int task_read_index = atoi(args[1]);
            task_out(task_read_index);
            free_split_string(args);
        }
        else if (strcmp(args[0], "kill") == 0) {
            int task_read_index = atoi(args[1]);
            task_kill(task_read_index);
            free_split_string(args);
        }
        else if (strcmp(args[0], "sleep") == 0) {
            int microseconds = atoi(args[1]);
            usleep(microseconds * 1000);
            free_split_string(args);
        }
        else if (strcmp(args[0], "quit") == 0) {
            free_split_string(args);
            break;
        }
        else {
            free_split_string(args);
        }
        if (queue_guard_index != 0 && executor_occupied == false) {
            print_queue();
        }
    }
    pthread_mutex_lock(&occupied_mutex);
    executor_occupied = false;
    pthread_mutex_unlock(&occupied_mutex);

    pthread_mutex_lock(&queue_mutex);
    if (queue_guard_index != 0) {
        print_queue();
    }
    pthread_mutex_unlock(&queue_mutex);

    for (int i = 0; i < task_index; i++) {
        if (tasks[i].is_running) {
            kill(tasks[i].task_pid, SIGKILL);
        }
        pthread_join(tasks[i].thread, NULL);
        pthread_mutex_destroy(&(lines[i][0].mutex));
        pthread_mutex_destroy(&(lines[i][1].mutex));
    }

    return 0;
}

