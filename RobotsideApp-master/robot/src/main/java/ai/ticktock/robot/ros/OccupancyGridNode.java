package ai.cellbots.robot.ros;

import android.support.annotation.NonNull;
import android.util.Log;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.internal.message.MessageBuffers;
import org.ros.internal.node.topic.SubscriberIdentifier;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.NodeConfiguration;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.PublisherListener;
import org.ros.rosjava_geometry.Quaternion;

import java.io.IOException;

import ai.cellbots.robot.costmap.CostMap;
import nav_msgs.OccupancyGrid;

/**
 * A node for an obstacle grid.
 */
class OccupancyGridNode extends ROSNode {
    private static final String TAG = OccupancyGridNode.class.getSimpleName();
    private static final double ADF_ORIGIN_ABOVE_GROUND = 1.4;
    private final CostMap.Source mSource;

    private Publisher<OccupancyGrid> mOccupancyGridPublisher = null;

    private CostMap mLastPublishedCostMap = null;
    private CostMap mLatchCostMap = null;

    /**
     * Constructor
     *
     * @param nodeConfiguration The configuration for this node.
     * @param source            The node's source.
     */
    OccupancyGridNode(NodeConfiguration nodeConfiguration, @NonNull CostMap.Source source) {
        super(nodeConfiguration, "cellbots/occupancy/" + source.toString().toLowerCase());
        mSource = source;
        start();
    }

    /**
     * Called to start the node.
     */
    @Override
    protected void onStart() {
        Publisher<OccupancyGrid> publisher = getNode().newPublisher(
                GraphName.of("/occupancy/" + mSource.toString().toLowerCase()), OccupancyGrid._TYPE);
        publisher.setLatchMode(true);
        publisher.addListener(new PublisherListener<OccupancyGrid>() {
            @Override
            public void onNewSubscriber(Publisher<OccupancyGrid> publisher,
                    SubscriberIdentifier subscriberIdentifier) {
                synchronized (OccupancyGridNode.this) {
                    if (mLatchCostMap != null) {
                        publishGrid(mLatchCostMap);
                    }
                }
            }

            @Override
            public void onShutdown(Publisher<OccupancyGrid> publisher) {
            }

            @Override
            public void onMasterRegistrationSuccess(Publisher<OccupancyGrid> occupancyGridPublisher) {
            }

            @Override
            public void onMasterRegistrationFailure(Publisher<OccupancyGrid> occupancyGridPublisher) {
            }

            @Override
            public void onMasterUnregistrationSuccess(Publisher<OccupancyGrid> occupancyGridPublisher) {
            }

            @Override
            public void onMasterUnregistrationFailure(Publisher<OccupancyGrid> occupancyGridPublisher) {
            }
        });
        mOccupancyGridPublisher = publisher;
    }

    /**
     * Publish an occupancy grid.
     * @param costMap The CostMap to publish.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void publishGrid(CostMap costMap) {
        if (costMap.isMutable()) {
            throw new Error("CostMaps to OccupancyGridNode must be immutable: " + costMap);
        }

        mLatchCostMap = costMap;

        if (mOccupancyGridPublisher == null
                || mOccupancyGridPublisher.getNumberOfSubscribers() <= 0) {
            return;
        }

        if (mLastPublishedCostMap == costMap) {
            return;
        }
        mLastPublishedCostMap = costMap;

        OccupancyGrid occupancyGridMessage = mOccupancyGridPublisher.newMessage();
        occupancyGridMessage.getHeader().setStamp(Time.fromMillis(System.currentTimeMillis()));
        occupancyGridMessage.getHeader().setFrameId("/map");
        occupancyGridMessage.getInfo().setWidth(
                costMap.getUpperXLimit() - costMap.getLowerXLimit());
        occupancyGridMessage.getInfo().setHeight(
                costMap.getUpperYLimit() - costMap.getLowerYLimit());
        occupancyGridMessage.getInfo().setResolution((float) costMap.getResolution());
        occupancyGridMessage.getInfo().getOrigin().getPosition().setX(
                costMap.getLowerXLimit() * costMap.getResolution());
        occupancyGridMessage.getInfo().getOrigin().getPosition().setY(
                costMap.getLowerYLimit() * costMap.getResolution());
        occupancyGridMessage.getInfo().getOrigin().getPosition().setZ(-ADF_ORIGIN_ABOVE_GROUND);
        Quaternion.identity().toQuaternionMessage(
                occupancyGridMessage.getInfo().getOrigin().getOrientation());

        ChannelBufferOutputStream output = new ChannelBufferOutputStream(
                MessageBuffers.dynamicBuffer());

        byte[] outputGrid = costMap.getFullCostRegion();
        for (int i = 0; i < outputGrid.length; i++) {
            if (CostMap.isObstacle(outputGrid[i])) {
                outputGrid[i] = 0;
            } else {
                outputGrid[i] = (byte)(255 - outputGrid[i]);
            }
        }
        try {
            output.write(outputGrid);
        } catch (IOException e) {
            Log.w(TAG, "Unable to send message, error writing grid", e);
            return;
        }
        occupancyGridMessage.setData(output.buffer());
        // Publish OccupancyGrid message
        mOccupancyGridPublisher.publish(occupancyGridMessage);
    }
}
