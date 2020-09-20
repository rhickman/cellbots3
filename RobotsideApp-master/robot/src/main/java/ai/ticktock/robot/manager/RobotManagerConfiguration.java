package ai.cellbots.robot.manager;

import ai.cellbots.common.Transform;

/**
 * Configuration of the robot manager.
 */
public class RobotManagerConfiguration {
    public static final double DEFAULT_COSTMAP_RESOLUTION = 0.11; // In meters / cell.
    public static final double DEFAULT_INFLATION_FACTOR = 0.5;

    /**
     * SLAM system for the robot.
     */
    public enum SLAMSystem {
        TANGO
    }

    /**
     * The global planner type.
     */
    public enum GlobalPlanner {
        DIJKSTRA,
        ASTAR,
    }

    /**
     * The local planner type.
     */
    public enum LocalPlanner {
        PURE_PURSUIT,
        TRAJECTORY_ROLLOUT,
        SIMPLE
    }

    /**
     * CostMap inflator.
     */
    public enum CostMapInflator {
        SIMPLE,
    }

    /**
     * CostMap fuser.
     */
    public enum CostMapFuser {
        TRIVIAL,
    }

    /**
     * Driver type.
     */
    public enum RobotDriver {
        MOCK, // The mock driver
        REAL, // All supported robot drivers, auto-detect robot type
    }

    /**
     * Executive type.
     */
    public enum Executive {
        RANDOM, // The random driver
        BASIC, // Executes all goals in order with no internal planning
    }

    private final SLAMSystem mSLAMSystem;
    private final GlobalPlanner mGlobalPlanner;
    private final LocalPlanner mLocalPlanner;
    private final CostMapInflator mCostMapInflator;
    private final CostMapFuser mCostMapFuser;
    private final Executive mExecutive;
    private final RobotDriver mRobotDriver;
    private final double mCostMapResolution;
    private final double mInflationFactor;
    private final Transform mMockLocation;
    // True if publishing color/depth images, path, and others to ROS.
    private final boolean mEnableROS;
    // True if running operation (e.g., teleop, point cloud safety blocker) sound.
    private final boolean mEnableOperationSound;
    // True if running vision system.
    private final boolean mEnableVisionSystem;

    /**
     * Creates the RobotManagerConfiguration.
     * @param slamSystem           The SLAM system to be used.
     * @param globalPlanner        The GlobalPlanner to be used.
     * @param localPlanner         The LocalPlanner to be used.
     * @param inflator             The CostMapInflator to be used.
     * @param costMapFuser         The CostMapFuser to be used.
     * @param executive            The Executive to be used.
     * @param robotDriver          The RobotDriver to be used.
     * @param mockLocation         The Transform to be used for fake location, or null for true localization.
     * @param enableROS            If true, ROS is enabled.
     * @param enableOperationSound If true, sound is enabled for operations like teleop and safety blocker.
     * @param enableVisionSystem   If true, vision system is enabled.
     * @param costMapResolution    The CostMap resolution, in meters/cell.
     * @param inflationFactor      The multiplier of robot radius used for global planner costmap inflation.
     */
    public RobotManagerConfiguration(SLAMSystem slamSystem, GlobalPlanner globalPlanner,
            LocalPlanner localPlanner, CostMapInflator inflator, CostMapFuser costMapFuser,
            Executive executive, RobotDriver robotDriver, Transform mockLocation,
            boolean enableROS, boolean enableOperationSound, boolean enableVisionSystem,
            double costMapResolution, double inflationFactor) {
        mSLAMSystem = slamSystem;
        mGlobalPlanner = globalPlanner;
        mLocalPlanner = localPlanner;
        mCostMapInflator = inflator;
        mCostMapFuser = costMapFuser;
        mExecutive = executive;
        mRobotDriver = robotDriver;
        mMockLocation = mockLocation;
        mCostMapResolution = costMapResolution;
        mEnableROS = enableROS;
        mEnableOperationSound = enableOperationSound;
        mEnableVisionSystem = enableVisionSystem;
        mInflationFactor = inflationFactor;
    }

