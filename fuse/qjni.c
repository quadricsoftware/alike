#define _GNU_SOURCE

#include <sys/file.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <alloca.h>
#include <jni.h>
#include "quadric_util_Flocker.h"



JNIEXPORT jint JNICALL Java_quadric_util_Flocker_getNativeLock
  (JNIEnv *env, jobject o, jbyteArray array) {
	int len = (*env)->GetArrayLength (env, array);
	char* myPath = alloca(len +1);
	(*env)->GetByteArrayRegion(env, array, 0, len, (jbyte*) myPath);
	myPath[len] =  '\0';
	int foo = open(myPath, O_WRONLY |O_CREAT, S_IRWXU);
	if(foo == -1) {
		return 0;
	}
	if(flock(foo, LOCK_EX | LOCK_NB) != 0) {
		close(foo);
		return 0;
	}
	return foo;
	
	
  }


JNIEXPORT void JNICALL Java_quadric_util_Flocker_closeNativeLock
  (JNIEnv *env, jobject o, jint guy) {
	  int phil = (int) guy;
	  flock(phil, LOCK_UN);
	  close(phil);
  }