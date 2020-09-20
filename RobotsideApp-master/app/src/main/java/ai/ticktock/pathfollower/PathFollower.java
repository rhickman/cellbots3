package ai.cellbots.pathfollower;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.cellbots.common.Transform;
import ai.cellbots.container.FibonacciHeap;
import ai.cellbots.robotlib.Controller;

/**
 * Travels around and follows the path.
 */
abstract class PathFollower extends Controller {
    private static final String TAG = PathFollower.class.getSimpleName();
    private static final boolean DEFAULT_AVOID_OBSTACLES = false;

    private PathNode mCurrentNode = null;
    private PathNode mExtraPathNode = null;
    private List<PathNode> mCurrentPathNodes = new ArrayList<>();
    private List<Transform> mOriginalTransforms = new ArrayList<>();

    private Transform mGoal = null;
    private GoalPointAction mGoalPointAction = null;
    private PathFollowerState mState = PathFollowerState.STATE_NO_GOAL;
    private List<PathNode> mPath = null;

    private final double mNodePruningDistance;
    private final double mNodeConnectionDistance;
    private final double mNodeCustomConnectionDistance;
    private final boolean mAvoidObstacles;

    private double mCurrentNodeConnectionDistance;

    private long mTimerStart;

    /**
     * The state of a path follower.
     * STATE_NO_GOAL: the last goal completed successfully, and thus the robot is waiting.
     * STATE_FOLLOW_COURSE: the robot has a valid path to the goal it is following.
     * STATE_CLOSE_TO_GOAL: the robot is near the goal and is driving directly to the goal point.
     * STATE_REPLAN: the robot is replanning after an obstacle
     * STATE_AVOID_OBSTACLE: the robot is avoiding an obstacle
     * STATE_ACTION: the robot is executing an after goal-action, using Controller handleGoalPointAction
     * STATE_CLOSE_TO_GOAL: the robot is near the goal and is heading directly to the goal transform
     * STATE_REJECT_GOAL: the system could not plan to the goal and thus rejected it.
     */
    // TODO: add STATE_NO_PATH_TO_GOAL, to handle cases when no path to a goal is found, even if
    // the goal was valid. https://github.com/cellbotsai/RobotsideApp/issues/148
    enum PathFollowerState {
        STATE_NO_GOAL,
        STATE_FOLLOW_COURSE,
        STATE_REPLAN,
        STATE_AVOID_OBSTACLE,
        STATE_ACTION,
        STATE_CLOSE_TO_GOAL,
        STATE_REJECT_GOAL,
    }

    // Reason why a goal is rejected.
    private String mGoalRejectionReason = null;
    // Reasons for rejecting a goal. Messages for log purpose.
    // TODO: This is temporary, until new states are added or proper classes for the possible states
    // are implemented.
    private static final String GOAL_REJECTED_NO_PATH = "[Goal rejected] Bad plan";
    private static final String GOAL_REJECTED_NULL = "[Goal rejected] Null goal";
    private static final String GOAL_REJECTED_FAR = "[Goal rejected] Goal is too far";
    private static final String GOAL_REJECTED_TIMEOUT = "[Goal rejected] Timeout reached";
    private static final String GOAL_REJECTED_INVALID_ACTION = "[Goal rejected] Invalid action";

    // We prune nodes along the path that are closer than this distance.
    private static final double DEFAULT_NODE_PRUNING_DISTANCE = 0.05;
    // Nodes that are closer than this distance are considered connected.
    private static final double DEFAULT_NODE_CONNECTION_DISTANCE = 0.25;
    // Custom nodes that are closer than this distance are considered connected.
    private static final double DEFAULT_CUSTOM_NODE_CONNECTION_DISTANCE = 0.5;
    // Angles at which extra nodes are added during obstacle avoidance maneuvers.
    private static final double AVOIDANCE_MANEUVER_LEFT_ANGLE = Math.PI / 2;
    private static final double AVOIDANCE_MANEUVER_RIGHT_ANGLE = - Math.PI / 2;
    // The maximum distance from a node to be considered off path. In square meters.
    private static final double MAX_DISTANCE_FROM_NODE_SQUARED = 25.0;

