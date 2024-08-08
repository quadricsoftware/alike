#define _GNU_SOURCE

#include <syslog.h>
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <signal.h>
#include <pthread.h>
#include "memslot.h"

struct memslot_impl {
        pthread_mutex_t lock;
        pthread_cond_t condition;
	pthread_mutexattr_t attr;
	pthread_condattr_t attrc;
	int terminated;
	int txNo;
	
	int32_t waiting;
        int32_t bufLen;
};


static char g_fileName[1024];
static char *g_mem;
static int g_fileDesc;
static int g_memsize;
static char *g_cursors[QUADRIC_MEMSLOT_MAX];
static char *g_buffers[QUADRIC_MEMSLOT_MAX];



static void privateInit(int clientNo) {
	g_memsize = QUADRIC_MEMSLOT_MAX * ( sizeof(struct memslot_impl) + MEMSLOT_SHARED_BUFFER_SIZE) ;
	g_memsize++;
	sprintf(g_fileName,"%s_%u", "/tmp/k_sharedmem", clientNo);
}

static struct memslot_impl* getSlot(int slotNo) {
	if(slotNo > QUADRIC_MEMSLOT_MAX || slotNo < 0) {
		syslog(LOG_ERR, "Out-of-bounds memslot request to illegal slot %d", slotNo);
		return NULL;
	}
	// Each memslot_impl is followed by an arbitrary-sized memory area of MEMSLOT_SHARED_BUFFER_SIZE
	int slotSize = sizeof(struct memslot_impl);
	char *ptr = g_mem + ((slotSize + MEMSLOT_SHARED_BUFFER_SIZE) * slotNo);
	struct memslot_impl *impl = (struct memslot_impl*) ptr;
	// Initialize ptr to memory area
	g_buffers[slotNo] = ptr + slotSize;
	//syslog(LOG_INFO, "getSlotptr is at %p, buffer is at %p, impl is at %p", ptr, g_buffer, impl);
	return impl;
}


int memslot_init_client(int clientNo) {
	privateInit(clientNo);
	/* open the input file */
	if ((g_fileDesc = open (g_fileName, O_RDWR)) < 0) {
		syslog(LOG_ERR, "Cannot open %s for memory mapping", g_fileName);
		return -1;
	}
   
	/* mmap the input file */
	if ((g_mem = mmap (0, g_memsize, PROT_READ | PROT_WRITE, MAP_SHARED, g_fileDesc, 0))
							== (caddr_t) -1) {
		syslog(LOG_ERR, "mmap error for input");
		return -1;
	}
	return 0;
}
	

