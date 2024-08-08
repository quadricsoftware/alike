#define _GNU_SOURCE

#ifndef quadric_blkfs
#define quadric_blkfs

#include "memslot.h"

#define G_MAX_PIPES QUADRIC_MEMSLOT_MAX
#define G_NULL_PIPE (QUADRIC_MEMSLOT_MAX +1)

extern void *blkfs_state;

enum blkfs_command_e { BLKFS_COMMAND_DATA, BLKFS_COMMAND_ATTR, BLKFS_COMMAND_LIST, BLKFS_COMMAND_FOLLOW_LINK};
typedef enum blkfs_command_e blkfs_command_t;

struct blkfs_record {
	int64_t clientId;
	int64_t offset;
	int64_t txId;
	int32_t pathLen;
	int32_t length;
	int32_t command;
	int32_t increment;
};


int blkfs_do_read(const char *, char *, size_t, off_t);
int blkfs_init(int party);
int blkfs_destroy();
int blkfs_wait_for_control_pipe(const char *);
int blkfs_write_control_message(const struct blkfs_record *, const char *);
uint32_t blkfs_get_next_datafile();
void blkfs_unlink_datafile(uint32_t);
int blkfs_read_var_pipe_entry_string(int, char **);

#endif
