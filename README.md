Chat Server
===========

Description of Code
--------------------
This source code implements a CLI chat server with the specifications listed in `specification.pdf`. Features include authentication, blocking IPs for suspicious login activity, broadcasting messages to all other users, specifying one or more users to send a message to, viewing the list of users who were connected to the server within the last n minutes, and automatically logging out inactive clients.

The code is organized using object-oriented design and includes these files:
* `Server.java`: listens for client requests for TCP connections, maintaining the state of authentication attempts
* `ServerThread.java`: spawned by the Server instance to handle each client connection
* `Client.java`
* `GarbageCollector.java`: spawned by the Server to monitor all connected Clients, disconnecting them when they become inactive

Development Environment
------------------------
This project was developed on the Columbia CLIC Lab machines, which run Ubuntu Linux 14.04 64-bit.

Usage
------
Please set the following environment variables before you run the program:
* `BLOCK_TIME`: number of seconds to block a user on a given IP after 3 consecutive failed logins from that IP
* `TIME_OUT`: number of seconds to wait after a client's most recently issued command, before closing its connection to the server

To start the server:
```
$ make
$ java Server 4119
```
To start a client, you can either specify the host name and port number where the server is running:
```
$ java Client cairo.clic.cs.columbia.edu 4119
```
or specify the hosts's IP address and port number:
```
$ java Client 128.59.15.55 4119
```

To remove bytecode files:
```
$ make clean
```

Additional Functionality (beyond specs)
----------------------------------------

* messages from the server are printed to each client's console in red font

* messages from other clients denote username of sender
