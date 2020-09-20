package ai.cellbots.robot.navigation;

import android.support.annotation.NonNull;

import ai.cellbots.robot.costmap.CostMapPose;

/**
 * DijkstraNode class.
 */
public class DijkstraNode implements Comparable<DijkstraNode> {
    CostMapPose mPose;
    double mScore = Double.MAX_VALUE;
    DijkstraNode mPrevious = null;

    /**
     * Class constructor.
     */
    public DijkstraNode(CostMapPose pose) {
        mPose = pose;
    }

    /**
     * Calculate the distance from the current node to another DijkstraNode.
     * @param otherNode A DijkstraNode to calculate the distance to.
     * @return distance.
     */
    public double distanceTo(DijkstraNode otherNode) {
        return Math.sqrt(mPose.squaredDistanceTo(otherNode.mPose));
    }

    /**
     * Check if a node has a previous one.
     * @return True if it has a previous node.
     */
    public boolean hasPrevious(){
        return mPrevious != null;
    }

    /**
     * Get the score of a node.
     * @return the cost of the node.
     */
    public double getScore(){
        return mScore;
    }

    /**
     * Set the score of a node.
     * @param score of the node.
     */
    public void setScore(double score){
        mScore = score;
    }

    /**
     * Get the previous node.
     * @return previous node.
     */
    public DijkstraNode getPrevious(){
        return mPrevious;
    }

    /**
     * Set the previous node.
     * @param previous node.
     */
    public void setPrevious(DijkstraNode previous){
        mPrevious = previous;
    }

    /**
     * Get the pose of the node.
     * @return pose of the node.
     */
    public CostMapPose getPose(){
        return mPose;
    }

    /**
     * Compare two Dijkstra nodes, according to its score.
     * This method returns the value 0 if both score are numerically equal;
     * a value less than 0 if distance of the current node is numerically less than the score of
     * otherNode; and a value greater than 0 if score of the current node is numerically greater
     * than the score of otherNode.
     * @param otherNode to compare to.
     */
    @SuppressWarnings("CompareToUsesNonFinalVariable")
    @Override
    public int compareTo(@NonNull DijkstraNode otherNode) {
        return Double.compare(mScore, otherNode.getScore());
    }
}