    /**
     * Construct a PathFollower controller with default parameters.
     */
    @SuppressWarnings("WeakerAccess")
    protected PathFollower() {
        mNodePruningDistance =
                DEFAULT_NODE_PRUNING_DISTANCE * DEFAULT_NODE_PRUNING_DISTANCE;
        mNodeConnectionDistance =
                DEFAULT_NODE_CONNECTION_DISTANCE * DEFAULT_NODE_CONNECTION_DISTANCE;
        mNodeCustomConnectionDistance =
                DEFAULT_CUSTOM_NODE_CONNECTION_DISTANCE * DEFAULT_CUSTOM_NODE_CONNECTION_DISTANCE;
        mAvoidObstacles = DEFAULT_AVOID_OBSTACLES;
    }

    /**
     * Construct a PathFollower controller.
     *
     * @param pruningDistance    Nodes less than this distance apart are removed from the path.
     * @param connectionDistance Nodes less than this distance apart are connected.
     * @param customConnectionDistance Nodes less than this distance apart are connected.
     */
    @SuppressWarnings("WeakerAccess")
    protected PathFollower(double pruningDistance, double connectionDistance, double customConnectionDistance) {
        mNodePruningDistance = pruningDistance * pruningDistance;
        mNodeConnectionDistance = connectionDistance * connectionDistance;
        mNodeCustomConnectionDistance = customConnectionDistance * customConnectionDistance;
        mAvoidObstacles = DEFAULT_AVOID_OBSTACLES;
    }

    /**
     * Get the current state of the path follower.
     *
     * @return The PathFollowerState.
     */
    PathFollowerState getPathFollowerState() {
        return mState;
    }

    /**
     * Called when a new world is loaded up by the controller.
     */
    @Override
    final protected void onNewWorld() {
        Log.d(TAG, "Reload path nodes, custom: " + getWorld().getCustomTransformCount()
                + " smoothed: " + getWorld().getSmoothedTransformCount());
        if (getWorld().getCustomTransformCount() == 0) {
            mCurrentNodeConnectionDistance = mNodeConnectionDistance;
            mOriginalTransforms = new ArrayList<>(getWorld().getSmoothedTransformCount());
            for (int i = 0; i < getWorld().getSmoothedTransformCount(); i++) {
                Transform tf = getWorld().getSmoothedTransform(i);
                // Ignore nodes that are very close to the previous node, so that we can prune
                // the node graph
                if (!mOriginalTransforms.isEmpty()) {
                    if (mOriginalTransforms.get(
                            mOriginalTransforms.size() - 1).planarDistanceToSquared(tf)
                            < mNodePruningDistance) {
                        continue;
                    }
                }
                mOriginalTransforms.add(tf);
            }
        } else {
            mCurrentNodeConnectionDistance = mNodeCustomConnectionDistance;
            mOriginalTransforms = new ArrayList<>(getWorld().getCustomTransformCount());
            for (int i = 0; i < getWorld().getCustomTransformCount(); i++) {
                mOriginalTransforms.add(getWorld().getCustomTransform(i));
            }
        }

        mCurrentNode = null;
        clearGoal();
    }

    /**
     * Takes a list of nodes and calculates the distances between its transforms. Populates the
     * close distances for every path node.
     */
    private List<PathNode> computeNodesDistances(List<PathNode> pathNodesList) {
        for (int i = 0; i < pathNodesList.size(); i++) {
            for (int k = i + 1; k < pathNodesList.size(); k++) {
                double dist = (pathNodesList.get(i).getTransform()
                        .planarDistanceToSquared(pathNodesList.get(k).getTransform()));
                if (dist < mCurrentNodeConnectionDistance) {
                    dist = Math.sqrt(dist);
                    pathNodesList.get(i).putCloseDistance(pathNodesList.get(k), dist);
                    pathNodesList.get(k).putCloseDistance(pathNodesList.get(i), dist);
                }
            }
        }

        HashSet<PathNode> connectedNodes = new HashSet<>();
        validateConnections(connectedNodes, pathNodesList.get(0));

        for (PathNode node : pathNodesList) {
            if (!connectedNodes.contains(node)) {
                addLogMessage("[PathFollower] node list is not complete, some nodes are inaccessible");
                break;
            }
        }

        return pathNodesList;
    }

