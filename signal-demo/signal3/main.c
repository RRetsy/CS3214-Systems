#include "rngs.h"
#include <stdio.h>
#include "esh-sys-utils.h"


#define WIDTH 100.0
#define HEIGHT 40000.0

int above, below;
double est;


void alarm_handler(int sig, siginfo_t* info, void* context) {
  printf("received %d \n", sig);
  printf("above: %d below: %d estimate: %g \n", above, below, est);
  alarm(1);
}

double func(double x){
  return 3.0*x*x + 2.0*x;
}



void main() {

double x,y,z;
  
  // identify the handler for the terminal STOP signal
  esh_signal_sethandler(SIGALRM, alarm_handler);


  //initialize the random number generator
  PlantSeeds((double)-1.0);
  SelectStream(0);

 above = 0;
 below = 0;

 alarm(1);

 while(1) {

   x = Random()*WIDTH;
   y = Random()*HEIGHT;

   z = func(x);

   if (y > z) above++;
   else below++;

   est = HEIGHT*WIDTH*below;
   est = est/((double)above + (double)below);

 }
}
