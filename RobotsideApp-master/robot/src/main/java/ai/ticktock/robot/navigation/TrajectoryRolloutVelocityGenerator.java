package ai.cellbots.robot.navigation;

import android.util.Log;

import java.util.List;

import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.driver.RobotModel;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Generate Trajectories through trajectory rollout.
 *
 * TODO(playerone) Add a test.
 */
public class TrajectoryRolloutVelocityGenerator implements VelocityGenerator {
    private final static String TAG = TrajectoryRolloutVelocityGenerator.class.getSimpleName();
    private final RobotSessionGlobals mSession;
    private final double mResolution;
    private final double[][] mVelocities;

    private final static long COST_SAMPLE_TIME = 50;  // In millisecond
    private final static long ROLLOUT_TIME = 2000;  // In millisecond
    private final static long UPDATE_INTERVAL = 100;  // In millisecond

    private final static int LINEAR_VELOCITY_SAMPLES = 4;
    private final static int ANGULAR_VELOCITY_SAMPLES = 10;
    private final static double LINEAR_ACCELERATION_MAX = 0.65;  // in Meters / second^2
    private final static double ANGULAR_ACCELERATION_MAX = 6.0;  // in radians / second^2

    private double[] mCurrentVelocity;

    private final static double COSTMAP_COST_FACTOR = 0.1;
    private final static double GOAL_COST_FACTOR = 24.0;
    private final static double PATH_COST_FACTOR = 32.0;
    private final static double FORWARD_PATH_COST_FACTOR = 32.0;
    private final static double VELOCITY_COST_FACTOR = 0.0;
    private final static double ANGULAR_COST_FACTOR = 20.0;

    /**
     * Robot state.
     */
    private final static class RobotState {
        private final Transform mTransform;
        private final double[] mVelocity;
        private final double mCost;
        private final long mTime;

        /**
         * Creates the robot state for the robot.
         *
         * @param transform The transform.
         * @param velocity The velocity of the robot.
         */
        private RobotState(Transform transform, double[] velocity) {
            mTransform = transform;
            mVelocity = velocity.clone();
            mCost = 0;
            mTime = 0;
        }

        /**
         * Creates a new robot state by updating.
         *
         * @param start The start robot state.
         * @param model The robot model.
         * @param costMap The CostMap of the robot.
         * @param goalVelocity The goal velocity.
         * @param updateTimeLength The update time length.
         */
        private RobotState(RobotState start, RobotModel model, CostMap costMap,
                           double[] goalVelocity, long updateTimeLength) {
            Transform pose = start.getTransform();
            double cost = 0;
            double[] velocity = start.getVelocity();
            for (long t = 0; t < updateTimeLength; t += COST_SAMPLE_TIME) {
                velocity = simulateVelocity(model, goalVelocity, velocity, COST_SAMPLE_TIME);
                pose = simulatePosition(pose, velocity, COST_SAMPLE_TIME);
                byte pointCost = costMap.getCostAtPosition(pose.getPosition(0), pose.getPosition(1));
                if (CostMap.isObstacle(pointCost)) {
                    cost = Double.MAX_VALUE;
                    break;
                }
                cost += pointCost;
            }
            mTransform = pose;
            mVelocity = velocity;
            mTime = start.getTime() + updateTimeLength;
            mCost = cost;
        }

        /**
         * Gets the transform of the robot state.
         *
         * @return The transform.
         */
        private Transform getTransform() {
            return mTransform;
        }

        /**
         * Gets the velocity of the robot state.
         *
         * @return The velocity.
         */
        private double[] getVelocity() {
            return mVelocity.clone();
        }

        /**
         * Get the cost of the robot state.
         *
         * @return The cost.
         */
        private double getCost() {
            return mCost;
        }

        /**
         * Gets the time in the future of the robot state.
         *
         * @return The time in milliseconds.
         */
        private long getTime() {
            return mTime;
        }
    }

    /**
     * Creates the trajectory rollout system.
     *
     * @param session The session variables.
     * @param resolution The resolution of the CostMap.
     */
    public TrajectoryRolloutVelocityGenerator(RobotSessionGlobals session, double resolution) {
        mSession = session;
        mResolution = resolution;
        mVelocities = generateVelocities(session.getRobotModel());
        mCurrentVelocity = new double[]{0, 0};
    }

