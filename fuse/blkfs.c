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
#include "blkfs.h"
#include "memslot.h"

#define RESERVED_PATH "/mnt/flr/reserved"


struct blkfs_stat {
	int64_t size;
	int64_t type; // 0 is dir, 1 is file, 2 is symlink
	int64_t ts;
};

/* 
 * xDeal with path redirection stuffage
 */


/*
 * Looks for a path that has four slashes and after the fourth slash, there's a digit
 * e.g. /0/14_myvm/55555555/0/inside_the_vole/crappy 
 * would be a yes
 */
int blkfs_is_mapped_dir(const char *path2) {
	char * path = malloc(strlen(path2) +1);
	if(path == NULL) {
		syslog(LOG_ERR, "Malloc failed");
		return 0;
	}
	strcpy(path, path2);
	char *tokin;
	char *save;
	const char delim[2] = "/";
	tokin = strtok_r(path, delim, &save);
	char *diskOffset = NULL;
	int count = 1;
	for(; tokin != NULL; ++count) {
		if(count == 4) {
			diskOffset = tokin;
		} 
		tokin = strtok_r(NULL, delim, &save);
	}
	if(count <= 4) {
		free(path);
		return 0;
	}
	if(diskOffset == NULL) {
		free(path);
		return 0;
	}
	int max = strlen(diskOffset);
	for(int x = 0; x < max; ++x) {
		if(! isdigit(diskOffset[x])) {
			free(path);
			return 0;
		}
	}
	free(path);
	return 1;
}

int blkfs_count_slashes(const char *c, int offsetForSlash, int *offset) {
	int slashCount = 0;
        char *pch = strchr(c, '/');
        while(pch != NULL) {
                slashCount++;
                pch = strchr(pch +1, '/');
                if(slashCount == offsetForSlash) {
                        *offset = pch - c;
                        //printf("Offset is %d\n", *offset);
                }

        }
        return slashCount;
}

char* blkfs_resolve_img_location(const char *path2) {
        int offset = 0;
	int slashCount = blkfs_count_slashes(path2, 4, &offset);
        if(slashCount <= 4) {
                return NULL;
        }
        // Build up image path
        char *imgBase = strndupa(path2, offset);
        char *ext = ".rnd";
        char *imgLoc =  NULL;
        int rez = asprintf(&imgLoc, "%s%s", imgBase, ext);
	if(rez == -1) {
 		syslog(LOG_ERR, "asprintf failed");
                return NULL;
	}
        //syslog(LOG_INFO, "Path %s resolves to img location %s", path2, imgLoc);
	return imgLoc;
}

int blkfs_trigger_remap(const char *path2, int resolve) {
        char *coolGuy = NULL;
	if(resolve) {
		coolGuy = blkfs_resolve_img_location(path2);
	        if(coolGuy == NULL) {
        	        return 0;
	        }
	} else {
		coolGuy = (char*) path2;
	}
	syslog(LOG_INFO, "Triggering refresh on img path %s", coolGuy);
	char trivial[1];
	int rez = blkfs_do_read(coolGuy, trivial, 1, 0);
	if(rez < 0) {
		syslog(LOG_ERR, "Attempt to freshen img path at %s failed", coolGuy);
	}
	if(resolve) {
		free(coolGuy);
	}
	return 1;
}

