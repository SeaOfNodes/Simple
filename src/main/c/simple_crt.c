#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// Native process arguments converted to ordinary Simple array layouts.
// The outer array is mutable; every contained byte array is published as
// immutable and non-null to Simple code.
typedef struct SimpleBytes {
    uint32_t len;
    unsigned char bytes[];
} SimpleBytes;

typedef struct SimpleArgs {
    uint32_t len;
    uint32_t pad;
    SimpleBytes *args[];
} SimpleArgs;

__attribute__((CALL_CONV))
extern int64_t simple_main(SimpleArgs *args);

static SimpleArgs *simple_args(int argc, char **argv) {
    if (argc < 0 || (uint64_t)argc > UINT32_MAX)
        return NULL;

    SimpleArgs *args = calloc(1, sizeof(*args) + (size_t)argc * sizeof(args->args[0]));
    if (args == NULL)
        return NULL;
    args->len = (uint32_t)argc;

    for (int i = 0; i < argc; i++) {
        size_t len = strlen(argv[i]);
        if (len > UINT32_MAX) {
            free(args);
            return NULL;
        }
        SimpleBytes *str = malloc(sizeof(*str) + len);
        if (str == NULL) {
            for (int j = 0; j < i; j++) free(args->args[j]);
            free(args);
            return NULL;
        }
        str->len = (uint32_t)len;
        memcpy(str->bytes, argv[i], len);
        args->args[i] = str;
    }
    return args;
}

int main(int argc, char **argv) {
    SimpleArgs *args = simple_args(argc, argv);
    if (args == NULL)
        return 1;
    // The language-level hidden receiver is not part of the emitted ABI for
    // free functions; source argument zero occupies native argument zero.
    return (int)simple_main(args);
}