int memslot_init_server(int clientNo) {
	openlog("jni", LOG_PID|LOG_CONS, LOG_USER);
	syslog(LOG_INFO, "JNI sharedmem facility initialing");
	privateInit(clientNo);
	int mode = S_IRWXU;

	/* open/create the output file */
	if ((g_fileDesc = open (g_fileName, O_RDWR | O_CREAT | O_TRUNC, mode )) < 0) {
		syslog(LOG_ERR, "Cannot open %s for memory mapping", g_fileName);
		return -1;
	}


	/* go to the location corresponding to the last byte */
	if (lseek (g_fileDesc, g_memsize +1, SEEK_SET) == -1) {
		syslog(LOG_ERR, "lseek error");
		return -1;
	}

	/* write a dummy byte at the last location */
	if (write (g_fileDesc, "", 1) != 1){
		syslog(LOG_ERR, "write error");
		return -1;
	}
	/* mmap the output file */
	if ((g_mem = mmap (0, g_memsize, PROT_READ | PROT_WRITE, MAP_SHARED, g_fileDesc, 0)) == (caddr_t) -1) {
		syslog(LOG_ERR, "mmap error for output");
		return -1;
	}
	/* initialize mutex structures */
	for(int x = 0; x < QUADRIC_MEMSLOT_MAX; ++x) {
		struct memslot_impl *impl = getSlot(x);
		pthread_mutexattr_init(&impl->attr);
		pthread_mutexattr_setrobust(&impl->attr, PTHREAD_MUTEX_ROBUST);
		pthread_mutexattr_setpshared(&impl->attr, PTHREAD_PROCESS_SHARED);
		
		pthread_condattr_init(&impl->attrc);
		pthread_condattr_setpshared(&impl->attrc, PTHREAD_PROCESS_SHARED);

		 if (pthread_mutex_init(&impl->lock, &impl->attr) != 0) {
			syslog(LOG_ERR, "Mutex init failed");
	        	return -1;
		}	
		if(pthread_cond_init(&impl->condition, &impl->attrc) != 0) {
			syslog(LOG_ERR, "Condition initialization failed");
			return -1;
		}
		impl->terminated = 0;
	}
	return 0;
}

   
void memslot_close() {
	 for(int x = 0; x < QUADRIC_MEMSLOT_MAX; ++x) {
                struct memslot_impl *impl = getSlot(x);
		pthread_mutex_lock(&impl->lock);
		impl->terminated = 1;
		for(int x = 0; x < 10; ++x) {
			pthread_cond_signal(&impl->condition);
        	        usleep(1000);
		}
		pthread_mutex_unlock(&impl->lock);
		pthread_cond_destroy(&impl->condition);
		pthread_mutex_destroy(&impl->lock);

		pthread_mutexattr_destroy(&impl->attr);
		pthread_condattr_destroy(&impl->attrc);
        }

	munmap(g_mem, g_memsize);
	close(g_fileDesc);
}

int memslot_destroy_client() {
	munmap(g_mem, g_memsize);
	close(g_fileDesc);
	return 0;
}


static int memslot_wait_on_slot(struct memslot_impl *impl, int secsSleepMax, int waitCondition) {
	struct timespec start, end;
        clock_gettime(CLOCK_MONOTONIC_RAW, &start);	
	while(impl->waiting != waitCondition) {
		//syslog(LOG_INFO, "In memslot_wait_on_slot, current impl condition is %d, but need %d", impl->waiting, waitCondition);
		if(impl->terminated != 0) {
			return -1;
		}
		// If we wait too long, break out
		clock_gettime(CLOCK_MONOTONIC_RAW, &end);
                uint64_t delta_us = (end.tv_sec - start.tv_sec) * 1000000 + (end.tv_nsec - start.tv_nsec) / 1000;
                uint64_t delta_ms = delta_us / 1000;
                if(delta_ms > ( secsSleepMax * 1000)) {
			syslog(LOG_ERR, "memslot_wait timeout");
                        return -1;
                }
		
		// Do the wait...
		struct timespec timeToWait;
             	clock_gettime(CLOCK_REALTIME, &timeToWait);
             	timeToWait.tv_sec += QUADRIC_MEMSLOT_WAIT_SECS;
             	int rez = pthread_cond_timedwait(&impl->condition, &impl->lock, &timeToWait);
		if(rez == ETIMEDOUT) {
			//syslog(LOG_DEBUG, "I slept, but timed out after QUADRIC_MEMSLOT_WAIT_SECS");
		} else if(rez == EINVAL) {
			syslog(LOG_ERR, "timedwait says I'm invalid!");
			return -1;
		} else if(rez == EPERM) {
			syslog(LOG_ERR, "I don't own the mutex!");
			return -1;
		} else if(rez == 0) {
			//syslog(LOG_DEBUG, "Oh man, a normal return code! I was signaled dude!");
		}
	}
	//syslog(LOG_INFO, "Falling out of memslot_wait_on_slot with condition fulfilled of %d at txNo of %d", impl->waiting, impl->txNo);
	return 1;
}


static int memslot_client_reset(struct memslot_impl *impl, int slotNo) {
        impl->waiting = MEMSLOT_FLAG_WAITING;
        impl->bufLen = -1;
	g_cursors[slotNo] = g_buffers[slotNo];
	impl->txNo++;
	//syslog(LOG_DEBUG, "In memslot_client_reset, buffer is %p and cursor is %p", g_buffer, g_cursor);
	return 0;
}

