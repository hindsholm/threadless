# ThreadLess

A simple command-line tool for exploring a Trådfri gateway.

## Usage

    java -jar target/threadless-jar-with-dependencies.jar KEY URI

- KEY: The key printed at the bottom of the Traadfri Gateway
- URI: The CoAP URI of the Trådfri gateway

Example:

    java -jar target/threadless-jar-with-dependencies.jar kdhHi7Frwo93LNb coaps://192.168.1.11/.well-known/core