    /**
     * Used to validate the connections of the nodes.
     *
     */
    private void validateConnections(HashSet<PathNode> connectedNodes, PathNode start) {
        if (connectedNodes.contains(start)) {
            return;
        }
        connectedNodes.add(start);
        for (PathNode next : start.getCloseDistanceNodes()) {
            validateConnections(connectedNodes, next);
        }
    }

    /**
     * Clear out the current goal, stopping the robot. This should only be called on the main loop
     * thread that is calling onUpdate() to avoid synchronization issues. Generally called from
     * onUpdateSetGoal().
     */
    protected final void clearGoal() {
        mGoal = null;
        mPath = null;
        mState = PathFollowerState.STATE_NO_GOAL;
        setAction1(false);
        setAction2(false);
    }

    /**
     * Set the goal for the system to target. This should only be called on the main loop thread
     * that is calling onUpdate() to avoid synchronization issues. Generally called from
     * onUpdateSetGoal().
     *
     * @param tf     The transform of the goal to follow.
     * @param action The action to be executed at the goal.
     */
    protected final void setGoal(Transform tf, GoalPointAction action) {
        // Update current list of Pathnodes with original transforms.
        List<PathNode> pathNodes = new ArrayList<>();
        for (int i = 0; i < mOriginalTransforms.size(); i++) {
            PathNode pathNode = new PathNode(new Transform(mOriginalTransforms.get(i)));
            pathNodes.add(pathNode);
        }
        mCurrentPathNodes.clear();
        Log.d(TAG, "Computing distances to " + pathNodes.size() + " nodes");
        mCurrentPathNodes = computeNodesDistances(pathNodes);
        // Clear current node and let the state machine handle it.
        mCurrentNode = null;

        clearGoal();
        // We could not find a TF, we reject the goal.
        if (tf == null) {
            mState = PathFollowerState.STATE_REJECT_GOAL;
            mGoalRejectionReason = GOAL_REJECTED_NULL;
            return;
        }
        /*
        // The closest node is larger than 1.0m, so it is too far from the node.
        PathNode targetNode = getClosestNode(tf);
        if (targetNode.getTransform().planarDistanceTo(tf) > mGoalMaxDistance) {
            mState = PathFollowerState.STATE_REJECT_GOAL;
            mGoalRejectionReason = GOAL_REJECTED_FAR;
            return;
        }*/
        mTimerStart = new Date().getTime();
        mState = PathFollowerState.STATE_FOLLOW_COURSE;
        mGoal = tf;
        mGoalPointAction = action;
    }

    /**
     * Get the closest PathNode to a given transform.
     *
     * @param target The transform.
     * @return The closest path node found. Null if the path is empty.
     */
    @SuppressWarnings("ConstantConditions")
    private PathNode getClosestNode(Transform target) {
        if (mCurrentPathNodes.size() == 0) {
            return null;
        }
        PathNode bestNode = mCurrentPathNodes.get(0);
        double bestDistanceSquared = bestNode.getTransform().planarDistanceToSquared(target);
        for (int i = 1; i < mCurrentPathNodes.size(); i++) {
            PathNode pathNode = mCurrentPathNodes.get(i);
            if (pathNode == null) {
                Log.wtf(TAG, "Current path contains null node.");
            }
            double distanceSquared = pathNode.getTransform().planarDistanceToSquared(target);
            if (bestDistanceSquared > distanceSquared) {
                bestNode = pathNode;
                bestDistanceSquared = distanceSquared;
            }
        }
        return bestNode;
    }