void memslot_pre_receive(int slotNo) {
	//syslog(LOG_DEBUG, "Pre-receive call has been made brother for slot %d", slotNo);
	struct memslot_impl *impl = getSlot(slotNo);
      	pthread_mutex_lock(&impl->lock);
	memslot_client_reset(impl, slotNo);
	pthread_mutex_unlock(&impl->lock);
}

static int memslot_send_impl(int slotNo, const char *buffer, int len) {
	//syslog(LOG_INFO, "Entering memslot_send_impl on slot %d with len %d", slotNo, len);
	if(len > MEMSLOT_SHARED_BUFFER_SIZE) {
		//syslog(LOG_INFO, "Requested len %d is greater than shared buffer size, will truncate to MEMSLOT_SHARED_BUFFER_SIZE", len);
		len = MEMSLOT_SHARED_BUFFER_SIZE;
		//syslog(LOG_INFO, "New size is %d", len);
	}
	struct memslot_impl *impl = getSlot(slotNo);
        int myCode = pthread_mutex_lock(&impl->lock);
	if(myCode != 0) {
		syslog(LOG_ERR, "Memslot send mutex failed, dying");
		return -1;
	}
        // Wait on the slot so it's in the correct state
        int rez = memslot_wait_on_slot(impl, QUADRIC_MEMSLOT_TIMEOUT_SECS, MEMSLOT_FLAG_WAITING);
        if(rez < 1) {
		syslog(LOG_ERR, "memslot_send timeout");
		pthread_mutex_unlock(&impl->lock);
		return -1;
	}
	//syslog(LOG_INFO, "Impl buffer address is %p.", g_buffer);
	//syslog(LOG_INFO, "BuffLen is %d, my len is %d, waiting is %d, txNo is %d",  impl->bufLen, len, impl->waiting, impl->txNo);
	if(len > 0) {
	        memcpy(g_buffers[slotNo], buffer, len);
	}
        impl->bufLen = len;
        impl->waiting = MEMSLOT_FLAG_SENDING;
	//syslog(LOG_INFO, "Ok sarge, lets signal the dang condition after memcpy of %d with txNo %d", len, impl->txNo);
	if(pthread_cond_signal(&impl->condition) != 0) {
		syslog(LOG_ERR, "Writer condition signal failed");
	}
	//syslog(LOG_INFO, "CONDITION SIGNALED, LET HER RIP");
        pthread_mutex_unlock(&impl->lock);
	return len;	
}

