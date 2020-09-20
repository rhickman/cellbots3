package ai.cellbots.robot.ros;

import android.util.Log;

import org.ros.RosCore;
import org.ros.exception.RosRuntimeException;
import org.ros.node.NodeConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.NetworkUtil;
import ai.cellbots.common.Polygon;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.vision.PointCloud;

/**
 * Manage the ROS nodes that make up the ROS visualization system.
 */
public class ROSNodeManager implements ThreadedShutdown {
    private final static String TAG = ROSNodeManager.class.getSimpleName();
    private final static String LISTEN_IP = "0.0.0.0";
    private final static int LISTEN_PORT = 11311;
    private final static String MASTER_URI = "http://127.0.0.1:" + LISTEN_PORT + "/";

    private final HashMap<CostMap.Source, OccupancyGridNode> mOccupancyGridNodes = new HashMap<>();
    private final HashMap<PathNode.PathType, PathNode> mPathNodes = new HashMap<>();
    private final RosCore mRosCore;
    private final TransformROSNode mTransformROSNode;
    private final PointCloudNode mPointCloudNode;
    private final MarkerNode mComputerVisionMarkerNode;

    /**
     * Create the ROSNodeManager.
     *
     * @param enable If true, we should enable the ROS node.
     */
    public ROSNodeManager(final boolean enable) {
        List<String> nodeIp = NetworkUtil.getIPv4List();
        if ((nodeIp == null) || nodeIp.isEmpty() || !enable) {
            mTransformROSNode = null;
            mRosCore = null;
            mPointCloudNode = null;
            mComputerVisionMarkerNode = null;
            return;
        }
        RosCore rosCore;
        try {
            rosCore = RosCore.newPublic(LISTEN_IP, LISTEN_PORT);
            rosCore.start();
        } catch (RosRuntimeException ex) {
            Log.w(TAG, "ROS Node could not be started", ex);
            mTransformROSNode = null;
            mRosCore = null;
            mPointCloudNode = null;
            mComputerVisionMarkerNode = null;
            return;
        }
        mRosCore = rosCore;

        NodeConfiguration nodeConfiguration
                = NodeConfiguration.newPublic(nodeIp.get(0));
        try {
            nodeConfiguration.setMasterUri(new URI(MASTER_URI));
        } catch (URISyntaxException e) {
            Log.e(TAG, "URL rejected: " + e);
            mTransformROSNode = null;
            mPointCloudNode = null;
            mComputerVisionMarkerNode = null;
            return;
        }
        mTransformROSNode = new TransformROSNode(nodeConfiguration);
        mPointCloudNode = new PointCloudNode(nodeConfiguration);
        mComputerVisionMarkerNode = new MarkerNode(nodeConfiguration, "computer_vision");
        for (CostMap.Source source : CostMap.Source.values()) {
            mOccupancyGridNodes.put(source,
                    new OccupancyGridNode(nodeConfiguration, source));
        }
        for (PathNode.PathType pathType : PathNode.PathType.values()) {
            mPathNodes.put(pathType, new PathNode(nodeConfiguration, pathType));
        }
    }

    /**
     * Get the list of all threaded shutdown objects to terminate.
     * @return The list of shutdown objects.
     */
    private List<ThreadedShutdown> getShutdownList() {
        LinkedList<ThreadedShutdown> result = new LinkedList<>();
        if (mTransformROSNode != null) {
            result.add(mTransformROSNode);
        }
        if (mPointCloudNode != null) {
            result.add(mPointCloudNode);
        }
        result.addAll(mOccupancyGridNodes.values());
        result.addAll(mPathNodes.values());
        return result;
    }

    /**
     * Shutdown the system.
     */
    @Override
    public void shutdown() {
        for (ThreadedShutdown t : getShutdownList()) {
            t.shutdown();
        }
        if (mRosCore != null) {
            mRosCore.shutdown();
        }
    }

    /**
     * Wait for the system to shutdown.
     */
    @Override
    public void waitShutdown() {
        shutdown();
        TimedLoop.efficientShutdown(getShutdownList());
    }

    /**
     * Publish the device and base poses.
     *
     * @param device The device transform in the map frame.
     * @param base The base transform in the map frame.
     */
    public void publishRobotPose(Transform device, Transform base) {
        if (mTransformROSNode != null) {
            mTransformROSNode.publishRobotPose(device, base);
        }
    }

    /**
     * Publish the device and base poses.
     *
     * @param depthLocation The device depth location in the map frame.
     */
    public void publishPointCloudPose(Transform depthLocation) {
        if (mTransformROSNode != null) {
            mTransformROSNode.publishPointCloudPose(depthLocation);
        }
    }

    /**
    * Publish the POI poses.
    *
    * @param transforms The locations.
    * @param names      The names.
    */
    public void publishPointOfInterestPoses(List<Transform> transforms, List<String> names) {
        if (mTransformROSNode != null) {
            mTransformROSNode.publishPointOfInterestPoses(transforms, names);
        }
    }

    /**
     * Publish an occupancy grid.
     *
     * @param costMap The CostMap to publish.
     */
    public void publishGrid(CostMap costMap) {
        if (costMap == null) {
            return;
        }
        if (mOccupancyGridNodes.containsKey(costMap.getSource())) {
            mOccupancyGridNodes.get(costMap.getSource()).publishGrid(costMap);
        }
    }

    /**
     * Publish a point cloud.
     *
     * @param pointCloud  The PointCloud containing the image data.
     * @param colorsValid True if the point cloud colors are valid.
     * @param pointColors The coloration of the point cloud by the system.
     */
    public void publishPointCloud(PointCloud pointCloud, boolean colorsValid, float[] pointColors) {
        if (mPointCloudNode != null) {
            mPointCloudNode.publishPointCloud(pointCloud, colorsValid, pointColors);
        }
    }

     /**
     * Publish a local plan.
     *
     * @param path The path to publish.
     */
     public void publishLocalPlan(List<Transform> path) {
         if (mPathNodes.containsKey(PathNode.PathType.LOCAL)) {
             mPathNodes.get(PathNode.PathType.LOCAL).publishPath(path);
         }
     }

     /**
     * Publish a global plan.
     *
     * @param path       The path to publish.
     * @param resolution The resolution of the CostMap.
     */
     public void publishGlobalPlan(List<CostMapPose> path, double resolution) {
        if (mPathNodes.containsKey(PathNode.PathType.GLOBAL)) {
            mPathNodes.get(PathNode.PathType.GLOBAL).publishPath(path, resolution);
        }
     }

    /**
     * Sends the vision markers.
     *
     * @param timestamp The timestamp
     * @param polygons  The list of polygons.
     */
    public void sendComputerVisionMarkers(double timestamp, List<Polygon> polygons) {
        if (mComputerVisionMarkerNode != null) {
        mComputerVisionMarkerNode.sendComputerVisionMarkers(timestamp, polygons);
        }
    }
}