    /**
     * Called by the controller base class to update the system.
     */
    @Override
    final protected void onUpdate() {
        // Check to make sure we have not developed a very long distance to the node. This can
        // happen if a human is manipulating the robot during the start up process and the distance
        // is too long for us to handle.
        if (mCurrentNode != null) {
            if (mCurrentNode.getTransform().planarDistanceToSquared(getLocation()) >
                    MAX_DISTANCE_FROM_NODE_SQUARED) {
                Log.w(TAG, "Robot is too far away for the current node");
                mCurrentNode = null;
            }
        }

        // Call the function for the subclass to set a new goal.
        onUpdateSetGoal();

        // If we do not have a valid location, get the closest current location.
        if (mCurrentNode == null) {
            Log.i(TAG, "Resetting current node");
            mCurrentNode = getClosestNode(getLocation());
        }

        Log.d(TAG,
                "State: " + mState + " path " + (mPath == null ? "null" : mPath.size()) + " goal "
                        + mGoal);

        // If we are in the FOLLOW_COURSE state then we need to execute planning if the plan is
        // is invalid or does not exist.
        if (mState == PathFollowerState.STATE_FOLLOW_COURSE) {
            // If the path starts with not the current node, or the path is empty then clear it.
            if (mPath != null) {
                if (mPath.isEmpty()) {
                    mPath = null;
                } else if (mPath.get(0) != mCurrentNode) {
                    mPath = null;
                }
            }

            // If we have no path, we need to create one.
            if (mPath == null) {
                PathNode target = getClosestNode(mGoal);
                // If the closest node to the target is the current one, then we
                // drive straight to the target with a null path plan.
                if (target == mCurrentNode && target != null) {
                    Log.i(TAG, "Target = current, we are close to goal");
                    mPath = null;
                    mState = PathFollowerState.STATE_CLOSE_TO_GOAL;
                    addLogMessage("[PATHFOLLOWER] New path directly to goal");
                } else if (mCurrentNode == null) {
                    Log.i(TAG, "Current node is null");
                    addLogMessage("[PATHFOLLOWER] Unable to generate path, robot is too far from path");
                    mState = PathFollowerState.STATE_REJECT_GOAL;
                    mGoalRejectionReason = GOAL_REJECTED_NO_PATH;
                } else if (target == null) {
                    Log.i(TAG, "Target node is null");
                    addLogMessage("[PATHFOLLOWER] Unable to generate path, goal is too far from path");
                    mState = PathFollowerState.STATE_REJECT_GOAL;
                    mGoalRejectionReason = GOAL_REJECTED_NO_PATH;
                } else {
                    Log.i(TAG, "Generate new plan");
                    Log.i(TAG, "Target: " + target);
                    Log.i(TAG, "Current: " + mCurrentNode);
                    mPath = generatePlan(mCurrentPathNodes, mCurrentNode, target);
                    if (mPath != null && mPath.isEmpty()) {
                        mPath = null;
                    }
                    if (mPath == null) {
                        // TODO: (FlorGrosso) correct this behavior while re planning after an
                        // obstacle is detected.
                        Log.i(TAG, "Rejecting the goal for bad plan");
                        mState = PathFollowerState.STATE_REJECT_GOAL;
                        mGoalRejectionReason = GOAL_REJECTED_NO_PATH;
                        addLogMessage("[PATHFOLLOWER] Unable to generate path, no connection found");
                    } else {
                        mTimerStart = new Date().getTime();
                        addLogMessage("[PATHFOLLOWER] New path with " + mPath.size() + " points");
                    }
                }
            }
        }

        // We can now follow the course. We have a second if statement because we may have
        // had to reject goal during planning, or we may have skipped ahead to going directly
        // to the goal if we started close enough to the goal.
        if (mState == PathFollowerState.STATE_FOLLOW_COURSE) {
            // Try to drive to the path location
            Log.d(TAG, "Drive to " + mPath.get(0) + " start");
            while (driveToLocation(mPath.get(0).getTransform(), false, false)) {
                Log.d(TAG, "Drive to " + mPath.get(0) + " hit");
                mPath.remove(0);
                mTimerStart = new Date().getTime();
                if (mPath.isEmpty()) {
                    Log.i(TAG, "We are close to goal");
                    mPath = null;
                    mState = PathFollowerState.STATE_CLOSE_TO_GOAL;
                    addLogMessage("[PATHFOLLOWER] Going directly to goal");
                    break;
                } else {
                    mCurrentNode = mPath.get(0);
                }
            }
            if (mRobotIsBlocked && mState == PathFollowerState.STATE_FOLLOW_COURSE) {
                if (mAvoidObstacles) {
                    mState = PathFollowerState.STATE_REPLAN;
                } else {
                    setMotion(0, getAngular());
                }
            }
            // If loop ended because of a timeout event, set state as goal rejected.
            if (new Date().getTime() - mTimerStart > mGoalTimeoutSec * 1000 && mGoalTimeoutSec > 0) {
                mState = PathFollowerState.STATE_REJECT_GOAL;
                mGoalRejectionReason = GOAL_REJECTED_TIMEOUT;
            }
        } else if (mState == PathFollowerState.STATE_REPLAN) {
            // First stop the robot to avoid bumping into obstacle (only when octomap is
            // enabled).
            // TODO: (FlorGrosso) handle deceleration according to the obstacle distance.

            Log.d(TAG, "Path is blocked, recomputing");
            addLogMessage("[PATHFOLLOWER] Path is blocked, recomputing");
            setMotion(0.0, 0.0);

            // See which side is less blocked, if left or right (30 or -30 degrees).
            // distances is an array list with the distances to an obstacle 30 and -30 degrees.

            Transform newPosition;
            // Add an extra node at a distance of 0.25m to the left of the current position.
            Log.d(TAG, "We are blocked. Moving left");
            newPosition = new Transform(
                    DEFAULT_NODE_CONNECTION_DISTANCE * Math.cos(AVOIDANCE_MANEUVER_LEFT_ANGLE),
                    DEFAULT_NODE_CONNECTION_DISTANCE * Math.sin(AVOIDANCE_MANEUVER_LEFT_ANGLE),
                            0, 0);

            // Save the extra node as a new pathnode.
            mExtraPathNode = new PathNode(new Transform(getLocation(), newPosition));

            Set<PathNode> forceNodes = new HashSet<>();
            if (!mPath.isEmpty()) {
                // Do some work before deleting the blocked pathnode.
                Log.d(TAG, "Removing blocked PathNode:" + mPath.get(0).toString());
                // Delete the references to the deleted node as a close distance in other nodes.
                for (Map.Entry<PathNode, Double> nn : mPath.get(0).getCloseDistances()) {
                    nn.getKey().removeCloseDistance(mPath.get(0));
                    forceNodes.add(nn.getKey());
                }
                // Delete blocked node.
                mCurrentPathNodes.remove(mPath.get(0));
            }

            // Add extra node to the list of path nodes and recompute connections.
            Log.d(TAG, "Adding Node:" + mExtraPathNode.toString());
            recomputeCloseNodes(mExtraPathNode, forceNodes);
            mCurrentPathNodes.add(mExtraPathNode);

            // Clear the current path and add the extra node as its single element.
            mPath.clear();
            mPath.add(mExtraPathNode);

            // Now it's time to avoid the obstacle.
            mState = PathFollowerState.STATE_AVOID_OBSTACLE;
        } else if (mState == PathFollowerState.STATE_AVOID_OBSTACLE) {
            Log.d(TAG, "Avoiding obstacle");

            // Unblock the robot.
            mRobotIsBlocked = false;
            if (driveToLocation(mExtraPathNode.getTransform(), false, true)) {
                mPath = null;
                mCurrentNode = null;
                mTimerStart = new Date().getTime();
                mState = PathFollowerState.STATE_FOLLOW_COURSE;
                mExtraPathNode = null;
            } else if (mRobotIsBlocked) {
                // If the robot is still blocked, then replan again.
                mState = PathFollowerState.STATE_REPLAN;
                mExtraPathNode = null;
            }
        } else if (mState == PathFollowerState.STATE_CLOSE_TO_GOAL) {
            // Drive straight to the goal since we got close enough
            if (driveToLocation(mGoal, shouldAlignGoalRotation(), false)) {
                // We have achieved the goal
                if (goalPointImmediateTerminate()) {
                    Log.i(TAG, "Clearing the goal, we have hit the target");
                    clearGoal();
                } else if (startGoalPointAction()) {
                    mState = PathFollowerState.STATE_ACTION;
                } else {
                    Log.w(TAG, "Reject goal for bad action: " + mGoalPointAction);
                    mState = PathFollowerState.STATE_REJECT_GOAL;
                    mGoalRejectionReason = GOAL_REJECTED_INVALID_ACTION;
                }
                setMotion(0.0, 0.0);
            }
            // Stop the robot if we see the wall
            if (isBlockedDepth()) {
                setMotion(0, getAngular());
            }
        } else if (mState == PathFollowerState.STATE_ACTION) {
            GoalPointState state = handleGoalPointAction();
            if (state == GoalPointState.COMPLETED) {
                Log.i(TAG, "Clearing the goal, we have hit the target");
                clearGoal();
            } else if (state == GoalPointState.REJECTED) {
                mState = PathFollowerState.STATE_REJECT_GOAL;
                mGoalRejectionReason = GOAL_REJECTED_INVALID_ACTION;
            }
        } else {
            Log.d(TAG, "Robot is stopped");
            // Robot has either a bad goal or has finished. Either way, stop the robot.
            setMotion(0.0, 0.0);
            setAction1(false);
            setAction2(false);
        }

    }

