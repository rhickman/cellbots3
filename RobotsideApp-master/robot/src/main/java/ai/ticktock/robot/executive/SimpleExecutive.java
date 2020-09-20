package ai.cellbots.robot.executive;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ai.cellbots.robot.control.ActionMediator;
import ai.cellbots.robot.control.AnimationManager;
import ai.cellbots.robot.executive.simple.AnimationGoalProcessor;
import ai.cellbots.robot.executive.simple.BatteryMonitorGoalProcessor;
import ai.cellbots.robot.executive.simple.DriveGoalProcessor;
import ai.cellbots.robot.executive.simple.GoalProcessor;
import ai.cellbots.robot.executive.simple.PatrolDriverGoalProcessor;
import ai.cellbots.robot.executive.simple.RandomDriverGoalProcessor;
import ai.cellbots.robot.executive.simple.SoundGoalProcessor;
import ai.cellbots.robot.executive.simple.WaitGoalProcessor;
import ai.cellbots.robot.manager.SoundManager;
import ai.cellbots.robot.ros.ROSNodeManager;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * A simple executive planner that implements a planning-free method for each goal it receives.
 */
public class SimpleExecutive extends Executive {
    private static final String TAG = SimpleExecutive.class.getSimpleName();

    private final Map<String, GoalProcessor.GoalProcessorFactory> mGoalProcessorFactories;
    private GoalProcessor mGoalProcessor;

