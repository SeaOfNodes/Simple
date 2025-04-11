#include <stdio.h>
#include <math.h>
#include <stdlib.h>

__attribute__ ((CALL_CONV))
extern double test_sqrt(double);

int main( int argc, char** argv ) {
  double epsilon = 1e-50;
  for( int i=0; i<10; i++ ) {
    double d = test_sqrt(i), expect = sqrt(i);
    double delta = abs(d-expect);
    printf("%d  %f\n",i,d);
    if( delta > 1e-50 )
      return 1;
  }
  return 0;
}
