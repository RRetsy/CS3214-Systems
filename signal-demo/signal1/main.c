#include "rngs.h"
#include <stdio.h>

#define WIDTH 100.0
#define HEIGHT 40000.0

double func(double x){
  return 3.0*x*x + 2.0*x;
}

int above, below;
double est;


void main() {

double x,y,z;

  //initialize the random number generator
  PlantSeeds((double)-1.0);
  SelectStream(0);

 above = 0;
 below = 0;

 while(1) {

   x = Random()*WIDTH;
   y = Random()*HEIGHT;

   z = func(x);

   if (y > z) above++;
   else below++;

   est = HEIGHT*WIDTH*below;
   est = est/((double)above + (double)below);
   if((above+below)%10000==0) 
      printf("%d %e \n", above+below, est);

 }
}
