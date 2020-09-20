package ai.cellbots.executive;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.executive.action.Action;
import ai.cellbots.executive.action.AnimationAction;
import ai.cellbots.executive.action.BatteryMonitorAction;
import ai.cellbots.executive.action.DriveAction;
import ai.cellbots.executive.action.DrivePOIAction;
import ai.cellbots.executive.action.DriveWaitAction;
import ai.cellbots.executive.action.DriveWaitPOIAction;
import ai.cellbots.executive.action.PatrolDriver;
import ai.cellbots.executive.action.PatrolDriverPOI;
import ai.cellbots.executive.action.RandomDriverAction;
import ai.cellbots.executive.action.SoundPlayerAction;
import ai.cellbots.executive.action.VacuumSpiralAction;
import ai.cellbots.executive.action.WaitAction;
import ai.cellbots.robotlib.CloudSoundManager;
import ai.cellbots.robotlib.Controller;
import ai.cellbots.robotlib.SoundManager;

/**
 * A simple executive planner. The simple executive planner offers a number of hard-coded macros,
 * each of which translates directly to a single underlying action. The action goals are simply the
 * macro goals translated directly. The executive planner is "simple" because it does not actually
 * do any planning on how to plan a given goal.
 */
public class SimpleExecutivePlanner extends ExecutivePlanner {
    private final HashMap<String, Action> mActions = new HashMap<>();
    private Action.ActionInstance mActionInstance = null;
    private MacroGoal mCurrentGoal = null;
    private String mGoalRejectionReason = null;

    /**
     * Create the hardcoded drive macro
     *
     * @param point True if it is drivePoint, which does not care about rotation
     */
    private void createDriveMacro(boolean point) {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("location");
        driveParams.add("map");

        addMacro(new Macro("drive" + (point ? "Point" : ""), true, "1.0.0", driveVariables,
                driveParams));
        mActions.put("drive" + (point ? "Point" : ""), new DriveAction(!point));
    }

    /**
     * Create the hardcoded drive macro
     *
     * @param point True if it is drivePoint, which does not care about rotation
     */
    private void createDriveWaitMacro(boolean point) {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        driveVariables.put("time", new Types.VariableTypeLong());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("location");
        driveParams.add("map");
        driveParams.add("time");

        addMacro(new Macro("driveWait" + (point ? "Point" : ""), true, "1.0.0", driveVariables,
                driveParams));
        mActions.put("driveWait" + (point ? "Point" : ""), new DriveWaitAction(!point));
    }

    /**
     * Create the hardcoded vacuum spiral
     */
    private void createVacuumSpiralMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        driveVariables.put("time", new Types.VariableTypeLong());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("location");
        driveParams.add("map");
        driveParams.add("time");

