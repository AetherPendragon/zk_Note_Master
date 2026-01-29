#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <time.h>
#include "queue_utils.h"

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond_customer = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_barber_ready = PTHREAD_COND_INITIALIZER;

int num_chairs;
int waiting_count = 0;
int barber_working = 0;
int shop_open = 1;
int current_customer_id = -1;
struct waiting_queue wait_queue;

void* barber(void* arg) {
    (void)arg;

    while (1) {
        pthread_mutex_lock(&mutex);

        while (waiting_count == 0 && shop_open) {
            printf("[理发师] 没有顾客等待，进入睡眠状态\n");
            pthread_cond_wait(&cond_customer, &mutex);
        }

        if (!shop_open && waiting_count == 0) {
            pthread_mutex_unlock(&mutex);
            break;
        }

        if (waiting_count == 0) {
            pthread_mutex_unlock(&mutex);
            continue;
        }

        int curr_customer_id = queue_pop(&wait_queue, &waiting_count);
        current_customer_id = curr_customer_id;
        barber_working = 1;

        printf("├─[理发师]准备为顾客/%d理发（剩余等待=%d）\n", curr_customer_id, waiting_count);
        printf("├─[理发师] 开始为顾客/%d理发...\n", curr_customer_id);
        pthread_cond_broadcast(&cond_barber_ready);
        pthread_mutex_unlock(&mutex);

        sleep(2);

        pthread_mutex_lock(&mutex);
        printf("[理发师] 顾客/%d 理发完成\n", curr_customer_id);
        printf("顾客/%d 满意离开\n", curr_customer_id);
        barber_working = 0;
        current_customer_id = -1;
        pthread_mutex_unlock(&mutex);
    }

    printf("[理发师] 理发店关门，结束工作\n");
    pthread_exit(NULL);
}

void* customer(void* arg) {
    int id = *((int*)arg);
    free(arg);

    pthread_mutex_lock(&mutex);
    printf("顾客/%d 到达理发店，查看等待区...\n", id);

    if (waiting_count >= num_chairs) {
        printf("顾客/%d ：等待区已满，离开理发店\n", id);
        pthread_mutex_unlock(&mutex);
        pthread_exit(NULL);
    }

    if (queue_push(&wait_queue, id, &waiting_count, num_chairs) == 0) {
        printf("顾客/%d 进入等待区（当前等待人数=%d）\n", id, waiting_count);
        pthread_cond_signal(&cond_customer);

        while (current_customer_id != id) {
            pthread_cond_wait(&cond_barber_ready, &mutex);
        }

        printf("顾客/%d 开始理发\n", id);
    }

    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

int main(int argc, char *argv[]) {
    if (argc != 3) {
        printf("Usage: %s <seats> <customers>\n", argv[0]);
        return 1;
    }

    num_chairs = atoi(argv[1]);
    int total_customers = atoi(argv[2]);

    printf("[系统] 理发店开张：座位数=%d，今日顾客=%d\n", num_chairs, total_customers);

    if (queue_init(&wait_queue, num_chairs) != 0) {
        printf("[系统] 队列初始化失败！\n");
        return 1;
    }

    srand(12345);

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

    pthread_mutex_lock(&mutex);
    shop_open = 0;
    pthread_mutex_unlock(&mutex);
    pthread_cond_signal(&cond_customer);
    pthread_join(b_thread, NULL);

    queue_destroy(&wait_queue);
    free(c_threads);
    printf("[系统] 理发店打烊\n");
    return 0;
}
