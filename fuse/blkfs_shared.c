#define _GNU_SOURCE
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <linux/fs.h>
#include <fuse.h>
#include <signal.h>
#include <pthread.h>
#include <limits.h>
#include <stdint.h>
#include <syslog.h>
#include <glib.h>
#include <ctype.h>
#include "memslot.h"
#include "blkfs.h"

#define CLIENT_ID 0
#define HOME_DIR "/tmp/interproc"
#define MAX_WAIT_TIME_SECS 30

const char *g_file_path = HOME_DIR"/ku_0_";
const char *g_control_pipe_path = HOME_DIR"/ku_control_pipe";
const int SLEEP_MAX_ATTEMPTS = 10000000;
const int STRING_MAX_LEN = 10000;


struct blkfs_globals {
	uint32_t txNum;
	pthread_mutex_t lock;
	pthread_cond_t condition;
	int control_pipe;
	GSList *pipe_list;
};


void *blkfs_state = 0;

void blkfs_init_globals() {
	struct blkfs_globals *s = malloc(sizeof(struct blkfs_globals));
	s->txNum = 0;
	s->pipe_list = NULL;
	s->control_pipe = 0;
	blkfs_state = (void*) s;

};

/*
 * Returns a pipe id to the glib slist for reuse
 */
void blkfs_unlink_datafile(uint32_t txNum) {
	struct blkfs_globals *s = (struct blkfs_globals*) blkfs_state;
	uint32_t pipeId = txNum;
	if(pipeId == G_NULL_PIPE) {
		// this is harmless
		return;
	}
	pthread_mutex_lock(&s->lock);
	//syslog(LOG_DEBUG, "Returning pipe %d", pipeId);_
	s->pipe_list = g_slist_prepend(s->pipe_list, GUINT_TO_POINTER(pipeId));
	pthread_cond_signal(&s->condition);
	pthread_mutex_unlock(&s->lock);
	//syslog(LOG_DEBUG, "blkfs_unlink_datafile: return of tx %d complete", txNum);

}
	
/*
 * Obtains a pipe id from a pool of pre-used ones held in a glib slist
 * if the slist is empty, creates a new pipe id
 */ 
uint32_t blkfs_get_next_datafile() {
	struct blkfs_globals *s = (struct blkfs_globals*) blkfs_state;

	if(pthread_mutex_lock(&s->lock) != 0) {
		syslog(LOG_ERR, "Unable to obtain mutex, race conditions await");
		return 0;
	}
	uint32_t nextPipe = G_NULL_PIPE;
	time_t nowish = time(NULL);
	while(1) {
		time_t later = time(NULL);
		if(later - nowish > MAX_WAIT_TIME_SECS) {
			syslog(LOG_ERR, "Timeout waiting on write pool--consider increasing memory");
			break;
		}
		if(s->pipe_list != NULL) {
			nextPipe = GPOINTER_TO_UINT(s->pipe_list->data);
			s->pipe_list = g_slist_delete_link(s->pipe_list, s->pipe_list);
			break;
		} else {
			if(s->txNum >= G_MAX_PIPES) {
				syslog(LOG_DEBUG, "All %d pipes busy, we are on %d", G_MAX_PIPES, s->txNum);
				// We need to wait for a pipe to be returned
				struct timespec timeToWait;
				clock_gettime(CLOCK_REALTIME, &timeToWait);
				timeToWait.tv_sec += MAX_WAIT_TIME_SECS;
				//timeToWait.tv_nsec = (now.tv_usec * 1000UL);
				pthread_cond_timedwait(&s->condition, &s->lock, &timeToWait);
			} else {
				syslog(LOG_INFO, "Incrementing circulated bucket count from current return value of %u to %u, max is %d", s->txNum, s->txNum + 2, G_MAX_PIPES);
				nextPipe = s->txNum;
				s->txNum+=2;
				break;
			}
		}
	}
	pthread_mutex_unlock(&s->lock);
	//syslog(LOG_DEBUG, "Returning txNo token %u", nextPipe);
	return nextPipe;
}



/*
 * Mallocs a string based on the size specified in the length field of the pipe
 * Keeps the fd open and doesn't hang up
 */
int blkfs_read_var_pipe_entry_string(int txNum, char **dest) {
	// Determine size of the "payload"
	int32_t payload = 0;
	//syslog(LOG_DEBUG, "About to read length field from sharedmem");
	int retVal = memslot_receive_fully(txNum, (char*) &payload, sizeof(int32_t));
	if(retVal == sizeof(int32_t)) {
		if(payload > STRING_MAX_LEN) {
			syslog(LOG_ERR, "Memslot attempted to return buffer thats too large");
			errno = EMSGSIZE;
			return -1;
		}
		if(payload == 0) {
			//syslog(LOG_DEBUG, "read_var_pipe_entry_string found a payload of zero for str header, will return zero");
			// This is a zero-length string 
			return 0;
		}
		*dest = malloc(payload + 1);
		if(*dest == NULL) {
			syslog(LOG_ERR, "Malloc failed!");
			return -1;
		}
		retVal = memslot_receive_fully(txNum, *dest, payload);
		if(retVal < 1) {
			syslog(LOG_ERR, "blkfs_read_var_pipe_entry got a 'bad' return code from memslot_receive_fully of %d", retVal);
			// This is not really good
			free(*dest);
			return retVal;
		}
		(*dest)[payload] = 0;
	} else if(retVal == -1) {
		syslog(LOG_ERR, "Attempt to read length field off fifo failed");
		return -1;
	}
	//syslog(LOG_DEBUG, "Exiting read_var_pipe_entry_string with return code %d", retVal);
	return retVal;
}



