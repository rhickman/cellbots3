package ai.cellbots.executive;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.executive.action.Action;
import ai.cellbots.robotlib.Animation;
import ai.cellbots.robotlib.CloudSoundManager;
import ai.cellbots.robotlib.Controller;
import ai.cellbots.robotlib.SoundManager;

/**
 * Actual executive planner. Created by the manager.
 */
public abstract class ExecutivePlanner {
    /**
     * Instructions to the manager about the current goal.
     */
    public enum GoalState {
        WAIT, // Wait for an external event to happen
        PREEMPT, // Preemption has been requested and accepted
        COMPLETE, // Goal has succeeded
        REJECT, // Goal has failed
    }

    private final String mRobotUuid;
    private final String mUserUuid;
    private final CloudSoundManager mSoundManager;
    private final Callback mCallback = new Callback();
    private long mSequence = 0;
    private final HashMap<String, Object> mGoalStorage = new HashMap<>();
    private final HashMap<String, Animation> mAnimations = new HashMap<>();

    /**
     * Gets the storage of a goal.
     * @param goal The goal.
     * @return The stored object, possibly null.
     */
    protected synchronized Object getStorage(MacroGoal goal) {
        if (goal != null && mGoalStorage.containsKey(goal.getUuid())) {
            return mGoalStorage.get(goal.getUuid());
        }
        return null;
    }

    /**
     * Gets an animation by name.
     * @param name The name of the animation.
     * @return The animation or null if it does not exist.
     */
    private synchronized Animation getAnimation(String name) {
        if (mAnimations.containsKey(name)) {
            return mAnimations.get(name);
        }
        return null;
    }

    /**
     * Sets the animations.
     * @param animations The animation list.
     */
    synchronized void setAnimations(Collection<Animation> animations) {
        mAnimations.clear();
        for (Animation a : animations) {
            mAnimations.put(a.getName(), a);
        }
    }

    /**
     * Sets the storage of a goal.
     * @param goal The goal to set.
     * @param object The object to store. Could be null.
     */
    protected synchronized void setStorage(MacroGoal goal, Object object) {
        if (goal != null && object != null) {
            mGoalStorage.put(goal.getUuid(), object);
        }
        if (goal != null && mGoalStorage.containsKey(goal.getUuid()) && object == null) {
            mGoalStorage.remove(goal.getUuid());
        }
    }

    /**
     * Clears out storage for a goal.
     * @param goal The goal to clear.
     */
    synchronized void clearStorage(MacroGoal goal) {
        if (goal != null) {
            clearStorage(goal.getUuid());
        }
    }

    /**
     * Clears out storage for a goal.
     * @param uuid The goal to clear.
     */
    synchronized void clearStorage(String uuid) {
        if (uuid != null && mGoalStorage.containsKey(uuid)) {
            mGoalStorage.remove(uuid);
        }
    }

    /**
     * Class used for callback actions.
     */
    public class Callback {
        /**
         * Adds a new goal to the executive planner.
         *
         * @param goal The new goal to be added.
         */
        public void addGoal(MacroGoal goal) {
            ExecutivePlanner.this.addGoal(goal);
        }

        /**
         * Starts a sound playing.
         *
         * @param goal      The goal to start playing the sound for.
         * @param goalSound The goal sound to start playing.
         * @return The sound or null.
         */
        public SoundManager.Sound playGoalSound(Action.ActionGoal goal,
                SoundManager.GoalSound goalSound) {
            return mSoundManager.playGoalSound(goalSound, goal.getPriority());
        }

        /**
         * Get the next sequence number.
         *
         * @return The next sequence number.
         */
        public long getNextSequence() {
            return ExecutivePlanner.this.getNextSequence();
        }

        /**
         * Get an animation.
         * @param name The animation.
         * @return The animation or null if it does not exist.
         */
        public Animation getAnimation(String name) {
            return ExecutivePlanner.this.getAnimation(name);
        }
    }

    /**
     * Get the callback of this executive planner.
     *
     * @return The callback.
     */
    protected Callback getCallback() {
        return mCallback;
    }

    public String getRobotUuid() {
        return mRobotUuid;
    }

    public String getUserUuid() {
        return mUserUuid;
    }

    // Store the macros within this planner.
    private final Map<String, Macro> mMacros = new HashMap<>();