char* blkfs_get_mapped_path(const char *path2) {
	char * path = malloc(strlen(path2) +1);
	if(path == NULL) {
		syslog(LOG_ERR, "Malloc failed");
		return NULL;
	}
	strcpy(path, path2);
	FILE *stream;
	char *buf;
	size_t sz;
	stream = open_memstream(&buf, &sz);
	char *tokin;
	char *save;
	const char delim[2] = "/";
	tokin = strtok_r(path, delim, &save);
	if(tokin == NULL) {
		goto CLEANUP;	
	}
	// Start with /mnt/flr/SITE_
	fprintf(stream, "/mnt/flr/%s_", tokin);
	// Now append vault id
	tokin = strtok_r(NULL, delim, &save);
	if(tokin == NULL) {
		goto CLEANUP;
	}
	for(int x = 0; x < 10; ++x) {
		if(isdigit(tokin[x])) {
			fputc(tokin[x], stream);
		} else {
			break;
		}
	}
	// Now do version
	tokin = strtok_r(NULL, delim, &save);
	if(tokin == NULL) {
		goto CLEANUP;
	}
	fprintf(stream, "_%s_", tokin);
	// Now do disk
	tokin = strtok_r(NULL, delim, &save);
	if(tokin == NULL) {
		goto CLEANUP;
	}
	fputs(tokin, stream);
	
	// Append anything left
	tokin = strtok_r(NULL, "", &save);
	if(tokin != NULL) {
		fprintf(stream, "/%s", tokin);
	}
	// Always close the stream
	CLEANUP:
	fclose(stream);
	free(path);
	char *realPath = NULL;
	realPath = malloc(PATH_MAX +1);
	if(realPath == NULL) {
		syslog(LOG_ERR, "Malloc failed");
		return buf;
	}
	if(realpath(buf, realPath)) {
		// Symlink is missing--nothing here at all
		free(buf);
		return realPath;
	} else {
		int hasTimeout = 0;
		// Try to remap the sucker, if possible
		if(blkfs_trigger_remap(path2, 1)) {
			for(int x = 0; x < 20; ++x) {
				if(realpath(buf, realPath)) {
					free(buf);
					return realPath;
				}
				// 100ms
				usleep(100000);
			}
			hasTimeout = 1;
		} else if(hasTimeout) {
			syslog(LOG_ERR, "Attempted to refresh and remount FLR but timed out for %s", path2);
		} else {
			//syslog(LOG_INFO, "Path %s isn't an img path", path2);
		} 
		// Fall out with nothing
		free(realPath);
                free(buf);
		return NULL;

	}
}



int blkfs_mapped_release(const char *path, struct fuse_file_info *fi)
{
	(void) path;
	close(fi->fh);
	return 0;
}

int blkfs_mapped_getattr(const char *path, struct stat *stbuf)
{
	int res;
	if(path == NULL) {
		res = lstat(RESERVED_PATH, stbuf);
	} else {
		res = lstat(path, stbuf);
	}
	stbuf->st_mode = stbuf->st_mode | 0555;
	if (res == -1)
		return -errno;

	return 0;
}

int blkfs_mapped_readlink(const char *path, char *buf, size_t size)
{
	if(path == NULL) {
		return -EPIPE;
	}
	int res;

	res = readlink(path, buf, size - 1);
	if (res == -1)
		return -errno;

	buf[res] = '\0';
	return 0;
}


int blkfs_mapped_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
		       off_t offset, struct fuse_file_info *fi)
{
	DIR *dp;
	struct dirent *de;

	(void) offset;
	(void) fi;
	//(void) flags;
	if(path == NULL) {
		dp = opendir(RESERVED_PATH);
	} else {
		dp = opendir(path);
	}
	if (dp == NULL)
		return -errno;

	while ((de = readdir(dp)) != NULL) {
		struct stat st;
		memset(&st, 0, sizeof(st));
		st.st_ino = de->d_ino;
		st.st_mode = de->d_type << 12;
		if (filler(buf, de->d_name, &st, 0))
			break;
	}

	closedir(dp);
	return 0;
}

int blkfs_mapped_open(const char *path, struct fuse_file_info *fi)
{
	if(path == NULL) {
		return -EPIPE;
	}
	int res;

	res = open(path, fi->flags);
	if (res == -1)
		return -errno;

	fi->fh = res;
	return 0;
}

int blkfs_mapped_read(const char *path, char *buf, size_t size, off_t offset,
		    struct fuse_file_info *fi)
{
	if(path == NULL) {
		return -EPIPE;
	}
	int fd;
	int res;

	if(fi == NULL)
		fd = open(path, O_RDONLY);
	else
		fd = fi->fh;
	
	if (fd == -1)
		return -errno;

	res = pread(fd, buf, size, offset);
	if (res == -1)
		res = -errno;

	if(fi == NULL)
		close(fd);
	return res;
}


/********************************* End mapped stuff */



