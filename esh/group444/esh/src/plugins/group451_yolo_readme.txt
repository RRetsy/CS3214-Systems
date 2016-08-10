Group Number
------------
Group 451

Student Information
-------------------
Elmer Landaverde	pid: elmer@vt.edu
Scott Scott			pid: ssines@vt.edu

How to execute the plugin
-------------------------

The yolo plugin is executed as follows:

yolo <input_string>

First write "yolo" which is the name of the plugin. Next the user has the option
to specify an input_string for the function.

Description of Functionality
----------------------------

The plugin will take the user's input_string (default input_string is 'OO') and
display it repeatedly on the screen following a snake-like pattern. The printing
will continue for ten seconds after which a random number generator will be used
to generate a fun message. The user also has the option to stop the execution of
the plugin with Ctrl-Z or Ctrl-C. The handlers for these functions are reset
during the execution of the plugin and they are reset back to their initial
states prior to exiting the plugin.