    // Store the object types within this planner.
    private final Map<String, ObjectType> mObjectTypes = new HashMap<>();

    // A list of goals to be added to the system
    private final LinkedList<MacroGoal> mMacroGoals = new LinkedList<>();

    /**
     * Add a goal to the planner
     */
    protected synchronized void addGoal(MacroGoal newGoal) {
        mMacroGoals.add(newGoal);
    }

    /**
     * Get and goals.
     */
    synchronized MacroGoal[] getAndClearGoals() {
        MacroGoal[] goals = mMacroGoals.toArray(new MacroGoal[0]);
        mMacroGoals.clear();
        return goals;
    }

    /**
     * Add a macro to this planner.
     *
     * @param m The macro to add to the planner.
     */
    protected synchronized void addMacro(Macro m) {
        mMacros.put(m.getName(), m);
    }

    /**
     * Get the next sequence number.
     *
     * @return The next sequence number.
     */
    protected synchronized long getNextSequence() {
        long r = mSequence;
        mSequence++;
        return r;
    }

    /**
     * Get a macro from this planner.
     *
     * @param m The name of the macro.
     * @return The macro or null.
     */
    public synchronized Macro getMacro(String m) {
        if (mMacros.containsKey(m)) {
            return mMacros.get(m);
        }
        return null;
    }

    /**
     * Get the supported macros to upload to firebase
     *
     * @return The macro maps
     */
    public synchronized Map<String, Object> getMacroMaps() {
        Map<String, Object> map = new HashMap<>();
        for (Macro macro : mMacros.values()) {
            map.put(macro.getName(), macro.getMaps());
        }
        return map;
    }

    /**
     * Add a WorldObjectType to this planner.
     *
     * @param o The WorldObjectType to add to the planner.
     */
    protected synchronized void addObjectType(ObjectType o) {
        mObjectTypes.put(o.getName(), o);
    }

    /**
     * Get a WorldObjectType from this planner.
     *
     * @param o The name of the WorldObjectType.
     * @return The WorldObjectType or null.
     */
    public synchronized ObjectType getObjectType(String o) {
        if (mObjectTypes.containsKey(o)) {
            return mObjectTypes.get(o);
        }
        return null;
    }

    /**
     * Get the supported macros to upload to firebase
     *
     * @return The macro maps
     */
    public synchronized Map<String, Object> getObjectTypeMaps() {
        Map<String, Object> map = new HashMap<>();
        for (ObjectType object : mObjectTypes.values()) {
            map.put(object.getName(), object.getMaps());
        }
        return map;
    }

    /**
     * Create an executive planner
     *
     * @param robotUuid The uuid of the robot.
     * @param userUuid  The uuid of the firebase user.
     * @param soundManager The sound manager.
     */
    @SuppressWarnings("unused")
    protected ExecutivePlanner(String userUuid, String robotUuid, CloudSoundManager soundManager) {
        this(userUuid, robotUuid, soundManager, new MacroGoal[0]);
    }

    /**
     * Create an executive planner
     *
     * @param robotUuid The uuid of the robot.
     * @param userUuid  The uuid of the firebase user.
     * @param initGoals Initial goals that are always added.
     * @param soundManager The sound manager.
     */
    protected ExecutivePlanner(String userUuid, String robotUuid, CloudSoundManager soundManager, MacroGoal[] initGoals) {
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mSoundManager = soundManager;
        for (MacroGoal g : initGoals) {
            addGoal(g);
        }
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
    public abstract GoalState update(WorldState worldState, MacroGoal goal,
            boolean cancel, Controller controller, boolean preemptGoal, DetailedWorld world);

    /**
     * Called on goals that need to be queried if they should preempt the current goal.
     *
     * @param worldState  The world state.
     * @param goal        The goal to work upon.
     * @param controller  The robot's controller.
     * @param world       The detailed world we are executing against.
     * @return True if the goal needs to preempt.
     */
    public abstract boolean queryGoal(WorldState worldState, MacroGoal goal,
            Controller controller, DetailedWorld world);

    /**
     * Gets the sound manager.
     * @return The sound manager.
     */
    @SuppressWarnings("unused")
    public CloudSoundManager getSoundManager() {
        return mSoundManager;
    }

    /**
     * Gets the reason why a goal is rejected.
     * @return String indicating the reason.
     */
    public abstract String getGoalRejectionReason();
}