    /**
     * Simulates the next velocity.
     *
     * @param model The robot model.
     * @param goalVelocity The goal velocity for the robot.
     * @param velocity The velocity of the robot.
     * @param time The time to simulate.
     * @return The new velocity vector.
     */
    private static double[] simulateVelocity(RobotModel model, double[] goalVelocity,
                                             double[] velocity, long time) {
        double deltaLinear = goalVelocity[0] - velocity[0];
        deltaLinear = Math.max(-LINEAR_ACCELERATION_MAX * time / 1000.0,
                Math.min(LINEAR_ACCELERATION_MAX * time / 1000, deltaLinear));
        double deltaAngular = goalVelocity[1] - velocity[1];
        deltaAngular = Math.max(-ANGULAR_ACCELERATION_MAX * time / 1000.0,
                Math.min(ANGULAR_ACCELERATION_MAX * time / 1000, deltaAngular));
        return new double[]{
                Math.max(-model.getMaxLinearSpeed(),
                        Math.min(model.getMaxLinearSpeed(), velocity[0] + deltaLinear)),
                Math.max(-model.getMaxAngularSpeed(),
                        Math.min(model.getMaxAngularSpeed(), velocity[1] + deltaAngular))};
    }

    /**
     * The current robot pose.
     *
     * @param currentRobotPose The current robot position.
     * @param velocity The velocity.
     * @param time The time.
     * @return The new transform.
     */
    private static Transform simulatePosition(Transform currentRobotPose, double[] velocity, long time) {
        return new Transform(
                currentRobotPose.getPosition(0)
                        + velocity[0] * Math.cos(currentRobotPose.getRotationZ()) * time / 1000.0,
                currentRobotPose.getPosition(1)
                        + velocity[0] * Math.sin(currentRobotPose.getRotationZ()) * time / 1000.0,
                currentRobotPose.getPosition(2),
                currentRobotPose.getRotationZ() + velocity[1] * time / 1000.0);
    }

    /**
     * Generates the sampled velocity options for a robot.
     *
     * @param robotModel The robot model.
     * @return The possible velocity options.
     */
    private static double[][] generateVelocities(RobotModel robotModel) {
        double[][] velocities = new double[LINEAR_VELOCITY_SAMPLES
                * (ANGULAR_VELOCITY_SAMPLES * 2 - 1) - 1][2];
        int i = 0;
        for (int lin = 0; lin < LINEAR_VELOCITY_SAMPLES; lin++) {
            double linear = lin * (robotModel.getMaxLinearSpeed() / (LINEAR_VELOCITY_SAMPLES - 1));
            for (int ang = 1; ang < ANGULAR_VELOCITY_SAMPLES; ang++) {
                double v = ang * (robotModel.getMaxAngularSpeed() / (ANGULAR_VELOCITY_SAMPLES - 1));
                velocities[i][0] = linear;
                velocities[i][1] = v;
                i++;
                velocities[i][0] = linear;
                velocities[i][1] = -v;
                i++;
            }
            if (lin != 0) {
                velocities[i][0] = linear;
                velocities[i][1] = 0.0;
                i++;
            }
        }
        if (i != velocities.length) {
            throw new Error("Incorrect velocity count");
        }
        return velocities;
    }

    /**
     * Gets the closest transform.
     *
     * @param transforms The list of transforms.
     * @param target The target transform.
     * @return The transform.
     */
    private Transform getClosest(Transform[] transforms, Transform target) {
        double dist = Double.MAX_VALUE;
        Transform best = null;
        for (Transform transform : transforms) {
            double d2 = transform.planarDistanceToSquared(target);
            if (d2 < dist) {
                dist = d2;
                best = transform;
            }
        }
        return best;
    }

    private final static double MAX_FORWARD_DISTANCE = 0.25;

