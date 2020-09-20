Notes for Cellbots:

We fixed the following bugs:
- The system needs to get its own IP address, since ROS opens TCP sockets
  both ways. Thus, we need to get the correct IP. The default IP getter
  sometimes gets the IP of the phone's celluar connection, making it get
  the wrong IP and it hangs up.


Note: none of the changes in this code are proprietary, however the
information that we are working on ROSJava is not public. playertwo could
potentially check in the fixes as personal contributions to the mainline
repo and then we could delete this repo.

See [rosjava_core](https://github.com/rosjava/rosjava_core) readme.
