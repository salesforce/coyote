# Coyote
[Protocol Buffer](https://code.google.com/p/protobuf/) based RPC mechansim with support for streaming data wrapped in RoadRunner framed messages.

## Problem
The protobuf library is a great way to describe messages and services in a cross-platform way. However, it does not easily support large message payloads where you cannot readily materialize the entire message at once, either because it does not fit into memory or it would be undesirable to do so because of performance concerns.

## Solution
Coyote allows you to create protobuf services (client and server) that also support these large streaming payloads of data.

Internally, Coyote uses Netty to handle the actual wire communication. Netty is an industry standard async IO framework well suited to streaming messages.

Messages are framed using the RoadRunner framing protocol (though this is easily replaced with your own framing protocol of choice) that describes both the generic Protobuf message as well as the length of the payload.

To ensure that messages are streamed quickly and efficiently we leverage a [reactive streams](http://www.reactive-streams.org/) model to manage the bytes when decoding incoming bytes. This allows us to have non-blocking IO with backpressure. While we don't have a fully compliant reactive streams implementation, it follows the same interfaces and spirit without covering every possible case (but maybe it does - we haven't tried using the reactive-streams tck, yet).

Currently, we only support synchronous client and server protobuf implementations, but they are build on async framework, so it should be fairly easy to extent them in the future to support fully async service implementations.

## Why even write this?
Writing an _efficient_ client/server is a non-trivial process. You could start with java's NIO framework and make your own async library. Then you could layer on managing the reads and writes to the socket. And then you could create the logic to create simple RPC mechanisms for the client and server.

Or you could just use Netty (that's what we did). It's the industry standard for doing this sort of work. Though it does force you to use its handler chain mechanisms, this is a clean mechanism for most servers and clients. All coyote does is manage the reading/writing of messages, so handler chains are more than sufficient.

Obviously, Netty has a little bit more overhead than a custom tuned solution (notably managing the handler chains), but it more than makes up for that in documentation, readability and widespread adoption and testing. 

The framing protocol is merely a matter of taste. RoadRunner was used for legacy reasons, but is a compact representation of the message and data.

Tying in directly to protobuf service implementations means you get compile-time checks for your services. Its impossible to add a new method to a service and forget to add the actual handling.

## Features
 * A set of streaming Netty handlers that are divorced from any protobuf logic
   * you can have your own messages and just leverage the framing and streaming components
 * Fully supported protobuf blocking services
   * generic Services are not notably more work
 * Marshalling and unmarshalling of remote exceptions
   * the client knows the full stack trace of what happened on the server
 * Drop-dead simple client and server implementations (see the coyote-it package for examples)
 * Reactive streams paradigm means streaming is _fast_
 * Compile time protobuf service implementation checks
   * never forget to add a message handler

Plus all the standard goodness of Netty and protocol buffers!

## Quick Start

Take a look at the [coyote examples](/coyote-it/src/main/java/com/salesforce/keystone/coyote/example) for a simple client/server implementation that supports
 * simple message passing
 * message streaming
 * exception handling

This is the 'hello world' of using coyote.

## Modules

Coyote is composed of multiple modules so you can pick and choose the necessary pieces

Package Name | Description
-------------|-----------------
coyote-transport |  provides the streaming and RoadRunner handlers (1) 
coyote-protocol | simple protobuf message building (2)
coyote-protobuf-common | utilities for the protobuf server and client
coyote-protobuf-server | protobuf service based server
coyote-protobuf-client | protobuf service based client
coyote-protobuf-rpc-all | helper maven module(3)
roadrunner-framing | support for framing RoadRunner based messages


1. You could drop these in and then handle your own message types.
2. If you want to build your own protobuf service you will need to have physical access to the .proto files so you can add your own message extension (see coyote-it for an example).
3. Just add a dependency on this module and you will pull in everything you need to build your own protobuf service

## Developing

Coyote is a standard maven multi-module project and attempts to follow the standard building practices.

### Building

For the first build, run

``` $ mvn clean install -DskipTests```

which will install the basic artifacts locally. From there, you can start editing files and just running

``` $ mvn compile ``` or ``` $ mvn test-compile ```

to build the source when working in a module

### New Bugs/Features

All bugs and features are tracked via Github Issues.

### Utilities

Supporting tools, like a code formatter, are available in [dev support](/devsupport).

### Releases

Releases follow the standard [semantic versioning model 2.0.0](http://semver.org/spec/v2.0.0.html). The [top-level parent pom](/pom.xml) will track the current release version. Git branches will be used to track major and minor versions, and git tags will be used to mark patch versions.

Releases will be done initially on an 'as needed' basis. However, if the volume of features starts to grow we will move to a monthly release cadence.

### Roadmap

Protocol features:
 * Optional stream framing
   * Would allow the server avoid having to pull the entire message off the wire from the client, if it knows its a message it doesn't care about

Security features:
 * SASL support
   * Netty already supports SASL, so coyote should too
 * Built-in password based authentication
   * Currently you can add this in your own server/client RPC, but it would be nice to include as part of Coyote

### Contributing

Create a new issue before starting your project so that we can keep track of what you are trying to add/fix. That way, we can also offer suggestions or let you know if there is already an effort in progress.

 * Fork off this repository.
 * Create a topic branch for the issue that you are trying to add. When possible, you should branch off master.
   * Generally this will be the short name of the issue, followed by the issue number, e.g. add-password-auth-coyote-10
 * Edit the code in your fork.
 * Send us a pull request when you are done. We'll review your code, suggest any needed changes, and merge it in.

External contributors will be required to sign a Contributor's License Agreement. You can find this file [here](/dev-support/SFDC_CLA.pdf). Please sign, scan, and send to osscore@salesforce.com. We look forward to collaborating with you!
