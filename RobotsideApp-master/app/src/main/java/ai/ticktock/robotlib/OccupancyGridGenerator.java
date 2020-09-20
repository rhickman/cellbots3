package ai.cellbots.robotlib;

import org.jboss.netty.buffer.ChannelBuffer;

import nav_msgs.MapMetaData;
import std_msgs.Header;

/**
 * Interface to generate an {@link nav_msgs.OccupancyGrid} to be published by
 * {@link ROSNode}.
 */
public interface OccupancyGridGenerator {
    void fillHeader(Header header);
    void fillInformation(MapMetaData information);
    ChannelBuffer generateChannelBufferData();
}
