package ai.cellbots.robot.ros;

import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;

import ai.cellbots.common.ThreadedShutdown;

/**
 * A ROSNode.
 */
abstract class ROSNode extends AbstractNodeMain implements NodeMain, ThreadedShutdown {
    private final String mName;
    private final NodeConfiguration mNodeConfiguration;
    private NodeMainExecutor mNodeMainExecutor;
    private ConnectedNode mNode = null;

    /**
     * Constructor
     *
     * @param nodeConfiguration The configuration for this node.
     * @param name              The name of the node.
     */
    @SuppressWarnings("WeakerAccess")
    protected ROSNode(NodeConfiguration nodeConfiguration, String name) {
        mName = name;
        mNodeConfiguration = nodeConfiguration;
    }

    /**
     * Starts the node. Must be called before the end of the constructor or unexpected behavior
     * will occur.
     */
    protected void start() {
        mNodeMainExecutor = DefaultNodeMainExecutor.newDefault();
        mNodeMainExecutor.execute(this, mNodeConfiguration);
    }

    /**
     * Get the actual ROS node.
     * @return The node.
     */
    protected ConnectedNode getNode() {
        return mNode;
    }

    /**
     * Function called by ROS to start the system up.
     *
     * @param connectedNode The node from ROS.
     */
    @Override
    public final void onStart(ConnectedNode connectedNode) {
        mNode = connectedNode;
        onStart();
    }

    /**
     * Called when the node is started.
     */
    protected abstract void onStart();

    /**
     * Function for ROS to get the default name of the node.
     *
     * @return The ROS GraphName of the node.
     */
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(mName);
    }

    /**
     * Shutdown the node.
     */
    @Override
    public void shutdown() {
        if (mNodeMainExecutor != null) {
            mNodeMainExecutor.shutdown();
            mNodeMainExecutor = null;
        }
    }

    /**
     * Shutdown the node.
     */
    @Override
    public void waitShutdown() {
        shutdown();
    }
}
