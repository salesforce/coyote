# RoadRunner Framing

Protocol Buffers and other serialization frameworks are great with small structured data, but generally suck when faced with large chunks of binary data, RoadRunner provides a simple & efficient way to frame a protocol buffer message along with an optional chunk of binary data and ship it to/from a binary stream (such as TCP, UDT, or Disk files).

A RoadRunner message consists of these 3 parts one after the other.

1. The frame header.
2. The serialized protocol buffer message.
3. The optional binary data.

## Frame Header
The frame header is a simple 12 byte header the specifies the rest of the data it consists of

Byte | Use 
-----|-----
0    | Version/Magic # currently 42. 
1  | The Protocol Buffer message type.
2  | Spare should be 0
3    | Spare should be 0
4-7  | The length in bytes of the protocol buffer message that follows (big endian)
8-11 | The length in bytes of the binary data the follows the protocol buffer message (big endian, can be 0)

## Protocol Buffer message type.
To deserialize a protocol buffer you need to know what type the outer type is, this is not serialized with the message, the PB message type byte in the header can be used to indicate the type of the PB message part of the message. 0 is reserved and shouldn't be used. 1-9 is reserved by the RoadRunner framework itself, 10-255 can be used by services.

## Modules
These modules provide the basic support for the on-wire framing of RoadRunner messages.

Package Name | Description
-------------|-----------------
roadrunner-framing-common |  general utils, supporting standard Java NIO ByteBuffer serialization
roadrunner-framing-netty | Netty ByteBuf based serialization
  
