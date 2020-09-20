package ai.cellbots.robot.state;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.robot.driver.RobotModel;

/**
 * Stores the user uuid, robot uuid, robot model, and detailed world. All elements of this class
 * and subclasses should remain final to avoid race conditions and unexpected behavior.
 */
public class RobotSessionGlobals {
    private final String mUserUuid;
    private final String mRobotUuid;
    private final DetailedWorld mWorld;
    private final RobotModel mRobotModel;

    /**
     * Create the robot state for the system.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param world The world.
     * @param robotModel The robot model.
     */
    public RobotSessionGlobals(String userUuid, String robotUuid, DetailedWorld world, RobotModel robotModel) {
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mWorld = world;
        mRobotModel = robotModel;
    }

    /**
     * Get the user uuid.
     * @return The user uuid.
     */
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Get the robot uuid.
     * @return The robot uuid.
     */
    public String getRobotUuid() {
        return mRobotUuid;
    }

    /**
     * Get the world. Null if mapping.
     * @return The detailed world.
     */
    public DetailedWorld getWorld() {
        return mWorld;
    }

    /**
     * Get the robot model.
     * @return The robot model.
     */
    public RobotModel getRobotModel() {
        return mRobotModel;
    }
}