static int memslot_receive_impl(int slotNo, char *buffer, int max) {
	//syslog(LOG_DEBUG, "Entering memslot_receive_impl for slot %d wanting %d max", slotNo, max);
	struct memslot_impl *impl = getSlot(slotNo);
        int rezCode = pthread_mutex_lock(&impl->lock);
	if(rezCode != 0) {
		syslog(LOG_ERR, "memslot_receive_impl could not obtain mutex, dying");
		return -1;
	}
	//syslog(LOG_DEBUG, "MUTEX OBTAINED");
	if(impl->waiting != MEMSLOT_FLAG_UNDERREAD) {
		int rez = memslot_wait_on_slot(impl, QUADRIC_MEMSLOT_TIMEOUT_SECS, MEMSLOT_FLAG_SENDING);
		if(rez < 1) {
			pthread_mutex_unlock(&impl->lock);
			errno = EPIPE;
			return -1;
		}
	} else {
		//syslog(LOG_DEBUG, "***Completing underread on slot %d", slotNo);
	}
	int amt = 0;
	if(impl->bufLen > 0) {
		// OK to memcpy	
		amt = max;
		if(amt > impl->bufLen) {
			amt = impl->bufLen;
		}
		//syslog(LOG_DEBUG, "About to memcpy TO client buffer %p FROM memory cursor %p amount %d. Memory buffer has bufLen of %d", 
													//buffer, g_cursor, amt, impl->bufLen);
		memcpy(buffer, g_cursors[slotNo], amt);
		//syslog(LOG_DEBUG, "Memcpy complete, now it's time for some fun....");
		if(amt >= impl->bufLen) {
			memslot_client_reset(impl, slotNo);
			//syslog(LOG_DEBUG, "Read ALL BYTES off buffer, reset memslot back to factory and signaling at txNo %d", impl->txNo);
			if(pthread_cond_signal(&impl->condition) != 0) {
				syslog(LOG_ERR, "Reader condition signal failed!");
			}
		} else {
			//syslog(LOG_DEBUG, "Client underread. Available of %d, but they only want %d. That's ok.", impl->bufLen, amt);
			// Underfulfilled!
			impl->waiting = MEMSLOT_FLAG_UNDERREAD;
			impl->bufLen -= amt;
			g_cursors[slotNo] += amt;
		}
	} else if(impl->bufLen < 0) {
		syslog(LOG_ERR, "Unexpected buffer length of -1 found!");
		amt = -1;
	} else {
		syslog(LOG_DEBUG, "Valid zero-length read from memslot");
	}
	
	pthread_mutex_unlock(&impl->lock);
	//syslog(LOG_DEBUG, "Exiting memslot_receive_impl for slot %d, received %d", slotNo, amt);
	return amt;
	
}
	

int memslot_send_fully(int slotNo, const char *buffer, int len) {
	//syslog(LOG_INFO, "Entering memslot_send_fully for slot %d with len %d", slotNo, len);
	int amtSent = 0;
	if(len == 0) {
		//syslog(LOG_INFO, "memslot_send_fully issuing EOF");
		// Send an EOF
		int rez = memslot_send_impl(slotNo, NULL, 0);
		if(rez < 0) {
			syslog(LOG_ERR, "Memslot has an error sending EOF, will return -1");
			return -1;
		}
		return 0;
	}
	while(amtSent < len) {	
		//syslog(LOG_INFO, "memslot_send_fully at amtSent of %d of %d", amtSent, len);
		int rez = memslot_send_impl(slotNo, buffer + amtSent, len - amtSent);
		if(rez <= 0) {
			syslog(LOG_ERR, "memslot has error sending, will return -1");
			return -1;
		}
		amtSent += rez;
	}
	return amtSent;
}	

int memslot_receive_fully(int slotNo, char *buffer, int max) {
	//syslog(LOG_DEBUG, "Entering memslot_receive_fully for slot %d with a max of %d", slotNo, max);
	int amtRecv = 0;
	int hasEof = 0;
	while(amtRecv < max && hasEof == 0) {
		char *cursedBuf = buffer + amtRecv;
		int remaining = max - amtRecv;
		//syslog(LOG_DEBUG, "ABOUT TO CALL receive_impl with buffer %p and remaining of %d, thus far read %d of %d", cursedBuf, remaining, amtRecv, max); 
		int rez = memslot_receive_impl(slotNo, cursedBuf, remaining);
		//syslog(LOG_DEBUG, "memslot_receive_fully got %d, remaining is %d", rez, remaining - rez);
		if(rez == 0) {
			//syslog(LOG_DEBUG, "memslot_receive_fully got zero-read EOF, breaking");
                        // EOF reached
                        break;
                } else if(rez < 0) {
                        syslog(LOG_ERR, "memslot has error receiving, will return -1");
                        return -1;
                } else if(rez < MEMSLOT_SHARED_BUFFER_SIZE && rez < max) {
			//syslog(LOG_DEBUG, "memslot under-buffer EOF suspected, fashinating");
			hasEof = 1;
		}
                amtRecv += rez;
	}
	//syslog(LOG_DEBUG, "EXITING memslot_receive_fully for slot %d having received %d", slotNo, amtRecv);
	return amtRecv;
}
	



