#include <stdlib.h>
#include "queue_utils.h"

int queue_init(struct waiting_queue *queue, int capacity) {
    if (queue == NULL || capacity <= 0) {
        return -1;
    }

    queue->data = (int *)malloc(sizeof(int) * capacity);
    if (queue->data == NULL) {
        return -1;
    }

    queue->capacity = capacity;
    queue->head = 0;
    queue->tail = 0;
    queue->count = 0;
    return 0;
}

void queue_destroy(struct waiting_queue *queue) {
    if (queue == NULL) {
        return;
    }

    free(queue->data);
    queue->data = NULL;
    queue->capacity = 0;
    queue->head = 0;
    queue->tail = 0;
    queue->count = 0;
}

int queue_push(struct waiting_queue *queue, int id, int *waiting_count, int max_chairs) {
    if (queue == NULL || waiting_count == NULL) {
        return -1;
    }

    if (*waiting_count >= max_chairs || queue->count >= queue->capacity) {
        return -1;
    }

    queue->data[queue->tail] = id;
    queue->tail = (queue->tail + 1) % queue->capacity;
    queue->count++;
    (*waiting_count)++;
    return 0;
}

int queue_pop(struct waiting_queue *queue, int *waiting_count) {
    int id;

    if (queue == NULL || waiting_count == NULL) {
        return -1;
    }

    if (*waiting_count <= 0 || queue->count <= 0) {
        return -1;
    }

    id = queue->data[queue->head];
    queue->head = (queue->head + 1) % queue->capacity;
    queue->count--;
    (*waiting_count)--;
    return id;
}

int is_customer_next_safe(const struct waiting_queue *queue, int id) {
    if (queue == NULL) {
        return 0;
    }

    return queue->count > 0 && queue->data[queue->head] == id;
}
