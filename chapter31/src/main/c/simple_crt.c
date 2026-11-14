#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <termios.h>
#include <unistd.h>

// Fixed-semantics compiler intrinsics. Keep these definitions for indirect
// calls; direct calls may inline to representation-preserving register moves.
int64_t simple_f64_bits(double value) {
    int64_t bits;
    memcpy(&bits, &value, sizeof(bits));
    return bits;
}
double simple_f64_from_bits(int64_t bits) {
    double value;
    memcpy(&value, &bits, sizeof(value));
    return value;
}

// errno may be thread-local or a macro calling a platform accessor.
int32_t simple_errno(void) { return errno; }
int32_t simple_set_errno(int32_t error) { errno = error; return -1; }
// Export platform errno constants; the buffering and retry loops are Simple.
// C identifiers avoid the macros; linker names match the Simple bindings.
int32_t simple_EINTR __asm__("EINTR") = EINTR;
int32_t simple_EIO __asm__("EIO") = EIO;
int32_t simple_EINVAL __asm__("EINVAL") = EINVAL;
int32_t simple_EOVERFLOW __asm__("EOVERFLOW") = EOVERFLOW;

// Read one stdin byte, without terminal line editing or echo. POSIX terminals
// include Cygwin consoles; redirected files/pipes need no mode change.
// Return 0..255, or -1 with errno set to EOF or an OS error. Control characters,
// including Ctrl-C, are returned as bytes so the mode is restored first.
int32_t simple_getch(void) {
    struct termios saved, mode;
    int terminal = tcgetattr(STDIN_FILENO, &saved) == 0;
    if (!terminal && errno != ENOTTY)
        return -1;
    if (terminal) {
        mode = saved;
        mode.c_lflag &= ~(ICANON | ECHO | ECHONL | ISIG | IEXTEN);
        mode.c_iflag &= ~IXON;
        mode.c_cc[VMIN] = 1;
        mode.c_cc[VTIME] = 0;
        if (tcsetattr(STDIN_FILENO, TCSANOW, &mode) != 0)
            return -1;
    }
    unsigned char byte;
    ssize_t count = read(STDIN_FILENO, &byte, 1);
    int read_errno = errno;
    if (terminal) {
        int restored;
        do {
            restored = tcsetattr(STDIN_FILENO, TCSANOW, &saved);
        } while (restored != 0 && errno == EINTR);
        if (restored != 0)
            return -1;
    }
    if (count < 0) {
        errno = read_errno;
        return -1;
    }
    if (count == 0) {
        errno = EOF;
        return -1;
    }
    return byte;
}

// Simple byte-array layout, used only by the floating-point formatter.
typedef struct SimpleBytes {
    uint32_t len;
    unsigned char bytes[];
} SimpleBytes;

// Binary floating-point conversion is the only native formatting primitive.
// Buffering, integer conversion, string precision and padding live in sys.text.
SimpleBytes *simple_text_float(double value, int64_t spec,
                               int64_t width, int64_t precision) {
    char kind = spec & 255;
    if (!kind) { kind = 'g'; precision = 17; }
    if (!strchr("fFeEgGaA", kind) || (spec & ~8191LL) ||
        width < 0 || width > INT32_MAX || precision < -1 || precision > INT32_MAX)
        abort();
    char fmt[64], *p = fmt;
    *p++ = '%';
    const char *flags = "-+ #0";
    for (int i=0; i<5; i++) if (spec & (256LL<<i)) *p++ = flags[i];
    if (width) p += sprintf(p, "%d", (int)width);
    if (precision >= 0) p += sprintf(p, ".%d", (int)precision);
    *p++ = kind;
    *p = 0;
    int len = snprintf(NULL, 0, fmt, value);
    if (len < 0) abort();
    SimpleBytes *str = malloc(sizeof(*str) + (size_t)len + 1);
    if (!str) abort();
    str->len = (uint32_t)len;
    snprintf((char *)str->bytes, (size_t)len + 1, fmt, value);
    return str;
}
// Simple does not yet dereference raw native pointers. Keep just the argv
// element access here; allocation and array construction live in sys/rt/simple_args.smp.
const char *simple_argv(char **argv, int64_t index) {
    return argv[index];
}

__attribute__((CALL_CONV))
extern void *simple_args(int64_t argc, char **argv);
__attribute__((CALL_CONV))
extern int64_t simple_main(void *args);

int main(int argc, char **argv) {
    // simple_args fills every slot before publishing this as u8[~][].
    void *args = simple_args(argc, argv);
    if (args == NULL)
        return 1;
    // The language-level hidden receiver is not part of the emitted ABI for
    // free functions; source argument zero occupies native argument zero.
    return (int)simple_main(args);
}
