# RobotsideApp
The robot side app repository contains the robot android app as well as the 
companion android app.

# Building Locally

Download the Git repo and open it in Android Studio. Wait for gradle to sync, and accept 
installation of any dependencies either from Maven or from the Android SDK. If any 
dependency fails to install, resolve the error. Once it is finished, you will be able
to select "app" or "companion" in the drop-down next to the run arrow. Choose "app" to
build the robot side app and run it, and choose "companion" to build the companion app
and run it.

# Building a Release for Play Store

TODO

# Starting the RobotSide App on a Robot

1. Open the robot app on the robot's phone.
2. Enter a username from firebase and log in. Note currently you must have the same user 
   on the robot app and as on the companion app or web app.
3. If needed, accept the two permissions prompts for Tango. Failure to accept the prompts
   will close the app.
4. Choose "Start Service"
5. A list of maps will be displayed. Choose the one you want.
6. Plug the phone into the Kobuki base, and accept the two USB permission dialogs.
7. Turn on the Kobuki base with the switch in the back right corner.
8. Carry the robot through the map until it is localized.
9. The motors should start moving. See "Using the Companion App".

Troubleshooting note 1: If the robot fails to move, then the robot might not be named
properly. Open the companion app and rename the robot.

Troubleshooting note 2: If the map you would like to run on is not in the list, yet 
you know it was made on the account you are logged in with, then:
1. Select "Stop Service".
2. Click the wrench icon in the top bar.
3. Make sure "Import worlds from cloud" is checked.
4. Click "Start Service".
5. Wait for all worlds to download, accepting the Tango permission dialogs.
6. The map should now be visible.

# Creating a New Map

TODO

# Using the Companion App

TODO

# Using ROS

ROS allows a user to teleoperate the robot and to visualize the pointclouds and camera images from the robot.

To get started, start the application as you normally would, but be sure to note the IP of the phone. It is
helpfully printed for you at the top of the Robot App GUI as "Address: <IP>". The debug computer must have 
two-way TCP access to that IP address, or it will fail. Generally, this means it must be on the same wifi
network as the phone, and that network must allow peer-to-peer access.

Once this is ensured, you must set the ROS_MASTER_URI on the debug computer, by running:
```
export ROS_MASTER_URI=http://<IP>:11311/
```
With IP being the IP from the app.

You should now be able to do "rostopic list" to list all ROS topics.

## Teleoping the Robot

Simply run:
```
rosrun teleop_twist_keyboard teleop_twist_keyboard.py
```

## Viewing Images

To view uncompressed images, run either:
```
rosrun image_view image_view image:=/color/image_raw
```
for color or for fisheye:
```
rosrun image_view image_view image:=/fisheye/image_raw
```

Viewing uncompressed images has an extremely slow frame rate, so instead you may want compressed
```
rosrun image_view image_view image:=/color/image_raw compressed
```
for color or for fisheye:
```
rosrun image_view image_view image:=/fisheye/image_raw compressed
```

## Point Clouds

To view point clouds, use rviz: 
```
rosrun rviz rviz
```

And then configure a PointCloud2 viewer. There is only one topic avaiable, "/point_cloud", so 
use it to view the point cloud.

## Troubleshooting: bad ROS_HOSTNAME or bad ROS_IP

In order for the robot to send ROS messages to the computer, it must have a valid ROS_HOSTNAME
or ROS_IP. The ROS_HOSTNAME is a DNS name which can be accessed. We recommend that you set this
to my_computer_hostname.local, where my_computer_hostname is the output of running 
```hostname```. This will cause the robot to use zeroconf networking to connect to your computer,
so you will be isolated from IP address changes. If this is not an option, we recommend using
ROS_IP to set the IP address of the computer.