    /**
     * Creates the hardcoded drive goal.
     *
     * @param point True if it is drivePoint, which does not care about rotation.
     * @return The hardcoded goal.
     */
    private static GoalType createDriveGoal(boolean point) {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("location");
        driveParams.add("map");

        return new GoalType("drive" + (point ? "Point" : ""), true,
                "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded drive and wait goal.
     *
     * @param point True if it is drivePoint, which does not care about rotation.
     * @return The hardcoded goal.
     */
    private static GoalType createDriveWaitGoal(boolean point) {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        driveVariables.put("time", new Types.VariableTypeLong());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("location");
        driveParams.add("map");
        driveParams.add("time");

        return new GoalType("driveWait" + (point ? "Point" : ""), true,
                "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded vacuum spiral goal.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createVacuumSpiralGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        driveVariables.put("time", new Types.VariableTypeLong());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("location");
        driveParams.add("map");
        driveParams.add("time");

        return new GoalType("vacuumSpiral", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded drive to POI goal.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createDriveToPOIGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("target", new Types.VariableTypeObject("point_of_interest"));
        Set<String> driveParams = new HashSet<>();
        driveParams.add("target");

        return new GoalType("drivePOI", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded drive to POI and wait goal.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createDriveWaitPOIGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("target", new Types.VariableTypeObject("point_of_interest"));
        driveVariables.put("time", new Types.VariableTypeLong());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("target");
        driveParams.add("time");

        return new GoalType("driveWaitPOI", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded goal to wait for a time.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createWaitGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("time", new Types.VariableTypeLong());
        driveParams.add("time");
        return new GoalType("wait", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded goal to drive around randomly.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createRandomDriverGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        return new GoalType("randomDriver", false, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded goals to interrupt if the battery gets low.
     *
     * @return The hardcoded goals.
     */
    private static GoalType[] createBatteryMonitorGoals() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        return new GoalType[] {
                new GoalType("batteryMonitorLow", false, "1.0.0",
                        driveVariables, driveParams, true),
                new GoalType("batteryMonitorCritical", false, "1.0.0",
                        driveVariables, driveParams, true)};
    }

    /**
     * Creates the sound goals.
     *
     * @return The hardcoded goals.
     */
    private static GoalType[] createSoundGoals() {
        Map<String, Types.VariableType> timeVariables = new HashMap<>();
        Set<String> timeParams = new HashSet<>();
        timeVariables.put("time", new Types.VariableTypeLong());
        timeParams.add("time");
        Map<String, Types.VariableType> emptyVariables = new HashMap<>();
        Set<String> emptyParams = new HashSet<>();

        return new GoalType[] {
                new GoalType("alarm", true, "1.0.0", timeVariables, timeParams),
                new GoalType("you_are_welcome", true, "1.0.0", emptyVariables, emptyParams)};
    }

    /**
     * Creates the hardcoded goal to drive around randomly.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createPatrolDriverGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        driveParams.add("location");
        driveParams.add("map");
        return new GoalType("patrolDriver", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded goal to drive around POI randomly.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createPatrolDriverPOIGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("target", new Types.VariableTypeObject("point_of_interest"));
        driveParams.add("target");
        return new GoalType("patrolDriverPOI", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the hardcoded goal to call an animation.
     *
     * @return The hardcoded goal.
     */
    private static GoalType createAnimationGoal() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("animation", new Types.VariableTypeAnimation());
        driveParams.add("animation");
        return new GoalType("animation", true, "1.0.0", driveVariables, driveParams);
    }

    /**
     * Creates the static map of hardcoded goals.
     *
     * @return All the hardcoded goals.
     */
    private static Map<String, GoalType> createHardCodedGoals() {
        Set<GoalType> goalTypesSet = new HashSet<>();
        goalTypesSet.add(createAnimationGoal());
        goalTypesSet.add(createDriveGoal(false));
        goalTypesSet.add(createDriveGoal(true));
        goalTypesSet.add(createDriveToPOIGoal());
        goalTypesSet.add(createDriveWaitGoal(false));
        goalTypesSet.add(createDriveWaitGoal(true));
        goalTypesSet.add(createPatrolDriverGoal());
        goalTypesSet.add(createPatrolDriverPOIGoal());
        goalTypesSet.add(createDriveWaitPOIGoal());
        goalTypesSet.add(createRandomDriverGoal());
        goalTypesSet.add(createVacuumSpiralGoal());
        goalTypesSet.add(createWaitGoal());
        Collections.addAll(goalTypesSet, createBatteryMonitorGoals());
        Collections.addAll(goalTypesSet, createSoundGoals());

        Map<String, GoalType> goalTypes = new HashMap<>();
        for (GoalType goalType : goalTypesSet) {
            goalTypes.put(goalType.getName(), goalType);
        }
        return Collections.unmodifiableMap(goalTypes);
    }

    /**
     * Creates the list of goal processors.
     *
     * @return The map of goal type name to factory.
     */
    private static Map<String, GoalProcessor.GoalProcessorFactory> createGoalProcessorFactories() {
        HashMap<String, GoalProcessor.GoalProcessorFactory> factories = new HashMap<>();
        factories.put("drive", DriveGoalProcessor.DRIVE_GOAL_FACTORY);
        factories.put("drivePoint", DriveGoalProcessor.DRIVE_POINT_GOAL_FACTORY);
        factories.put("drivePOI", DriveGoalProcessor.DRIVE_POI_GOAL_FACTORY);
        factories.put("driveWait", DriveGoalProcessor.DRIVE_WAIT_GOAL_FACTORY);
        factories.put("driveWaitPoint", DriveGoalProcessor.DRIVE_WAIT_POINT_GOAL_FACTORY);
        factories.put("driveWaitPOI", DriveGoalProcessor.DRIVE_WAIT_POI_GOAL_FACTORY);
        factories.put("vacuumSpiral", DriveGoalProcessor.VACUUM_SPIRAL_GOAL_FACTORY);
        factories.put("wait", WaitGoalProcessor.WAIT_GOAL_FACTORY);
        factories.put("animation", AnimationGoalProcessor.ANIMATION_GOAL_FACTORY);
        factories.put("you_are_welcome", SoundGoalProcessor.YOU_ARE_WELCOME_GOAL_FACTORY);
        factories.put("alarm", SoundGoalProcessor.ALARM_GOAL_FACTORY);
        factories.put("randomDriver", RandomDriverGoalProcessor.RANDOM_DRIVER_GOAL_FACTORY);
        factories.put("patrolDriver", PatrolDriverGoalProcessor.PATROL_DRIVER_GOAL_FACTORY);
        factories.put("patrolDriverPOI", PatrolDriverGoalProcessor.PATROL_DRIVER_POI_GOAL_FACTORY);
        factories.put("batteryMonitorLow",
                BatteryMonitorGoalProcessor.BATTERY_MONITOR_LOW_GOAL_FACTORY);
        factories.put("batteryMonitorCritical",
                BatteryMonitorGoalProcessor.BATTERY_MONITOR_CRITICAL_GOAL_FACTORY);
        return Collections.unmodifiableMap(factories);
    }

    /**
     * Starts the executive planner.
     *
     * @param parent Parent context.
     * @param session Session.
     * @param actionMediator Robot command action mediator.
     * @param soundManager Sound manager.
     * @param animationManager Animation manager.
     * @param rosNodeManager ROS node manager.
     */
    public SimpleExecutive(Context parent, RobotSessionGlobals session,
            ActionMediator actionMediator, SoundManager soundManager,
            AnimationManager animationManager, ROSNodeManager rosNodeManager) {
        super(parent, session, actionMediator, soundManager, animationManager, rosNodeManager,
                createHardCodedGoals());
        mGoalProcessorFactories = createGoalProcessorFactories();
        for (String goalType : getGoalTypeNames()) {
            if (!mGoalProcessorFactories.containsKey(goalType)) {
                throw new Error("We do not support goal type: " + goalType);
            }
            //noinspection ConstantConditions
            if (getGoalType(goalType).isQuery()
                    && !(mGoalProcessorFactories.get(goalType)
                    instanceof GoalProcessor.GoalProcessorQuery)) {
                throw new Error("Goal type must be query: " + goalType);
            }
        }
        addGoal(new Goal("RANDOM_DRIVER", "randomDriver", "1.0.0", new HashMap<String, Object>(),
                session.getUserUuid(), session.getRobotUuid(), 0, getNextSequence(), 0));
        addGoal(new Goal("BATTERY_LOW", "batteryMonitorLow", "1.0.0",
                new HashMap<String, Object>(), session.getUserUuid(), session.getRobotUuid(), 0,
                getNextSequence(), 20));
        addGoal(new Goal("BATTERY_CRITICAL", "batteryMonitorCritical", "1.0.0",
                new HashMap<String, Object>(), session.getUserUuid(), session.getRobotUuid(), 0,
                getNextSequence(), 250));
        Log.i(TAG, "Created");
    }

    /**
     * Determines if a goal should start execution. We ignore all unknown goals.
     *
     * @param worldState The state of the planner's current world.
     * @param goal       The goal to query for.
     * @return Always true.
     */
    @Override
    protected boolean query(WorldState worldState, Goal goal) {
        GoalProcessor.GoalProcessorFactory factory = mGoalProcessorFactories.get(goal.getType());
        if (factory instanceof GoalProcessor.GoalProcessorQuery) {
            return ((GoalProcessor.GoalProcessorQuery) factory).query(goal, worldState);
        }
        throw new Error("Query on bad goal type: " + goal.getType());
    }

    /**
     * Processes each goal. In our case, returns COMPLETE instantly.
     *
     * @param worldState The state of the planner's current world.
     * @param goal       The current goal to process.
     * @param isNew      True if the goal is new.
     * @param cancel     True if the goal should be cancelled.
     * @param preempt    True if the goal should be preempted.
     * @return Always COMPLETE.
     */
    @Override
    protected GoalState processGoal(WorldState worldState, Goal goal,
            boolean isNew, boolean cancel, boolean preempt) {
        if (isNew) {
            mGoalProcessor = mGoalProcessorFactories.get(goal.getType()).createGoalProcessor(goal);
        }
        return mGoalProcessor.processGoal(getDelegate(), worldState, cancel, preempt);
    }

    /**
     * Updates the executive.
     */
    @Override
    protected void onUpdate() {
        // Do nothing.
    }

    /**
     * Shuts down the executive.
     */
    @Override
    protected void onShutdown() {
        // Do nothing.
    }
}
