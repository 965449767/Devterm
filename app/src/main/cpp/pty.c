#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <errno.h>
#include <termios.h>
#include <signal.h>

#define LOG_TAG "DevTerm-PTY"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int master_fd = -1;
static pid_t child_pid = -1;
static int g_cols = 80;
static int g_rows = 24;
static char** g_env = NULL;
static char* g_working_dir = NULL;
static char** g_command = NULL;
static int g_command_len = 0;

static int set_window_size(int fd, int cols, int rows) {
    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    return ioctl(fd, TIOCSWINSZ, &ws);
}

static int create_pty() {
    int ptmx_fd;
    char pts_name[64];
    int slave_fd;

    ptmx_fd = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (ptmx_fd < 0) {
        LOGE("open /dev/ptmx failed: %s", strerror(errno));
        return -1;
    }

    if (grantpt(ptmx_fd) < 0) {
        LOGE("grantpt failed: %s", strerror(errno));
        close(ptmx_fd);
        return -1;
    }

    if (unlockpt(ptmx_fd) < 0) {
        LOGE("unlockpt failed: %s", strerror(errno));
        close(ptmx_fd);
        return -1;
    }

    if (ptsname_r(ptmx_fd, pts_name, sizeof(pts_name)) != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(ptmx_fd);
        return -1;
    }

    slave_fd = open(pts_name, O_RDWR | O_NOCTTY);
    if (slave_fd < 0) {
        LOGE("open slave %s failed: %s", pts_name, strerror(errno));
        close(ptmx_fd);
        return -1;
    }

    return ptmx_fd;
}

static void set_slave_terminal(int slave_fd) {
    struct termios term;
    tcgetattr(slave_fd, &term);

    term.c_lflag |= (ICANON | ECHO | ECHOE | ISIG);
    term.c_iflag |= (BRKINT | ICRNL | IXON | IXOFF);
    term.c_oflag |= (OPOST | ONLCR);
    term.c_cflag |= (CS8 | CLOCAL | CREAD);

    term.c_cc[VINTR] = '\003';
    term.c_cc[VQUIT] = '\034';
    term.c_cc[VERASE] = '\177';
    term.c_cc[VKILL] = '\025';
    term.c_cc[VEOF] = '\004';
    term.c_cc[VTIME] = 0;
    term.c_cc[VMIN] = 1;
    term.c_cc[VSWTC] = '\0';
    term.c_cc[VSTART] = '\021';
    term.c_cc[VSTOP] = '\023';
    term.c_cc[VSUSP] = '\032';
    term.c_cc[VEOL] = '\0';
    term.c_cc[VREPRINT] = '\022';
    term.c_cc[VDISCARD] = '\017';
    term.c_cc[VWERASE] = '\027';
    term.c_cc[VLNEXT] = '\036';
    term.c_cc[VEOL2] = '\0';

    tcsetattr(slave_fd, TCSANOW, &term);
}

JNIEXPORT jboolean JNICALL
Java_com_devterm_terminal_PtyBackend_nativeCreatePty(JNIEnv *env, jobject thiz) {
    int slave_fd;
    pid_t pid;

    slave_fd = create_pty();
    if (slave_fd < 0) {
        LOGE("create_pty failed");
        return JNI_FALSE;
    }

    master_fd = slave_fd;

    set_slave_terminal(slave_fd);

    set_window_size(slave_fd, g_cols, g_rows);

    pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master_fd);
        master_fd = -1;
        return JNI_FALSE;
    }

    if (pid == 0) {
        setsid();

        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);

        close(slave_fd);
        close(master_fd);

        if (g_working_dir != NULL && chdir(g_working_dir) != 0) {
            exit(1);
        }

        execve(g_command[0], g_command, g_env);

        exit(127);
    }

    child_pid = pid;

    close(slave_fd);

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_devterm_terminal_PtyBackend_nativeSetWindowSize(JNIEnv *env, jobject thiz,
                                                         jint fd, jint cols, jint rows) {
    if (fd >= 0) {
        set_window_size(fd, cols, rows);
    }
}

JNIEXPORT void JNICALL
Java_com_devterm_terminal_PtyBackend_nativeKillChild(JNIEnv *env, jobject thiz, jint pid) {
    if (pid > 0) {
        kill(pid, SIGTERM);
    }
}

JNIEXPORT void JNICALL
Java_com_devterm_terminal_PtyBackend_nativeClosePty(JNIEnv *env, jobject thiz, jint fd) {
    if (fd >= 0) {
        close(fd);
    }
}

JNIEXPORT jint JNICALL
Java_com_devterm_terminal_PtyBackend_nativeWaitForChild(JNIEnv *env, jobject thiz, jint pid) {
    if (pid <= 0) {
        return -1;
    }

    int status;
    waitpid(pid, &status, 0);

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }

    return -1;
}

JNIEXPORT void JNICALL
Java_com_devterm_terminal_PtyBackend_nativeInitialize(JNIEnv *env, jobject thiz,
                                                       jobjectArray command,
                                                       jobjectArray env_vars,
                                                       jstring working_dir,
                                                       jint cols, jint rows) {
    int i, len;

    g_cols = cols;
    g_rows = rows;

    len = (*env)->GetArrayLength(env, command);
    g_command_len = len;
    g_command = (char**)malloc((len + 1) * sizeof(char*));
    for (i = 0; i < len; i++) {
        jstring str = (jstring)(*env)->GetObjectArrayElement(env, command, i);
        const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
        g_command[i] = strdup(cstr);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
        (*env)->DeleteLocalRef(env, str);
    }
    g_command[len] = NULL;

    if (env_vars != NULL) {
        len = (*env)->GetArrayLength(env, env_vars);
        g_env = (char**)malloc((len + 1) * sizeof(char*));
        for (i = 0; i < len; i++) {
            jstring str = (jstring)(*env)->GetObjectArrayElement(env, env_vars, i);
            const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
            g_env[i] = strdup(cstr);
            (*env)->ReleaseStringUTFChars(env, str, cstr);
            (*env)->DeleteLocalRef(env, str);
        }
        g_env[len] = NULL;
    } else {
        g_env = NULL;
    }

    if (working_dir != NULL) {
        const char* cstr = (*env)->GetStringUTFChars(env, working_dir, NULL);
        g_working_dir = strdup(cstr);
        (*env)->ReleaseStringUTFChars(env, working_dir, cstr);
    } else {
        g_working_dir = NULL;
    }
}

JNIEXPORT void JNICALL
Java_com_devterm_terminal_PtyBackend_nativeCleanup(JNIEnv *env, jobject thiz) {
    int i;

    if (g_command != NULL) {
        for (i = 0; i < g_command_len; i++) {
            free(g_command[i]);
        }
        free(g_command);
        g_command = NULL;
    }

    if (g_env != NULL) {
        for (i = 0; g_env[i] != NULL; i++) {
            free(g_env[i]);
        }
        free(g_env);
        g_env = NULL;
    }

    if (g_working_dir != NULL) {
        free(g_working_dir);
        g_working_dir = NULL;
    }

    if (master_fd >= 0) {
        close(master_fd);
        master_fd = -1;
    }
}
