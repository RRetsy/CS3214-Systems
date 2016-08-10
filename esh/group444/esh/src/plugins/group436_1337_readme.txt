###################################################################################
# NOTE: The test file may need to be located in the src directory to run          #
#       depnding on the way you call the test driver                              #
#                                                                                 #
#		I ran with '~cs3214/bin/stdriver.py -p plugins plugins.tst' from my src   #
#		directory with the plugins.tst also in the src directory and plugin.so    #
#		file in the plugins directory											  #
###################################################################################

Plugin Name: 1337

Functionality: This plugin takes any amount of input arguments as a "sentence" and 
prints the sentence converted to 1337 speak. ]-[0|D3 '/0|_| 1!|{3 !7!

To use: 
1. To call from in your program:
	plugin->process_builtin(/* esh_command */ commands)

2. From the command line:

esh> 1337 hello world
]-[3110 \/\/0|21o|
esh> 1337 abcdefghijklmnopqrstuvwxyz
@|3<o|3ph6]-[!_||{1/v\/\/0|Dkw|2$7|_|\/\/\/><'/2 
esh> 1337
/v\|_|$7 !/\/|D|_|7 @o|o|!7!0/\/@1 @|26|_|/v\3/\/7$ (must input additional arguments)


***NOTE: the leet is spelled one three three seven 