int blkfs_getattr(const char *path, struct stat *stbuf)
{  
	//syslog(LOG_INFO, "Entering getattr for path (mapped) at %s", path);	
	 if(blkfs_is_mapped_dir(path)) {
                char *path2 = blkfs_get_mapped_path(path);
                int rezzy = blkfs_mapped_getattr(path2, stbuf);
		int offset = 0;
		int slashCount = blkfs_count_slashes(path, 4, &offset);
		if(path2 == NULL && slashCount > 4) {
			//syslog(LOG_INFO, "Getattr will void this guy, it looks trashy");
			rezzy = -ENOENT;
		} 
		//syslog(LOG_INFO, "Delegated getattr for subpath %s with slashcount %d", path2, slashCount);
                free(path2);
                return rezzy;
        }


	//syslog(LOG_INFO, "Entering getattr for path %s", path);
	uint32_t txNum = blkfs_get_next_datafile();
	if(txNum == G_NULL_PIPE) {
		syslog(LOG_ERR, "blkfs_getattr timed out waiting on free transit");
		// Timed out waiting on stuff
		return -EBUSY;
	}
	struct blkfs_record rec= { 
		.clientId = 0,
		.offset = 0,
		.txId = txNum,
		.pathLen = strlen(path),
		.length = 0,
		.command = BLKFS_COMMAND_ATTR,
		.increment = 0
	};
	memslot_pre_receive(txNum);	
	int retVal = blkfs_write_control_message(&rec, path);
	if(retVal == -1) {
		//blkfs_unlink_datafile(txNum);
		return -errno;
	}
	if(retVal < 0) {
		blkfs_unlink_datafile(txNum);
		return -EPIPE;
	} 
	
	struct blkfs_stat myStat;
	retVal = memslot_receive_fully(txNum, (char*) &myStat, sizeof(struct blkfs_stat));
	blkfs_unlink_datafile(txNum);
	
	// Check for errors after read
	if(retVal == -1) {
		return -errno;
	}
	if(retVal == 0) {
		syslog(LOG_ERR, "blkfs_getattr: memslot_receive_fully returned a zero-size read, but should not do so!");
		return -EPIPE;
	}
	if(retVal != sizeof(struct blkfs_stat)) {
		syslog(LOG_ERR, "blkfs_getattr: memslot_receive_fully overread of %d", retVal);
		return -EPIPE;
	}
	
	// Did they try to stat something that doesn't exist?
	if(myStat.type == -1) {
		//syslog(LOG_ERR, "blkfs_getattr: myStat type is -1, will return error ENOENT");
		errno = ENOENT;
		return -errno;
	}
	
	memset(stbuf, 0, sizeof(struct stat));
	if(myStat.type == 0) {
		//syslog(LOG_DEBUG, "regular file of size %lu and ts %lu", myStat.size, myStat.ts);
		//stbuf->st_mode = S_IFREG | 0666;
		stbuf->st_mode = S_IFREG | 0444;
		stbuf->st_nlink = 1;
		stbuf->st_size = myStat.size;
		stbuf->st_mtime = myStat.ts;
	} else if(myStat.type == 1) {
		//stbuf->st_mode = S_IFDIR | 0755;
		stbuf->st_mode = S_IFDIR | 0555;
		stbuf->st_nlink = 2;
		stbuf->st_size = 4096;
		stbuf->st_mtime = myStat.ts;
	} else if(myStat.type == 2) {
		// Symlink
		stbuf->st_mode = S_IFLNK | 0555;
		stbuf->st_nlink = 1;
		stbuf->st_size = myStat.size;
		stbuf->st_mtime = myStat.ts;
	} else {
		syslog(LOG_ERR, "Stat found unknown structure from Data Engine, will return an error to filesystem");
		errno = EPIPE;
		return -errno;
	}
	//syslog(LOG_DEBUG, "blkfs_getattr returning 0");	
	return 0;
    
}

