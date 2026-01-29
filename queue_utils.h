#ifndef QUEUE_UTILS_H
#define QUEUE_UTILS_H

struct waiting_queue {
    int *data;
    int capacity;
    int head;
    int tail;
    int count;
};

int queue_init(struct waiting_queue *queue, int capacity);
void queue_destroy(struct waiting_queue *queue);
int queue_push(struct waiting_queue *queue, int id, int *waiting_count, int max_chairs);
int queue_pop(struct waiting_queue *queue, int *waiting_count);
int is_customer_next_safe(const struct waiting_queue *queue, int id);

#endif
