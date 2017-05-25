#include "stdio.h"

void assertMsg(int arg, char* msg) {
    if (arg == 0) {
        printf("%s\n", msg);
        exit(-1);
    }
}

int tryOpen(char* fileName) {
    int fd;

    fd = open(fileName);
    close(fd);
    if (fd == -1)
        return 0;
    else
        return 1;
}

int forkCreat(char* fileName) {
    char* excutable;
    char* argv[2];
    int pid, argc;

    excutable = "testCreat.coff";
    argc = 2;
    argv[0] = excutable;
    argv[1] = fileName;

    pid = exec(excutable, narg, argc);

    return pid;
}

int forkOpen(char* fileName) {
    char* excutable;
    char* argv[2];
    int pid, argc;

    excutable = "testOpen.coff";
    argc = 2;
    argv[0] = excutable;
    argv[1] = fileName;

    pid = exec(excutable, narg, argc);

    return pid;
}

int main(int argc, char* argv[]) {
	char* fileName = "testUnlink.txt";
    int pid, fd[10], i;

    pid = forkCreat(fileName);
    assertMsg(pid != -1, "Not create\n");

    for (i = 0; i < 9; i++) {
        fd[i] = open(fileName);
        assertMsg(fd[i] == -1, "Not open\n");
    }

    for (i = 0; i < 3; i++) {
        pid = forkOpen(fileName);
        assertMsg(pid != -1, "Failed to fork thread to open\n");
    }

    assertMsg(unlink(fileName) == 0, "Unable to unlink\n");
    assertMsg(tryOpen(fileName), "Can still open after unlink\n");

    assertMsg(unlink(fileName) == 0, "Failed when issue unlink twice before actually deteting\n");
    printf("passed\n");
}