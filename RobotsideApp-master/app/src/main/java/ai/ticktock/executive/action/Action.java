package ai.cellbots.executive.action;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Animation;
import ai.cellbots.robotlib.Controller;
import ai.cellbots.robotlib.SoundManager;

/**
 * An executive planner action. The outer Action class wraps an inner ActionInstance which actually
 * handles a given MacroGoal.
 */
public abstract class Action {

    /**
     * A goal for an action.
     */
    public static class ActionGoal {
        private final Map<String, Object> mParameters;
        private final long mPriority;
        private final String mUserUuid;
        private final String mRobotUuid;

        /**
         * Create an ActionGoal from a MacroGoal by copying parameters.
         * @param goal The MacroGoal.
         */
        public ActionGoal(MacroGoal goal) {
            this(goal.getUserUuid(), goal.getRobotUuid(), goal.getParameters(), goal.getPriority());
        }

        /**
         * Create an action goal with parameters and a priority.
         * @param userUuid The user uuid for the goal.
         * @param robotUuid The robot uuid for the goal.
         * @param parameters The parameters to the goal.
         * @param priority The priority from the root executive planner goal.
         */
        public ActionGoal(String userUuid, String robotUuid, Map<String, Object> parameters, long priority) {
            mUserUuid = userUuid;
            mRobotUuid = robotUuid;
            mParameters = Collections.unmodifiableMap(new HashMap<>(parameters));
            mPriority = priority;
        }

        /**
         * Get the priority of the goal.
         * @return The priority of the goal.
         */
        public long getPriority() {
            return mPriority;
        }

        /**
         * Get the parameters of the goal.
         * @return The parameters.
         */
        @SuppressWarnings("ReturnOfCollectionOrArrayField")
        public Map<String, Object> getParameters() {
            return mParameters;
        }

        /**
         * Get the robot uuid.
         * @return The robot uuid.
         */
        public String getRobotUuid() {
            return mRobotUuid;
        }

        /**
         * Get the user uuid.
         * @return The user uuid.
         */
        public String getUserUuid() {
            return mUserUuid;
        }
    }

    /**
     * An inner ActionInstance for a given goal.
     */
    public abstract class ActionInstance {
        // The goal this action is executing upon.
        private final ActionGoal mGoal;
        // The callback for the executive planner.
        private final ExecutivePlanner.Callback mCallback;

        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        protected ActionInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            mGoal = goal;
            mCallback = callback;
        }

        /**
         * Return the associated goal.
         * @return The goal.
         */
        public ActionGoal getGoal() {
            return mGoal;
        }

        /**
         * Adds a new MacroGoal to the system.
         * @param goal The goal to start up.
         */
        public void addGoal(MacroGoal goal) {
            mCallback.addGoal(goal);
        }

        /**
         * Get the next sequence number.
         * @return The priority of the goal.
         */
        public long getNextSequence() {
            return mCallback.getNextSequence();
        }

        /**
         * Plays a sound.
         * @param goalSound The sound to start playing
         * @return The Sound object or null.
         */
        public SoundManager.Sound playGoalSound(SoundManager.GoalSound goalSound) {
            return mCallback.playGoalSound(mGoal, goalSound);
        }

        /**
         * Gets an animation by name.
         * @param name The name of the animation.
         * @return The animation or null if it does not exist.
         */
        public Animation getAnimation(String name) {
            return mCallback.getAnimation(name);
        }

        /**
         * Called when the action is updated.
         * @param worldState The state of the world, currently.
         * @param cancel True if we should cancel the goal.
         * @param controller The controller.
         * @param preemptGoal True if we should preempt the goal.
         * @param world The current world of the goal.
         * @return A goal state to be returned from the ExecutivePlanner.
         */
        public abstract ExecutivePlanner.GoalState update(WorldState worldState,
                boolean cancel, Controller controller, boolean preemptGoal, DetailedWorld world);
    }

    /**
     * Determines if the goal should run given a state of the system.
     * @param worldState The current world state.
     * @param goal The goal to be evaluated.
     * @param controller The controller.
     * @param world The world.
     * @return If true, the goal will start executing, if false it will stay in the queue.
     */
    @SuppressWarnings("unused")
    public abstract boolean query(WorldState worldState, MacroGoal goal,
                                  Controller controller, DetailedWorld world);

    /**
     * Called to create an instance of the Action.
     * @param goal The goal to be handled.
     * @param callback The callback from the ExecutivePlanner.
     * @return An ActionInstance subclass for handling the goal.
     */
    public abstract ActionInstance onCreateInstance(ActionGoal goal, ExecutivePlanner.Callback callback);

    /**
     * Create an instance of an action.
     * @param goal The goal to be handled.
     * @param callback The callback from the ExecutivePlanner.
     * @return An ActionInstance subclass for handling the goal.
     */
    public final ActionInstance createInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
        ActionInstance a = onCreateInstance(goal, callback);
        if (a.getGoal() != goal) {
            return null;
        }
        return a;
    }
}
