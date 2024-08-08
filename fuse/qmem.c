#define _GNU_SOURCE

#include <sys/file.h>
#include <syslog.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <alloca.h>
#include <jni.h>
#include "quadric_util_SharedMem.h"
#include "memslot.h"


JNIEXPORT jint JNICALL Java_quadric_util_SharedMem_ninit
  (JNIEnv *env, jobject o, jint i) {
	  return (int) memslot_init_server((int) i);
  }

/*
 * Class:     quadric_util_SharedMem
 * Method:    nclose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_quadric_util_SharedMem_nclose
  (JNIEnv *env, jobject o) {
	  memslot_close();
  }


JNIEXPORT jint JNICALL Java_quadric_util_SharedMem_nset
  (JNIEnv *env, jobject o, jint i, jbyteArray bites) {
	  int len = (*env)->GetArrayLength(env, bites);
	  jboolean noThanks = JNI_FALSE;
	  char *natives = (char*) (*env)->GetByteArrayElements(env, bites, &noThanks);
	  int myRez = memslot_send_fully((int) i, natives, len);
	  (*env)->ReleaseByteArrayElements(env, bites, natives, JNI_ABORT);
	  return myRez;
  }