    /**
     * Generate the command velocity for the robot.
     * @param currentRobotPose Current position and orientation of the robot.
     * @param goal The goal transform.
     * @param costMap The CostMap.
     * @param path Way that the robot should drive
     * @param isBlocked True if the robot is blocked and cannot drive forward
     * @param bins The point cloud safety controller bins
     * @param closeToGoalDistance The distance in which the robot is close to the goal.
     * @param ticksSinceLastUpdate The number of ticks since the last update. Ignored when negative.
     * @param outputPath The output path for the robot.
     * @return The command velocity. May be null.
     */
    @Override
    public double[] computeVelocities(Transform currentRobotPose, Transform goal,
            CostMap costMap, Path path, boolean isBlocked, int[] bins, double closeToGoalDistance,
            long ticksSinceLastUpdate, List<Transform> outputPath) {
        RobotState robotState = new RobotState(currentRobotPose, mCurrentVelocity);

        if (path == null || path.size() < 1) {
            Log.i(TAG, "Stop for empty path");
            mCurrentVelocity = simulateVelocity(mSession.getRobotModel(),
                    new double[]{0.0, 0.0}, mCurrentVelocity, UPDATE_INTERVAL);
            return mCurrentVelocity.clone();
        }
        Log.i(TAG, "Path size: " + path.size());

        double[] bestVelocity = mCurrentVelocity;
        double bestCost = Double.MAX_VALUE;

        Transform[] pathTf = new Transform[path.size()];
        for (int i = 0; i < path.size(); i++) {
            pathTf[i] = new Transform(path.get(i).getX() * mResolution,
                    path.get(i).getY() * mResolution, 0, 0);
        }

        double forwardDistance = Math.min(MAX_FORWARD_DISTANCE,
                Math.sqrt(pathTf[pathTf.length - 1].planarDistanceToSquared(currentRobotPose))
                        / 2.0);

        double vScale = Math.min(1.0,
                Math.sqrt(pathTf[path.size() - 1].planarDistanceToSquared(currentRobotPose))
                        * 1000.0 / (ROLLOUT_TIME * mSession.getRobotModel().getMaxLinearSpeed()));

        for (double[] velocityT : mVelocities) {
            if (isBlocked && velocityT[0] > 0.0) {
                continue;
            }
            double[] velocity = {velocityT[0] * vScale, velocityT[1]};
            RobotState next = new RobotState(robotState,
                    mSession.getRobotModel(), costMap, velocity, ROLLOUT_TIME);
            // If the state is too costly, skip it
            if (next.getCost() == Double.MAX_VALUE) {
                continue;
            }
            Transform forward = new Transform(
                    next.getTransform().getPosition(0)
                            + forwardDistance * Math.cos(next.getTransform().getRotationZ()),
                    next.getTransform().getPosition(1)
                            + forwardDistance * Math.sin(next.getTransform().getRotationZ()),
                    0, next.getTransform().getRotationZ());
            double cost = COSTMAP_COST_FACTOR * next.getCost()
                    + PATH_COST_FACTOR * getClosest(pathTf,
                    next.getTransform()).distanceTo(next.getTransform())
                    + FORWARD_PATH_COST_FACTOR * getClosest(pathTf, forward).distanceTo(forward)
                    + GOAL_COST_FACTOR * pathTf[pathTf.length - 1].distanceTo(next.getTransform())
                    + VELOCITY_COST_FACTOR * (1 - velocity[0] / mSession.getRobotModel().getMaxLinearSpeed());
            if (velocity[0] == 0) {
                cost += ANGULAR_COST_FACTOR * (1 - Math.abs(velocity[1]) / mSession.getRobotModel().getMaxAngularSpeed());
            }
            //Log.i(TAG, "Measure: " + cost + " X:" + velocity[0] + " R:" + velocity[1]);
            if (cost < bestCost) {
                bestCost = cost;
                bestVelocity = velocity;
            }
        }
        Log.i(TAG, "Trajectory cost: " + bestCost + " X: "
                + bestVelocity[0] + " R: " + bestVelocity[1] + " scale:" + vScale);

        mCurrentVelocity = simulateVelocity(mSession.getRobotModel(),
                bestVelocity.clone(), mCurrentVelocity, UPDATE_INTERVAL);

        Log.i(TAG, "Generated velocity: " + mCurrentVelocity[0] + " " + mCurrentVelocity[1]);

        return mCurrentVelocity.clone();
    }

    /**
     * If true, then the local planner will not complete the goal until a null is returned.
     * @return True if we should wait for null.
     */
    @Override
    public boolean isStopManager() {
        return false;
    }
}