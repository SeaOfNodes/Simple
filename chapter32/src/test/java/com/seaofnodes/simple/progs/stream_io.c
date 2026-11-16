#include <errno.h>
#include <stdint.h>
#include <stddef.h>
#include <unistd.h>

// Deterministic short I/O and failures; ordinary descriptors still use libc.
extern ssize_t __real_read(int, void *, size_t);
extern ssize_t __real_write(int, const void *, size_t);
static int mode, calls, bad;
static int64_t count, offset;

int32_t test_io_mode(int32_t next, int64_t start) {
    mode = next;
    calls = bad = 0;
    count = 0;
    offset = start;
    return 0;
}
int64_t test_io_count(void) { return count; }
int32_t test_io_status(void) {
    return bad || (mode == 5 && (errno != EAGAIN || calls != 1));
}

ssize_t __wrap_read(int fd, void *buffer, size_t size) {
    if (fd != 700) return __real_read(fd, buffer, size);
    ++calls;
    if (mode == 1 && calls == 1) { errno = EINTR; return -1; }
    if (mode == 2 && calls > 1) { errno = EIO; return -1; }
    if (count == 24593) return 0;
    size_t n = size < 137 ? size : 137;
    if (n > (size_t)(24593-count)) n = (size_t)(24593-count);
    for (size_t i=0; i<n; ++i) ((unsigned char *)buffer)[i] = (count+i)%251;
    count += n;
    return (ssize_t)n;
}

ssize_t __wrap_write(int fd, const void *buffer, size_t size) {
    if (fd != 701) return __real_write(fd, buffer, size);
    ++calls;
    if (mode == 1 && calls == 1) { errno = EINTR; return -1; }
    if (mode == 3 && calls > 1) { errno = EIO; return -1; }
    if (mode == 4) return 0;
    if (mode == 5) { errno = EAGAIN; return -1; }
    size_t n = size < 7 ? size : 7;
    for (size_t i=0; i<n; ++i)
        if (((const unsigned char *)buffer)[i] != (offset+count+i)%251) bad = 1;
    count += n;
    return (ssize_t)n;
}