    /**
     * Creates the RobotManagerConfiguration with the default grid size.
     * @param slamSystem           The SLAM system to be used.
     * @param globalPlanner        The GlobalPlanner to be used.
     * @param localPlanner         The LocalPlanner to be used.
     * @param inflator             The CostMapInflator to be used.
     * @param costMapFuser         The CostMapFuser to be used.
     * @param executive            The Executive to be used.
     * @param robotDriver          The RobotDriver to be used.
     * @param mockLocation         The Transform to be used for fake location, or null for true localization.
     * @param enableROS            If true, ROS is enabled.
     * @param enableOperationSound If true, sound is enabled for operations like teleop and safety blocker.
     * @param enableVisionSystem   If true, vision system is enabled.
     */
    public RobotManagerConfiguration(SLAMSystem slamSystem, GlobalPlanner globalPlanner,
            LocalPlanner localPlanner, CostMapInflator inflator, CostMapFuser costMapFuser,
            Executive executive, RobotDriver robotDriver, Transform mockLocation,
            boolean enableROS, boolean enableOperationSound, boolean enableVisionSystem) {
        this(slamSystem, globalPlanner, localPlanner, inflator, costMapFuser, executive,
                robotDriver, mockLocation, enableROS, enableOperationSound,
                enableVisionSystem, DEFAULT_COSTMAP_RESOLUTION, DEFAULT_INFLATION_FACTOR);
    }

    /**
     * Gets the SLAM system used by the robot.
     *
     * @return The SLAM system type.
     */
    public SLAMSystem getSLAMSystem() {
        return mSLAMSystem;
    }

    /**
     * Gets the global planner used by the robot.
     *
     * @return The global planner type.
     */
    public GlobalPlanner getGlobalPlanner() {
        return mGlobalPlanner;
    }

    /**
     * Gets the local planner used by the robot.
     *
     * @return The local planner type.
     */
    public LocalPlanner getLocalPlanner() {
        return mLocalPlanner;
    }

    /**
     * Gets the multiplier of robot radius used for global planner costmap inflation.
     *
     * @return The robot radius inflation factor;
     */
    public double getInflationFactor() {
        return mInflationFactor;
    }

    /**
     * Gets the CostMapInflator used by the robot.
     *
     * @return The CostMapInflator type.
     */
    public CostMapInflator getCostMapInflator() {
        return mCostMapInflator;
    }

    /**
     * Gets the CostMapInflator used by the robot.
     *
     * @return The CostMapInflator type.
     */
    public CostMapFuser getCostMapFuser() {
        return mCostMapFuser;
    }

    /**
     * Gets the Executive used by the robot.
     *
     * @return The Executive type.
     */
    public Executive getExecutive() {
        return mExecutive;
    }

    /**
     * Gets the RobotDriver used by the robot.
     *
     * @return The RobotDriver type.
     */
    public RobotDriver getRobotDriver() {
        return mRobotDriver;
    }

    /**
     * Gets the Transform of the mock location, or null for actual localization.
     *
     * @return The Transform of the mock location, or null for actual localization.
     */
    public Transform getMockLocation() {
        return mMockLocation;
    }

    /**
     * Gets the size of a grid cell, in meters.
     *
     * @return The size of a grid cell.
     */
    public double getCostMapResolution() {
        return mCostMapResolution;
    }

    /**
     * Checks if ROS is enabled.
     *
     * @return True if ROS is enabled.
     */
    public boolean isROSEnabled() {
        return mEnableROS;
    }

    /**
     * Checks if operation sound is enabled.
     *
     * @return True if operation sound is enabled.
     */
    public boolean isOperationSoundEnabled() {
        return mEnableOperationSound;
    }

    /**
     * Checks if vision system is enabled.
     *
     * @return True if vision system is enabled.
     */
    public boolean isVisionSystemEnabled() {
        return mEnableVisionSystem;
    }
}
