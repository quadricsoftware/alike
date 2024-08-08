GLIB_CFLAGS 	:= $(shell pkg-config --cflags glib-2.0)
FUSE_CFLAGS	:= -D_FILE_OFFSET_BITS=64 -DFUSE_USE_VERSION=30
CFLAGS		:= -pthread -Wall $(GLIB_CFLAGS) $(FUSE_CFLAGS)
LIBS		:= $(shell pkg-config --libs glib-2.0) -lfuse
JAVA_HOME 	:= /usr/lib/jvm/java-8-oracle
LIBS2			:= -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

all: blkfs jni jni2 blkfs_shared
blkfs:blkfs.c
	gcc $(CFLAGS) blkfs.c blkfs_shared.c memslot.c -o blkfs $(LIBS)
jni:qjni.c
	gcc -shared -fpic $(CFLAGS) qjni.c -o qjni.so $(LIBS2)
jni2: qmem.c 
	gcc -shared -fpic $(CFLAGS) qmem.c memslot.c blkfs_shared.c -o qmem.so $(LIBS2)
blkfs_shared:blkfs_shared.c
	gcc -shared -fpic $(CFLAGS) blkfs_shared.c memslot.c -o blkfs_shared.so -z defs -export-dynamic -lglib-2.0
clean:
	rm -f blkfs;rm -f ambfs; rm -f qjni.so
