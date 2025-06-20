#include <stdio.h>
#include <math.h>
#include <stdlib.h>

extern double newtonFloat(double);

int main( int argc, char** argv ) {
  for( int i=0; i<10; i++ ) {
    double d = newtonFloat((double)i), expect = sqrt((double)i);
    double delta = fabs(d-expect);
    printf("%d  %f   (%g)\n",i,d,delta);
    if( delta > 1e-15 )
      return 1;
  }
  return 0;
}