        addMacro(new Macro("vacuumSpiral", true, "1.0.0", driveVariables, driveParams));
        mActions.put("vacuumSpiral", new VacuumSpiralAction());
    }

    /**
     * Create the hardcoded drive to point macro
     */
    private void createDriveToPOIMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("target", new Types.VariableTypeObject("point_of_interest"));
        Set<String> driveParams = new HashSet<>();
        driveParams.add("target");

        addMacro(new Macro("drivePOI", true, "1.0.0", driveVariables, driveParams));
        mActions.put("drivePOI", new DrivePOIAction());
    }

    /**
     * Create the hardcoded drive to point and wait macro
     */
    private void createDriveWaitPOIMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        driveVariables.put("target", new Types.VariableTypeObject("point_of_interest"));
        driveVariables.put("time", new Types.VariableTypeLong());
        Set<String> driveParams = new HashSet<>();
        driveParams.add("target");
        driveParams.add("time");

        addMacro(new Macro("driveWaitPOI", true, "1.0.0", driveVariables, driveParams));
        mActions.put("driveWaitPOI", new DriveWaitPOIAction());
    }

    /**
     * Create the hardcoded macro to wait for a time
     */
    private void createWaitMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("time", new Types.VariableTypeLong());
        driveParams.add("time");
        addMacro(new Macro("wait", true, "1.0.0", driveVariables, driveParams));
        mActions.put("wait", new WaitAction());
    }

    /**
     * Create the hardcoded macro to drive around randomly
     */
    private void createRandomDriverMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        addMacro(new Macro("randomDriver", false, "1.0.0", driveVariables, driveParams));
        mActions.put("randomDriver", new RandomDriverAction());
    }

    /**
     * Create the hardcoded macro to drive around randomly
     */
    private void createBatteryMonitorMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        addMacro(new Macro("batteryMonitorLow", false, "1.0.0", driveVariables, driveParams, true));
        addMacro(new Macro("batteryMonitorCritical", false, "1.0.0", driveVariables, driveParams,
                true));
        mActions.put("batteryMonitorLow", new BatteryMonitorAction(false));
        mActions.put("batteryMonitorCritical", new BatteryMonitorAction(true));
    }

    /**
     * Create the sound macros
     */
    private void createSoundMacros() {
        Map<String, Types.VariableType> timeVariables = new HashMap<>();
        Set<String> timeParams = new HashSet<>();
        timeVariables.put("time", new Types.VariableTypeLong());
        timeParams.add("time");
        Map<String, Types.VariableType> emptyVariables = new HashMap<>();
        Set<String> emptyParams = new HashSet<>();

        addMacro(new Macro("alarm", true, "1.0.0", timeVariables, timeParams));
        mActions.put("alarm", new SoundPlayerAction(SoundManager.GoalSound.ALARM, "time"));
        addMacro(new Macro("you_are_welcome", true, "1.0.0", emptyVariables, emptyParams));
        mActions.put("you_are_welcome",
                new SoundPlayerAction(SoundManager.GoalSound.YOU_ARE_WELCOME, null));
    }

    /**
     * Create the hardcoded macro to drive around randomly
     */
    private void createPatrolDriverMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("location", new Types.VariableTypeTransform());
        driveVariables.put("map", new Types.VariableTypeMap());
        driveVariables.put("time", new Types.VariableTypeLong());
        driveParams.add("location");
        driveParams.add("map");
        driveParams.add("time");
        addMacro(new Macro("patrolDriver", true, "1.0.0", driveVariables, driveParams));
        mActions.put("patrolDriver", new PatrolDriver());
    }

    /**
     * Create the hardcoded macro to drive around randomly
     */
    private void createPatrolDriverPOIMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("target", new Types.VariableTypeObject("point_of_interest"));
        driveVariables.put("time", new Types.VariableTypeLong());
        driveParams.add("target");
        driveParams.add("time");
        addMacro(new Macro("patrolDriverPOI", true, "1.0.0", driveVariables, driveParams));
        mActions.put("patrolDriverPOI", new PatrolDriverPOI());
    }

    /**
     * Create the hardcoded macro to call an animation
     */
    private void createAnimationMacro() {
        Map<String, Types.VariableType> driveVariables = new HashMap<>();
        Set<String> driveParams = new HashSet<>();
        driveVariables.put("animation", new Types.VariableTypeAnimation());
        driveParams.add("animation");
        addMacro(new Macro("animation", true, "1.0.0", driveVariables, driveParams));
        mActions.put("animation", new AnimationAction());
    }

    /**
     * Create the hardcoded POI object type.
     */
    private void createPOIObjectType() {
        Map<String, Types.VariableType> params = new HashMap<>();
        params.put("location", new Types.VariableTypeTransform());
        params.put("name", new Types.VariableTypeString());
        Map<String, Map<String, String>> display = new HashMap<>();
        Map<String, String> displayPoint = new HashMap<>();
        displayPoint.put("type", "point");
        displayPoint.put("color", "red");
        displayPoint.put("location", "location");
        displayPoint.put("offset", "0,0");
        display.put("point", displayPoint);

        Map<String, String> displayText = new HashMap<>();
        displayText.put("type", "text");
        displayText.put("color", "red");
        displayText.put("location", "location");
        displayText.put("offset", "0,-0.1");
        displayText.put("variable", "name");
        display.put("text", displayText);

        addObjectType(new ObjectType("point_of_interest", params, display));
    }

    /**
     * Create the hardcoded types used by the SimpleExecutivePlanner.
     */
    private void hardcodedData() {
        createPOIObjectType();
        createDriveToPOIMacro();
        createDriveMacro(false);
        createDriveMacro(true);
        createRandomDriverMacro();
        createBatteryMonitorMacro();
        createWaitMacro();
        createDriveWaitMacro(false);
        createDriveWaitMacro(true);
        createDriveWaitPOIMacro();
        createSoundMacros();
        createPatrolDriverMacro();
        createPatrolDriverPOIMacro();
        createVacuumSpiralMacro();
        createAnimationMacro();
    }

    /**
     * Create a stub executive planner.
     *
     * @param robotUuid The uuid of the robot.
     * @param userUuid  The uuid of the firebase user.
     */
    public SimpleExecutivePlanner(String userUuid, String robotUuid, CloudSoundManager soundManager) {
        super(userUuid, robotUuid, soundManager, new MacroGoal[]{
                new MacroGoal("RANDOM_DRIVER", "randomDriver", "1.0.0",
                        new HashMap<String, Object>(), userUuid, robotUuid, 0.0, 0, 0),
                new MacroGoal("BATTERY_LOW", "batteryMonitorLow", "1.0.0",
                        new HashMap<String, Object>(), userUuid, robotUuid, 0.0, 1, 20),
                new MacroGoal("BATTERY_CRITICAL", "batteryMonitorCritical", "1.0.0",
                        new HashMap<String, Object>(), userUuid, robotUuid, 0.0, 2, 250)
        });
        hardcodedData();
    }

    /**
     * Called to perform actions upon a goal.
     *
     * @param worldState  The world state.
     * @param goal        The goal to work upon.
     * @param cancel      True if the goal is cancelled.
     * @param controller  The robot's controller.
     * @param preemptGoal The current goal should be preempted, if possible.
     * @param world       The detailed world we are executing against.
     * @return The result of the goal.
     */
    @Override
    public GoalState update(WorldState worldState, MacroGoal goal, boolean cancel,
            Controller controller, boolean preemptGoal, DetailedWorld world) {
        // Clear reason for rejecting a goal, in case there isn't any.
        mGoalRejectionReason = null;
        // If we currently have an Action, and it has the wrong goal, then stop it.
        if (mActionInstance != null) {
            if (!goal.getUuid().equals(mCurrentGoal.getUuid())) {
                mActionInstance = null;
                mCurrentGoal = null;
            }
        }

        // If we do not have an action, then start executing it.
        if (mActionInstance == null) {
            if (mActions.containsKey(goal.getMacro())) {
                mActionInstance = mActions.get(goal.getMacro()).createInstance(
                        new Action.ActionGoal(goal), getCallback());
                mCurrentGoal = goal;
            }
        }

        // If we still have an action, update it.
        if (mActionInstance != null) {
            // Check the timeouts on the patrolDriver goals.
            if (goal.getMacro().equals("patrolDriverPOI") || goal.getMacro().equals("patrolDriver")) {
                if (getStorage(goal) == null) {
                    setStorage(goal, new Date().getTime());
                }
                long startTime = (long) getStorage(goal);
                if (goal.getParameters().containsKey("time")) {
                    if (startTime + (long) goal.getParameters().get("time") < new Date().getTime()) {
                        mActionInstance = null;
                        mCurrentGoal = null;
                        return GoalState.COMPLETE;
                    }
                }
            }

            GoalState r = mActionInstance.update(worldState, cancel, controller, preemptGoal, world);
            if (r == GoalState.COMPLETE || r == GoalState.REJECT || r == GoalState.PREEMPT) {
                mActionInstance = null;
                mCurrentGoal = null;

                // In case a goal was rejected for known reasons, set them.
                if (r == GoalState.REJECT) {
                    mGoalRejectionReason = controller.getGoalRejectionReason();
                }
            }
            return r;
        }

        // If we could not find an action for a goal, reject it.
        return GoalState.REJECT;
    }

    /**
     * Called on goals that need to be queried if they should preempt the current goal.
     *
     * @param worldState The world state.
     * @param goal       The goal to work upon.
     * @param controller The robot's controller.
     * @param world      The detailed world we are executing against.
     * @return True if the goal needs to preempt.
     */
    @Override
    public boolean queryGoal(WorldState worldState, MacroGoal goal, Controller controller,
            DetailedWorld world) {
        return mActions.containsKey(goal.getMacro())
                && mActions.get(goal.getMacro()).query(worldState, goal, controller, world);
    }

    /**
     * Gets the reason why a goal is rejected.
     *
     * @return String indicating the reason.
     */
    @Override
    public String getGoalRejectionReason() {
        return mGoalRejectionReason;
    }
}
