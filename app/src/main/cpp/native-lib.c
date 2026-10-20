#include <jni.h>
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "Sacuma"

/*
 * JNI naming rule: Java_<package with _ for .>_<class>_<method>.
 * This one backs `external fun nativeHello()` in MainActivity.
 *
 * As a first sanity check it also tries to open /dev/uinput, which is
 * what milestone 3 depends on.
 */
JNIEXPORT jstring JNICALL
Java_io_github_aziztw_sacuma_MainActivity_nativeHello(JNIEnv *env, jobject thiz) {
    (void) thiz;

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    const char *uinput = fd >= 0 ? "OK" : "not accessible";
    if (fd >= 0) close(fd);

    char msg[128];
    snprintf(msg, sizeof msg, "Hello from C (uid %d)\n/dev/uinput: %s", getuid(), uinput);
    __android_log_print(ANDROID_LOG_INFO, TAG, "%s", msg);

    return (*env)->NewStringUTF(env, msg);
}
