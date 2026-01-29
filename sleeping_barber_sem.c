#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <semaphore.h>
#include <unistd.h>
#include <time.h>
#include "queue_utils.h"

sem_t sem_customers;
sem_t sem_mutex;
sem_t *sem_customer_ready;

int num_chairs;
int waiting_count = 0;
int shop_open = 1;
int total_customers = 0;
struct waiting_queue wait_queue;

void* barber(void* arg) {
    (void)arg;

    while (1) {
        sem_wait(&sem_mutex);
        if (waiting_count == 0 && shop_open) {
            printf("[理发师] 没有顾客等待，进入睡眠状态\n");
        }
        sem_post(&sem_mutex);

        sem_wait(&sem_customers);

        sem_wait(&sem_mutex);
        if (!shop_open && waiting_count == 0) {
            sem_post(&sem_mutex);
            break;
        }

        if (waiting_count == 0) {
            sem_post(&sem_mutex);
            continue;
        }

        int curr_id = queue_pop(&wait_queue, &waiting_count);

        printf("├─[理发师]准备为顾客/%d理发（剩余等待=%d）\n", curr_id, waiting_count);
        printf("├─[理发师] 开始为顾客/%d理发...\n", curr_id);
        sem_post(&sem_mutex);
        sem_post(&sem_customer_ready[curr_id]);

        sleep(2);

        sem_wait(&sem_mutex);
        printf("[理发师] 顾客/%d 理发完成\n", curr_id);
        printf("顾客/%d 满意离开\n", curr_id);
        sem_post(&sem_mutex);
    }

    printf("[理发师] 理发店关门，结束工作\n");
    fflush(stdout);
    pthread_exit(NULL);
}

void* customer(void* arg) {
    int id = *((int*)arg);
    free(arg);

    sem_wait(&sem_mutex);
    printf("顾客/%d 到达理发店，查看等待区...\n", id);

    if (waiting_count >= num_chairs) {
        printf("顾客/%d ：等待区已满，离开理发店\n", id);
        sem_post(&sem_mutex);
        pthread_exit(NULL);
    }

    if (queue_push(&wait_queue, id, &waiting_count, num_chairs) == 0) {
        printf("顾客/%d 进入等待区（当前等待人数=%d）\n", id, waiting_count);
        sem_post(&sem_customers);
        sem_post(&sem_mutex);

        sem_wait(&sem_customer_ready[id]);
        printf("顾客/%d 开始理发\n", id);
        pthread_exit(NULL);
    }

    sem_post(&sem_mutex);
    pthread_exit(NULL);
}

int main(int argc, char *argv[]) {
    if (argc != 3) {
        printf("Usage: %s <seats> <customers>\n", argv[0]);
        return 1;
    }
    setvbuf(stdout, NULL, _IONBF, 0);

    num_chairs = atoi(argv[1]);
    total_customers = atoi(argv[2]);

    if (queue_init(&wait_queue, num_chairs) != 0) {
        printf("[系统] 队列初始化失败！\n");
        return 1;
    }

    srand(12345);

    printf("[系统] 理发店开张：座位数=%d，今日顾客=%d\n", num_chairs, total_customers);

    sem_init(&sem_customers, 0, 0);
    sem_init(&sem_mutex, 0, 1);

    sem_customer_ready = malloc(sizeof(sem_t) * (total_customers + 1));
    if (sem_customer_ready == NULL) {
        printf("[系统] 内存分配失败！\n");
        return 1;
    }
    for (int i = 1; i <= total_customers; i++) {
        sem_init(&sem_customer_ready[i], 0, 0);
    }

    pthread_t b_thread;
    pthread_create(&b_thread, NULL, barber, NULL);

    pthread_t *c_threads = malloc(sizeof(pthread_t) * total_customers);
    for (int i = 0; i < total_customers; i++) {
        int* id = malloc(sizeof(int));
        *id = i + 1;
        pthread_create(&c_threads[i], NULL, customer, id);
        usleep(rand() % 1000000);
    }

    for (int i = 0; i < total_customers; i++) {
        pthread_join(c_threads[i], NULL);
    }

    sem_wait(&sem_mutex);
    shop_open = 0;
    sem_post(&sem_mutex);
    sem_post(&sem_customers);
    pthread_join(b_thread, NULL);
    printf("[系统] 理发店打烊\n");
    fflush(stdout);

    queue_destroy(&wait_queue);
    sem_destroy(&sem_customers);
    sem_destroy(&sem_mutex);
    for (int i = 1; i <= total_customers; i++) {
        sem_destroy(&sem_customer_ready[i]);
    }
    free(sem_customer_ready);
    free(c_threads);

    return 0;
}
