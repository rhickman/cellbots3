package ai.cellbots.executive.action;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.Types;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;

/**
 * Monitors the battery.
 */
public class BatteryMonitorAction extends Action {
    private final boolean mCritical;

    /**
     * Create a BatteryMonitorAction.
     * @param critical Should only trigger if ROBOT_BATTERY_CRITICAL is true, otherwise
     *                 triggers if ROBOT_BATTERY_LOW is true.
     */
    public BatteryMonitorAction(boolean critical) {
        mCritical = critical;
    }

    /**
     * Determines if the goal should run given a state of the system.
     * @param worldState The current world state.
     * @param goal The goal to be evaluated.
     * @param controller The controller.
     * @param world The world.
     * @return If true, the goal will start executing, if false it will stay in the queue.
     */
    @Override
    public boolean query(WorldState worldState, MacroGoal goal, Controller controller,
            DetailedWorld world) {
        Object r = worldState.getState(mCritical ? Types.WorldStateKey.ROBOT_BATTERY_CRITICAL
                                    : Types.WorldStateKey.ROBOT_BATTERY_LOW);
        return (boolean) r && false;
    }

    /**
     * Called to create an instance of the Action.
     * @param goal The goal to be handled.
     * @param callback The callback from the ExecutivePlanner.
     * @return An ActionInstance subclass for handling the goal.
     */
    @Override
    public ActionInstance onCreateInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
        return null;
    }
}
