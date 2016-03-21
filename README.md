Chat Server
===========

Description of Code
--------------------
TODO

Development Environment
------------------------
This project was developed on the Columbia CLIC Lab machines, which run Ubuntu Linux 14.04 64-bit.

Usage
------
Please set the following environment variables before you run the program:
* `BLOCK_TIME`: number of seconds to block a user on a given IP after 3 consecutive failed logins from that IP
* `TIME_OUT`: number of seconds to wait after a client's most recently issued command, before closing its connection to the server
```
$ make
$ java Server 4119
```
To remove bytecode files:
```
$ make clean
```

Additional Functionality (beyond specs)
----------------------------------------

* messages from the server are printed to console in red font

* messages from other clients denote username of sender
