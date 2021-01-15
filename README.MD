# JVM Server Architectures

## Description
This project compares three server architectures:
* Thread based servers
* Nonblocking sockets based servers
* Asynchronous socket handling

## Launch

In order to launch application, you should call `./gui.sh` from the program.
This script builds application with Gradle, then calls it.

### P.S.
In addition, there is a `./netbench-server.sh` (like `./gui.sh`), which is also a standalone CLI
application. This script is an application which we are testing (benching).
There you choose architecture and port, but usually you don't call it -- GUI
calls it for you. The `./netbench-server.sh` also trigger Gradle build when
called first time.

## Results

Results of launches on all available architectures and varying one of three
parameters are stored at [results](/results) folder. The naming of the file
is following:
```[arch]_[param].[ext]```
Where
* `[arch] = threaded | nonblocking | asynchronous` -- architecture
* `[param] = array | clients | delay` -- parameter varying: sorting array size / amount of simultaneous clients / delay between queries
* `[ext] = png | csv` -- result image (graphics) or result

## Troubleshooting

Sometimes there is a situation when server cannot start because old instances of server are
still running. GUI launches each architecture in separate dedicated port.
To deal with this situation you can call such thing:

```kill $(ps ux | grep server.jar | awk '{print $2}')```

Note, that can also help to unfreeze your debugger when starting application
from IntelliJ.

This one finds `server.jar` in your process list and kills them. **BE CAREFUL**, it may close other applications
containing `server.jar` in the name!

Also, It is recommended to free ports `9661`, `9662`, `9663` for threaded, nonblocking and asynchronous
servers correspondingly. This default values can be changed in source code of the object:
`ru.spb.kry127.netbench.client.PropLoader`.