#define _GNU_SOURCE

#ifndef quadric_memslot
#define quadric_memslot

#include <unistd.h>

#define QUADRIC_MEMSLOT_MAX 20 
#define MEMSLOT_FLAG_WAITING 1
#define MEMSLOT_FLAG_UNDERREAD 2 
#define MEMSLOT_FLAG_SENDING 0
#define QUADRIC_MEMSLOT_WAIT_SECS 3
#define QUADRIC_MEMSLOT_TIMEOUT_SECS 300
#define MEMSLOT_SHARED_BUFFER_SIZE (1024 * 50 ) 


int memslot_init_client(int clientNo);
int memslot_destroy_client();
int memslot_init_server(int clientNo);
void memslot_pre_receive(int slotNo);
//int memslot_send(int slotNo, const char *buffer, int len);
//int memslot_receive(int slotNo, char *buffer, int max);
int memslot_send_fully(int slotNo, const char *buffer, int len);
int memslot_receive_fully(int slotNo, char *buffer, int max);
void memslot_close();

#endif
