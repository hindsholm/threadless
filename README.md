# ThreadLess

A simple command-line tool for exploring a Trådfri gateway.

## Usage

    java -jar target/threadless-jar-with-dependencies.jar URI KEY

- URI: The CoAP URI of the Trådfri gateway
- KEY: The key printed at the bottom of the Traadfri Gateway

Example:

    java -jar target/threadless-jar-with-dependencies.jar coaps://192.168.1.11/.well-known/core kdhHi7Frwo93LNb