int blkfs_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
                         off_t offset, struct fuse_file_info *fi)
{
	if(blkfs_is_mapped_dir(path)) {
		char *path2 = blkfs_get_mapped_path(path);
		if(path2 == NULL) {
			// Special case--remap after a failure to resolve in the 
			// mnt/restore/X/VM/date/0/ directory that should show partitions
			int offset = 0;
			int slashCount = blkfs_count_slashes(path, 4, &offset);
			if(slashCount == 4) {
				// Append p0 to the path to trigger remount
 				char *imgLoc =  NULL;
			        int rez = asprintf(&imgLoc, "%s%s", path, "/p0");
			        if(rez == -1) {
			                syslog(LOG_ERR, "asprintf failed");
			                return -1;
        			}	
				//syslog(LOG_INFO, "blkfs_readdir triggering remap for path %s", imgLoc);
				char *tmpFace = blkfs_get_mapped_path(imgLoc);
				free(tmpFace);
				free(imgLoc);
				// Retry
				path2 = blkfs_get_mapped_path(path);
			}
		}
		int rezzy = blkfs_mapped_readdir(path2, buf, filler, offset, fi);
		free(path2);
		return rezzy;
	}
	//syslog(LOG_INFO, "Entering blkfs_readdir for path %s", path);
	
	int count = 0;
	
	uint32_t txNum = blkfs_get_next_datafile();
	if(txNum == G_NULL_PIPE) {
		// Timed out waiting on shit
		return -EBUSY;
	}
	
	struct blkfs_record rec= { 
		.clientId = 0,
		.offset = 0,
		.txId = txNum,
		.pathLen = strlen(path),
		.length = 0,
		.command = BLKFS_COMMAND_LIST,
		.increment = 0
	};
	memslot_pre_receive(txNum);
		
	int retVal = blkfs_write_control_message(&rec, path);
	if(retVal == -1) {
		syslog(LOG_ERR, "blkfs_readdir had an error writing control message, will return an error");
		blkfs_unlink_datafile(txNum);
		return -errno;
	}
	
	while(1) {
		char *dirMe = 0;
		retVal = blkfs_read_var_pipe_entry_string(txNum, &dirMe);
		if(retVal == -1 || retVal == 0) {
			break;
		}
		if(filler(buf, dirMe, NULL, 0) != 0) {
			errno = -ENOMEM;
			retVal = -1;
			break;
		}
		count++;
		free(dirMe);
	}
	blkfs_unlink_datafile(txNum);
	if(count > 0) {
		// If there's nothing here, there's nothing here...
		filler(buf, ".", NULL, 0);
		filler(buf, "..", NULL, 0);
	}
	
	
	//syslog(LOG_DEBUG, "blkfs_readdir found %d items", count);
	if(retVal == -1) {
		// TODO: This isn't technically correct, since errno 
		// can be overrided during a bunch of calls above
		return -errno;
	}
	return 0;
}

int blkfs_open(const char *path, struct fuse_file_info *fi) {
	//syslog(LOG_DEBUG, "Entering blkfs_open for path %s", path);
	 if(blkfs_is_mapped_dir(path)) {
                char *path2 = blkfs_get_mapped_path(path);
                int rezzy = blkfs_mapped_open(path2, fi);
                free(path2);
                return rezzy;
	}
	    
    return 0;
}

int blkfs_release(const char *path, struct fuse_file_info *fi)
{
	 if(blkfs_is_mapped_dir(path)) {
                char *path2 = blkfs_get_mapped_path(path);
                int rezzy = blkfs_mapped_release(path2, fi);
                free(path2);
                return rezzy;
        }

	return 0;
}



int blkfs_read(const char *path, char *buf, size_t size, off_t offset,
                    struct fuse_file_info *fi) {
	 if(blkfs_is_mapped_dir(path)) {
                char *path2 = blkfs_get_mapped_path(path);
                int rezzy = blkfs_mapped_read(path2, buf, size, offset, fi);
                free(path2);
                return rezzy;
        }

	int rez  = blkfs_do_read(path, buf, size, offset);
	return rez;
			}	

int blkfs_write(const char *path, const char *buf, size_t size, off_t offset,
                    struct fuse_file_info *fi)
{
    syslog(LOG_INFO, "Entering blkfs_write for path %s", path);
	return 0;
	//return pwrite((int)fi->fh, buf, size, offset);
}

int blkfs_truncate(const char *path, off_t size) 
{
	syslog(LOG_INFO, "Entering blkfs_truncate for path %s", path);
    return 0;
}