    /**
     * Recomputes the closest node to a PathNode.
     *
     * @param p PathNode to recompute its closest distances to.
     * @param forceNodes The nodes that will be connected regardless of distance.
     */
    private void recomputeCloseNodes(PathNode p, Set<PathNode> forceNodes) {
        for (PathNode node : mCurrentPathNodes) {
            if (node == p) {
                continue;
            }
            double distSq = node.getTransform().planarDistanceToSquared(p.getTransform());
            if (distSq < mCurrentNodeConnectionDistance || forceNodes.contains(node)) {
                double dist = Math.sqrt(distSq);
                p.putCloseDistance(node, dist);
                node.putCloseDistance(p, dist);
            }
        }
    }

    /**
     * Perform a Dijkstra's algorithm path plan from the current to the target.
     *
     * @param current The current start point.
     * @param target  The end point.
     * @return The List of PathNodes to follow, including current and target.
     */
    private LinkedList<PathNode> generatePlan(List<PathNode> pathNodes, PathNode current,
            PathNode target) {
        HashSet<PathNode> connectedNodes = new HashSet<>();
        validateConnections(connectedNodes, pathNodes.get(0));
        for (PathNode node : pathNodes) {
            if (!connectedNodes.contains(node)) {
                addLogMessage("[PathFollower] node list is not complete in generate plan");
                break;
            }
        }

        final FibonacciHeap<PathNode> heap = new FibonacciHeap<>();

        for (PathNode n : pathNodes) {
            heap.setScore(n, Double.MAX_VALUE);
        }
        heap.setScore(current, 0);

        while (!heap.isEmpty()) {
            PathNode n = heap.pop();
            double score = heap.getScore(n);
            if (score == Double.MAX_VALUE) {
                addLogMessage("[PATHFOLLOWER] Warning: evaluate invalid node");
                continue;
            }
            for (Map.Entry<PathNode, Double> nn : n.getCloseDistances()) {
                double nScore = nn.getValue() + score;
                if (nScore < heap.getScore(nn.getKey())) {
                    heap.setScore(nn.getKey(), nScore);
                }
            }
        }

        for (PathNode n : pathNodes) {
            if (heap.getScore(n) == Double.MAX_VALUE) {
                addLogMessage("[PATHFOLLOWER] Warning: node graph not complete!");
                break;
            }
        }

        // No path to the target
        if (heap.getScore(target) == Double.MAX_VALUE) {
            Log.i(TAG, "No route to target, score is maxed");
            return null;
        }

        LinkedList<PathNode> path = new LinkedList<>();
        path.add(target);
        PathNode f = target;
        while (f != current) {
            double best = 0.0;
            PathNode next = null;
            for (PathNode nn : f.getCloseDistanceNodes()) {
                if (next == null || best > heap.getScore(nn)) {
                    next = nn;
                    best = heap.getScore(nn);
                }
            }
            // This is an error that should never occur. If it did, then the path planner has
            // somehow started from an invalid node.
            if (next == null) {
                Log.e(TAG, "WARNING: stuck at empty node!");
                return null;
            }
            f = next;
            path.push(f);
        }
        return path;
    }

    /**
     * To be overridden by the subclass to decide what to do when a goal is complete or fails.
     */
    @SuppressWarnings("WeakerAccess")
    protected abstract void onUpdateSetGoal();

    /**
     * Get the current path returned by the system for display as part of the Controller interface.
     *
     * @return The path list of nodes to follow.
     */
    @Override
    protected List<Transform> getPath() {
        int len = (mGoal != null) ? 1 : 0;
        if (mPath != null) {
            len += mPath.size();
        }
        List<Transform> output = new ArrayList<>(len);
        if (mPath != null) {
            for (PathNode pathNode : mPath) {
                output.add(pathNode.getTransform());
            }
        }
        if (mGoal != null) {
            output.add(mGoal);
        }
        return output;
    }

    /**
     * Get the reason why a goal has been rejected.
     *
     * @return String indicating the reason.
     */
    public String getGoalRejectionReason() {
        return mGoalRejectionReason;
    }
}
