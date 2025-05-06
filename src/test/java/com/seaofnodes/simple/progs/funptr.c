#include <assert.h>
#include <stdint.h>

typedef int64_t (*Function)(void);
typedef int64_t (*Printer)(void *, void *);
extern Function factory(int64_t);
extern Printer printer(void);

int main(void) {
    assert(factory(1)() == 42);
    assert(factory(0)() == 43);
    struct { uint32_t length; char data[2]; } text = {2, {'o','k'}};
    printer()(0, &text); // Unused class receiver, then the byte array.
    return 0;
}
