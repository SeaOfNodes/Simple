#include <stdint.h>
#include <stdio.h>
#include <string.h>

typedef struct { uint32_t len; unsigned char bytes[]; } Bytes;
typedef struct { uint32_t len, pad; Bytes *args[]; } Args;

__attribute__((CALL_CONV))
extern Args *simple_args(int64_t argc, char **argv);

const char *simple_argv(char **argv, int64_t index) { return argv[index]; }

int main(void) {
    Args *empty = simple_args(0, NULL);
    if (!empty || empty->len != 0) return 1;
    if (simple_args(-1, NULL) != NULL) return 2;
    if (simple_args(4294967296LL, NULL) != NULL) return 6;

    char name[] = "program", blank[] = "", utf8[] = "\xc3\xa9\xcf\x80";
    char *argv[] = { name, blank, utf8, name };
    Args *args = simple_args(4, argv);
    if (!args || args->len != 4) return 3;
    for (int i = 0; i < 4; i++) {
        if (!args->args[i] || args->args[i]->len != strlen(argv[i]) ||
            memcmp(args->args[i]->bytes, argv[i], args->args[i]->len)) return 4;
    }
    // Copies survive changes to the native buffers, and repeated argv pointers
    // still produce independent Simple arrays.
    name[0] = 'X';
    utf8[0] = 0;
    args->args[0]->bytes[0] = 'Y';
    if (args->args[3]->bytes[0] != 'p' || args->args[2]->bytes[0] != 0xc3) return 5;
    puts("ok");
    return 0;
}