/*
 * Handles opening and writing to the control pipe
 */
int blkfs_do_write_control_string(const char *msg, int len) {
	 struct blkfs_globals *s = (struct blkfs_globals*) blkfs_state;
	// Write our read request to the control pipe
	int writeRet = write(s->control_pipe, msg, len);
	if(writeRet == -1) {
		syslog(LOG_ERR, "Write to the control pipe failed with error %d", errno);
		return -1;
	}
	if(writeRet < len) {
		// Recurse to finish the job
		syslog(LOG_INFO, "Underwrite occured to control pipe, sending remainder...");
		return blkfs_do_write_control_string(msg + writeRet, len - writeRet);
	}
	return 0;
}

int blkfs_write_control_string(const char *msg, int len) {
	struct blkfs_globals *s = (struct blkfs_globals*) blkfs_state;

	pthread_mutex_lock(&s->lock);
	int rez = blkfs_do_write_control_string(msg, len);
	pthread_mutex_unlock(&s->lock);
	return rez;
}
	

/*
 * Packages up a control message with the record and the path string and sends it to the control pipe
 */
int blkfs_write_control_message(const struct blkfs_record *rec, const char *path) {
	//syslog(LOG_INFO, "About to send record with string at the end. String is %s, length is %d", path, rec->pathLen);
	int len = rec->pathLen + sizeof(struct blkfs_record);
	char *buf = malloc(len);
	if(buf == NULL) {
		return -1;
	}
	memcpy(buf, rec, sizeof(struct blkfs_record));
	memcpy(buf + sizeof(struct blkfs_record), path, rec->pathLen);
	int retVal = blkfs_write_control_string(buf, len);
	free(buf);
	return retVal;
}


int blkfs_do_read(const char *path, char *buf, size_t size, off_t offset) {

	//syslog(LOG_INFO, "Entering blkfs_do_read for %s size %zu and offset of %zu", path, size, offset);
	uint32_t txNum = blkfs_get_next_datafile();
	if(txNum == G_NULL_PIPE) {
		// Timed out waiting
		return -EBUSY;
	}
	
	struct blkfs_record rec= { 
		.clientId = 0,
		.offset = offset,
		.txId = txNum,
		.pathLen = strlen(path),
		.length = size,
		.command = 0,
		.increment = 0
	};
	memslot_pre_receive(txNum);
	int retVal = blkfs_write_control_message(&rec, path);
	//syslog(LOG_INFO, "Control message sent to %u....", txNum);
	if(retVal == -1) {	
		return -EPIPE;
	}
	/* struct timespec start, end;
	clock_gettime(CLOCK_MONOTONIC_RAW, &start);	*/
	retVal = memslot_receive_fully(txNum, buf, size);
	blkfs_unlink_datafile(txNum);
	/* clock_gettime(CLOCK_MONOTONIC_RAW, &end);
        uint64_t delta_us = (end.tv_sec - start.tv_sec) * 1000000 + (end.tv_nsec - start.tv_nsec) / 1000;
        double bytesPerUs = size / delta_us;
	//double mbPerSec = bytesPerUs / 1048.576;
        syslog(LOG_DEBUG, "Data readin of %u took %lu us to slot %u at %.6fMB/s", retVal, delta_us, txNum, bytesPerUs); */

	if(retVal == -1) {
		syslog(LOG_ERR, "Return value is -1 after reading memslot, likely timeout");
		retVal = -1;
	}
	if(retVal != size) {
		//syslog(LOG_INFO, "Read request underread; returning %d of %zu requested on pipe %u", retVal, size, txNum);
	}
	//syslog(LOG_INFO, "Read request returning %d of %zu requested for pipe %u", retVal, size, txNum);
	return retVal;	
}

/*
 * Waits for the output pipe to be created by the Java process
 */
int blkfs_wait_for_control_pipe(const char *filePath) {
	//syslog(LOG_INFO, "About to check for output file %s", filePath);
	int sleepCount = 0;
	while(access(filePath, F_OK) == -1) {
		if(sleepCount++ > SLEEP_MAX_ATTEMPTS) {
			sleepCount = -1;
			break;
		}
		usleep(100000);
	}
	//syslog(LOG_INFO, "Done waiting on output file %s", filePath);
	if(sleepCount == -1) {
		syslog(LOG_ERR, "Fuse adapter timed out waiting on Java process");
		errno = EPIPE;
		return -1;
	}
	return 0;
}

int blkfs_destroy() {
 struct blkfs_globals *s = (struct blkfs_globals*) blkfs_state;
	close(s->control_pipe);
	pthread_mutex_destroy(&s->lock);
	memslot_destroy_client(CLIENT_ID);
	free(s);
	return 0;
}

int blkfs_init(int party)
{
	blkfs_init_globals();
    struct blkfs_globals *s = (struct blkfs_globals*) blkfs_state;
	s->txNum  = party;
    if (pthread_mutex_init(&s->lock, NULL) != 0)
    {
        printf("\n mutex init failed\n");
        return 1;
    }
	if(pthread_cond_init(&s->condition, NULL) != 0) {
		printf("\nCondition init failed\n");
	}
	if(memslot_init_client(CLIENT_ID) == -1) {
		syslog(LOG_ERR, "Unable to open shared memory");
		return -1;
	}
	
	blkfs_wait_for_control_pipe(g_control_pipe_path);
	s->control_pipe = open(g_control_pipe_path, O_WRONLY);
	if(s->control_pipe == -1) {
		syslog(LOG_ERR, "Unable to open control pipe");
		return -1;
	}
        syslog(LOG_INFO, "blkfs_shared initialized ok for party %d", party);	
	return 0;
}

