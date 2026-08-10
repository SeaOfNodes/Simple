#include <stdint.h>
#include <math.h>
#include <stdio.h>

extern int64_t float_to_int(double);
extern int64_t float_to_u8(double);

int main(void) {
    double values[] = {0.0,-0.0,6.92,-6.92,0.99,-0.99,255.75,-257.75,
        0x1p63,0x1.fffffffffffffp62,-0x1p63,-0x1.0000000000001p63,
        INFINITY,-INFINITY,NAN,-NAN};
    for( unsigned i=0; i<sizeof(values)/sizeof(values[0]); i++ ) {
        double x=values[i];
        int64_t expected=isnan(x) ? 0 : x>=0x1p63 ? INT64_MAX
            : x<=-0x1p63 ? INT64_MIN : (int64_t)x;
        int64_t actual=float_to_int(x), narrow=float_to_u8(x);
        if( actual!=expected || narrow!=(expected&255) ) {
            fprintf(stderr,"conversion of %g: got %lld / %lld, expected %lld / %lld\n",
                    x,(long long)actual,(long long)narrow,
                    (long long)expected,(long long)(expected&255));
            return 1;
        }
    }
    return 0;
}