int blkfs_chown(const char *path, uid_t size, gid_t gid)
{
	syslog(LOG_INFO, "Entering blkfs_chown for path %s", path);
    return 0;
}

int blkfs_chmod(const char *path, mode_t mode)
{
	syslog(LOG_INFO, "Entering blkfs_chmod for path %s", path);
    return 0;
}

int blkfs_utime(const char *path, struct utimbuf *time)
{
	//syslog(LOG_INFO, "Entering blkfs_utime for path %s", path);
    return 0;
}

int blkfs_flush(const char *path, struct fuse_file_info *finfo)
{
	//syslog(LOG_INFO, "Entering blkfs_flush for path %s", path);
    return 0;
}

int blkfs_readlink(const char *path, char *dest, size_t size) {
	 if(blkfs_is_mapped_dir(path)) {
                char *path2 = blkfs_get_mapped_path(path);
                int rezzy = blkfs_mapped_readlink(path2, dest, size);
                free(path2);
                return rezzy;
        }

	//syslog(LOG_INFO, "Entering blkfs_readlink for path %s and size of %zu", path, size);
	uint32_t txNum = blkfs_get_next_datafile();
	if(txNum == G_NULL_PIPE) {
		// Timed out waiting on shit
		return -EBUSY;
	}
	struct blkfs_record rec= { 
		.clientId = 0,
		.offset = 0,
		.txId = txNum,
		.pathLen = strlen(path),
		.length = 0,
		.command = BLKFS_COMMAND_FOLLOW_LINK,
		.increment = 0
	};
	memslot_pre_receive(txNum);
	int retVal = blkfs_write_control_message(&rec, path);
	if(retVal == -1) {
		blkfs_unlink_datafile(txNum);
		return -errno;
	}
	
	// Get the symlink dest and copy it off the pipe 
	// and into the caller's buffer
	retVal = memslot_receive_fully(txNum, dest, size-1);

	if(retVal > 0) {
		dest[retVal] = '\0';
		retVal = 0;
	}
	blkfs_unlink_datafile(txNum);
	return retVal;
	
}


int blkfs_create(const char *path, mode_t mode, struct fuse_file_info *fi) 
{
	syslog(LOG_INFO, "Entering blkfs_create for %s", path);
	errno = EROFS;
	return -errno;
}

// TODO: Guy on internets says int my_readlink(const char *path, char *buf, size_t size) is needed for teh symbolic linx
// ....but FUSE ancient.history doesn't no have dems apples

struct fuse_operations fops= {
    .open       = blkfs_open,
    .read       = blkfs_read,
    .write      = blkfs_write,
    .release    = blkfs_release,
    .readdir    = blkfs_readdir,
    .getattr    = blkfs_getattr,
    .truncate     = blkfs_truncate,
    .chmod      =blkfs_chmod,
    .chown      =blkfs_chown,
    .utime      =blkfs_utime,
    .flush      =blkfs_flush,
	.readlink	=blkfs_readlink,
	.create = blkfs_create,
};

	
void blkfs_test_thing(const char *fucko) {
	/* printf("Test string is %s\n", fucko);
        if(blkfs_is_mapped_dir(fucko)) {
                printf("Its a mapped dir bro: ");
                char *coolguy = blkfs_get_mapped_path(fucko);
                printf("%s", coolguy);
                printf("\n");
                free(coolguy);
        } else {
                printf("WTF\n");
        } */
	char *coolGuy = blkfs_resolve_img_location(fucko);
	if(coolGuy == NULL) {
		printf("Could not resolve img location for %s\n", fucko);
	} else {
		printf("AAAHHH satisfaction %s -> %s\n", fucko, coolGuy);
	}
}


int mainer(int argc, char **argv) {
	blkfs_test_thing(argv[1]);
	return 0;
}

int main(int argc, char **argv) {
	openlog("restorefs", LOG_PID|LOG_CONS, LOG_USER);
	syslog(LOG_INFO, "Alike Restore Fuse initializing");
	mkdir(RESERVED_PATH, 0700);
	int rez = blkfs_init(1);
	if(rez != 0) {
		return -1;
	}
    
    	int retVal = fuse_main(argc, argv, &fops, 0);
	blkfs_destroy();
	
	return retVal;
}

