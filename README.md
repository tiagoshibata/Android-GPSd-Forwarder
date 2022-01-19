# Android GPS/Compass Forwarder

This application forwards NMEA data from your phone's GPS and Compasss to a specified host. It's goal is to easily plug and feed data into a navigation system likr OpenCPN, using your cellphone as a GPS/Compass device. This way, your cellphone can be used as a GPS/Compass in navigation or robotics applications running in a host computer.

## Setup

Data is forwarded using UDP. First, make sure the computer's IP is reachable from your cellphone; this is the case in most default access points, so it should work if both are connected to the same WiFi network.

On OpenCPN to create a UDP connection with your cellphone IP and a port to listen at (for example. "9999"). In the Android app enter your host's IP or "255.255.255.255" to broadcast to everone in this network. Enter same port address and hit Start. OpenCPN is now receiving GPS and Compass data forwarded from your phone.
