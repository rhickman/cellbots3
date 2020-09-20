# usage: launch_rviz 10.34.0.92 10.34.0.94

if [[ -z $1 || -z $2 ]]; then
	echo "usage: source lauch_rviz.sh ROBOT_IP CURRENT_IP"
	return
fi

ROBOT_IP=$1
CURRENT_IP=$2

echo "configuring ROS MASTER"
# IP of the robot.
export ROS_MASTER_URI=http://$ROBOT_IP:11311
# IP of your computer where RViz will run.
export ROS_HOSTNAME=$CURRENT_IP

# Launch RViz.
rviz -d ../config/config.rviz &
# Launch rqt_plot.
rqt_plot &
