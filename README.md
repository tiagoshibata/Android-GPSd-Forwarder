# Android GPSd Client

This application forwards NMEA data from your phone's GPS to a specified host. It's goal is to easily plug and feed data into a GPS server service (e.g. GPSd), using your cellphone as a GPS device. This way, your cellphone can be used as a GPS in navigation or robotics applications running in a host computer.

## Setup

Data is forwarded using UDP. First, make sure the computer's IP is reachable from your cellphone; this is the case in most default access points, so it should work if both are connected to the same WiFi network.

On the host machine, execute `gpsd -N udp://<cellphone IP or * to accept any>:<port to listen at>` (e.g. `gpsd -N udp://*:29998`). In the Android app, put your host's IP and port and hit Start. Your host is now receiving GPS data forwarded from your phone. You can test it with `gpsmon` or other GPSd utilities.
