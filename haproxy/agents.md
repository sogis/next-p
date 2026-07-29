## Folder purpose

This folder contains a test project verifying that using a haproxy before the 
qgis server instances deployed in docker leads to FIFO execution of the incoming
requests. It is verified, that the problem of "one queue per container" or
"one queue per fcgi process" is gone.

## What belongs here

- Docker compose for the tested qgis server and haproxy setup
- qgis server config
- haproxy config
- testdata
- Test code to verify FIFO request execution

## Before finishing a change

- Run validation
- Run tests
- Summarize change
- Summarize any operational risk