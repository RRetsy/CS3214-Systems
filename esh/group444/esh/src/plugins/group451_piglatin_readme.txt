Group Number
------------
Group 451

Student Information
-------------------
Elmer Landaverde	pid: elmer@vt.edu
Scott Sines			pid: ssines@vt.edu

How to execute the plugin
-------------------------

The piglatin plugin is executed as follows:

piglatin ... WORD ...

First write "piglatin" which is the name of the plugin. Next the user can write
as many words as needed. The plugin will take an unlimited number of arguments
and parse all of them into pig latin.

Description of Functionality
----------------------------

The plugin will analyze one word at a time and apply the following rules:

-	If the word starts with vowel then "way" will be appended to the end of the
	word. Example: apple -> appleway, exit -> exitway

-	If the word starts with a consonant then everything up to the first vowel
	will be cut from the word and put at the end followed by "ay". In this case
	the letter 'y' counts as a vowel. Example: phil -> ilphay, scott -> ottscay
