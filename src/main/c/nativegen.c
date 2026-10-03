#include <stdio.h>
#include <jni.h>

#include "slobben_cells_service_NativeGeneration.h"
JNIEXPORT void JNICALL Java_slobben_cells_service_NativeGeneration_generate(JNIEnv *env, jobject obj) {
    printf("👋 Hello from C code!\n");
}
