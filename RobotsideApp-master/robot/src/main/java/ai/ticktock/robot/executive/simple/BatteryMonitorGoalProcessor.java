package ai.cellbots.robot.executive.simple;

import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.Types;
import ai.cellbots.robot.executive.WorldState;

/**
 * Battery monitor goal processor.
 */
final public class BatteryMonitorGoalProcessor extends GoalProcessor {
    public static final GoalProcessorFactory BATTERY_MONITOR_LOW_GOAL_FACTORY
            = new BatteryMonitorGoalProcessorFactory(BatteryLevel.LOW);
    public static final GoalProcessorFactory BATTERY_MONITOR_CRITICAL_GOAL_FACTORY
            = new BatteryMonitorGoalProcessorFactory(BatteryLevel.CRITICAL);

    /**
     * Battery level to trigger.
     */
    private enum BatteryLevel {
        LOW, // When battery is low or critical
        CRITICAL, // When battery is critical only
    }

    /**
     * Factory for battery monitors.
     */
    private static final class BatteryMonitorGoalProcessorFactory
            implements GoalProcessorFactory, GoalProcessorQuery {
        private final BatteryLevel mBatteryLevel;

        /**
         * Create a new battery monitor goal factory.
         *
         * @param batteryLevel The battery level.
         */
        private BatteryMonitorGoalProcessorFactory(BatteryLevel batteryLevel) {
            mBatteryLevel = batteryLevel;
        }

        /**
         * Create a goal processor.
         *
         * @param goal The goal for the processor.
         * @return The new goal processor.
         */
        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new BatteryMonitorGoalProcessor(goal);
        }

        /**
         * Queries the goal.
         *
         * @param goal The goal for query.
         * @param worldState The world state.
         * @return True if the goal should be executed.
         */
        @Override
        public boolean query(Goal goal, WorldState worldState) {
            return (mBatteryLevel == BatteryLevel.LOW
                    && (boolean) worldState.getState(Types.WorldStateKey.ROBOT_BATTERY_LOW))
                    || (boolean) worldState.getState(Types.WorldStateKey.ROBOT_BATTERY_CRITICAL);
        }
    }

    /**
     * Create the battery monitor goal.
     * @param goal The goal.
     */
    private BatteryMonitorGoalProcessor(Goal goal) {
        super(goal);
    }

    /**
     * Process the goal.
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The goal state.
     */
    @Override
    public Executive.GoalState processGoal(Executive.Delegate delegate, WorldState worldState,
            boolean cancel, boolean preempt) {
        if (cancel) {
            return Executive.GoalState.REJECT;
        }
        if (preempt) {
            return Executive.GoalState.PREEMPT;
        }

        // TODO implement
        return Executive.GoalState.RUNNING;
    }
}
