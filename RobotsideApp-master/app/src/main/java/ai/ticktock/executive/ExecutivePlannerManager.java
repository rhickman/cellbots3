package ai.cellbots.executive;

import android.util.Log;
import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.DetailedWorld;
import ai.cellbots.robotlib.Animation;
import ai.cellbots.robotlib.CloudSoundManager;
import ai.cellbots.robotlib.Controller;
import ai.cellbots.common.Transform;
import ai.cellbots.robotlib.SoundManager;

/**
 * The executive planner for the robot.
 * - Actions run on the robot
 * - Macros provide lists of actions
 */
public class ExecutivePlannerManager {
    private static final String TAG = ExecutivePlannerManager.class.getSimpleName();
    // Maximum time for processing consistency in milliseconds
    private static final long PROCESS_TIMEOUT = 24 * 60 * 60 * 1000;

    public interface Listener {
        /**
         * Called when a goal is rejected.
         *
         * @param goal The goal that is rejected.
         * @param reason why goal is rejected.
         */
        @SuppressWarnings("unused")
        void onGoalRejected(MacroGoal goal, String reason);

        /**
         * Called when a goal is completed.
         *
         * @param goal The goal that is completed.
         */
        void onGoalCompleted(MacroGoal goal);

        /**
         * Called when a goal is created.
         */
        void onGoalCreated(MacroGoal goal);

        /**
         * Called when a goal is added.
         */
        void onGoalAdded(MacroGoal goal);

        /**
         * Called when a new planner is created.
         */
        void onNewExecutivePlanner(ExecutivePlanner planner);
    }

    /**
     * Stores updates to objects.
     */
    private static final class ObjectUpdate {
        private final Map<String, Object> mFirebase;
        private final int mLockoutId;
        private final String mUuid;
        private final String mMapUuid;

        private ObjectUpdate(String mapUuid, String uuid, Map<String, Object> firebase,
                int lockoutId) {
            mUuid = uuid;
            mMapUuid = mapUuid;
            mFirebase = firebase;
            mLockoutId = lockoutId;
        }
    }

    // The current goal for the planner.
    private MacroGoal mCurrentGoal = null;

    // A list of goals that are to be cancelled. Cleared update() where we change goals
    private final Set<String> mCancelGoals = new HashSet<>();
    // A list goals that have been processed (flag reject or completed has been set).
    // The list is stored for a time to allow goal consistency.
    private final Map<MacroGoal, Date> mProcessedGoals = new HashMap<>();

    // A reason why a goal has been rejected.
    private String mGoalRejectionReason = null;

    // A list of listeners to be updated with news of a goal.
    private final List<Listener> mListeners = new ArrayList<>();

    // A list of goals to be achieved
    private final LinkedList<MacroGoal> mMacroGoals = new LinkedList<>();

    // Store the current executive planner.
    private ExecutivePlanner mExecutivePlanner = null;

    // Store the new executive planner.
    private ExecutivePlanner mNewExecutivePlanner = null;

    // Store the sound manager.
    private final CloudSoundManager mSoundManager;

    // If true, we should avoid processing goals since there are no objects.
    private boolean mObjectLockout = true;
    private int mObjectLockoutId = 0;

    // List of object updates for the queue.
    private final List<ObjectUpdate> mObjectUpdates = new LinkedList<>();

    // Stored objects, by lockout id.
    private int mStoredObjectLockoutId = 0;
    private final Map<String, WorldObject> mStoredObjects = new HashMap<>();

    // Store the metadata.
    private RobotMetadata mRobotMetadata = null;
    private RobotMetadata mNewRobotMetadata = null;

    private String mAnimationsUserUuid = null;
    private String mAnimationsRobotUuid = null;
    private HashMap<String, Animation> mAnimations = null;
    private boolean mAnimationsUpdated = false;

    public synchronized void setAnimations(String userUuid, String robotUuid,
            Map<String, Animation> animations) {
        mAnimationsUserUuid = userUuid;
        mAnimationsRobotUuid = robotUuid;
        mAnimations = new HashMap<>(animations);
        mAnimationsUpdated = true;
    }

    /**
     * Add a new listener.
     *
     * @param listener The listener.
     */
    public synchronized void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Remove a listener.
     *
     * @param listener The listener.
     */
    @SuppressWarnings("unused")
    public synchronized void removeListener(Listener listener) {
        mListeners.remove(listener);
    }


    /**
     * Set the robot metadata.
     * @param metadata Stores the metadata of the robot.
     */
    public synchronized void setRobotMetadata(RobotMetadata metadata) {
        mNewRobotMetadata = metadata;
    }

    public ExecutivePlannerManager(CloudSoundManager soundManager) {
        mSoundManager = soundManager;
    }

    /**
     * Update the executive planner.
     *
     * @param currentWorld    The current world.
     * @param location        The current location of the robot.
     * @param controller      The controller of the robot.
     * @param batteryLow      True if the battery is low.
     * @param batteryCritical True if the battery is critically low.
     * @param executiveMode   The mode of the executive planner.
     */
    public void update(DetailedWorld currentWorld, Transform location, Controller controller,
            boolean batteryLow, boolean batteryCritical,
            ExecutiveStateCommand.ExecutiveState executiveMode) {
        if (currentWorld == null || location == null) {
            return;
        }
        updatePlanStep(location, currentWorld.getUuid(), controller, currentWorld,
                batteryLow, batteryCritical, executiveMode);
    }


    /**
     * Called when we have finished processing a goal.
     *
     * @param goal       The goal we have completed processing.
     * @param successful True if the processing was successful.
     */
    private synchronized void processedGoal(MacroGoal goal, boolean successful) {
        if (mCurrentGoal == goal) {
            mCurrentGoal = null;
        }
        mExecutivePlanner.clearStorage(goal);
        mProcessedGoals.put(goal, new Date());
        // Only write non-local goals to the database
        if (!goal.getIsLocal()) {
            for (Listener l : mListeners) {
                if (successful) {
                    l.onGoalCompleted(goal);
                } else {
                    l.onGoalRejected(goal, mGoalRejectionReason);
                }
            }
        }
    }

    /**
     * Update the plan one step - execute up to one MacroLine and up to one Action.
     *
     * @param locationBase    The current location of the robot.
     * @param controller      The robot's controller.
     * @param world           The current world.
     * @param mapId           The current world's ID.
     * @param batteryLow      True if the battery is low.
     * @param batteryCritical True if the battery is critically low.
     * @param executiveState   The state of the executive planner.
     * @return True if we are waiting for a state change
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean updatePlanStep(Transform locationBase, String mapId, Controller controller,
            DetailedWorld world, boolean batteryLow, boolean batteryCritical,
            ExecutiveStateCommand.ExecutiveState executiveState) {
        // If we do not have a goal, then load a new goal from the goal queue.
        MacroGoal currentGoal;
        WorldState worldState;
        boolean cancelGoal = false;
        boolean preemptGoal = false;
        synchronized (this) {
            if (mNewExecutivePlanner != mExecutivePlanner) {
                mExecutivePlanner = mNewExecutivePlanner;
                mSoundManager.stopAllSounds(SoundManager.SoundLevel.AUTOGEN);
                mSoundManager.stopAllSounds(SoundManager.SoundLevel.COMMAND);
                // Force new animations to be written to the system
                mAnimationsUpdated = true;
            }

            if (mExecutivePlanner == null) {
                Log.d(TAG, "No executive planner, skipping");
                return true;
            }

            if (mAnimationsUpdated
                    && mExecutivePlanner.getRobotUuid().equals(mAnimationsRobotUuid)
                    && mExecutivePlanner.getUserUuid().equals(mAnimationsUserUuid)) {
                mExecutivePlanner.setAnimations(mAnimations.values());
                mAnimationsUpdated = false;
            }

            if (mObjectLockout) {
                Log.d(TAG, "Object lockout");
                return true;
            }

            if (mObjectLockoutId != mStoredObjectLockoutId) {
                mStoredObjectLockoutId = mObjectLockoutId;
                mStoredObjects.clear();
            }

            for (ObjectUpdate objectUpdate : mObjectUpdates) {
                if (objectUpdate.mLockoutId != mStoredObjectLockoutId) {
                    continue;
                }
                if (objectUpdate.mFirebase == null) {
                    if (mStoredObjects.containsKey(objectUpdate.mUuid)) {
                        mStoredObjects.remove(objectUpdate.mUuid);
                    }
                } else if (!WorldObject.isMapValid(objectUpdate.mFirebase)) {
                    Log.w(TAG, "Object from firebase is invalid with no type");
                    if (mStoredObjects.containsKey(objectUpdate.mUuid)) {
                        mStoredObjects.remove(objectUpdate.mUuid);
                    }
                } else {
                    ObjectType type = mExecutivePlanner.getObjectType(
                            (String) objectUpdate.mFirebase.get("type"));
                    if (type == null) {
                        Log.w(TAG, "Object ignored for invalid type: "
                                + objectUpdate.mFirebase.get("type"));
                        if (mStoredObjects.containsKey(objectUpdate.mUuid)) {
                            mStoredObjects.remove(objectUpdate.mUuid);
                        }
                    } else if (!WorldObject.isMapValid(objectUpdate.mFirebase, type)) {
                        //Log.w(TAG, "Object invalid with type");
                        if (mStoredObjects.containsKey(objectUpdate.mUuid)) {
                            mStoredObjects.remove(objectUpdate.mUuid);
                        }
                    } else {
                        mStoredObjects.put(objectUpdate.mUuid,
                                new WorldObject(objectUpdate.mMapUuid, type,
                                        objectUpdate.mFirebase));
                    }
                }
            }

            // Add any goals that the executive planner may have wanted to add.
            MacroGoal[] addPlannerGoals = mExecutivePlanner.getAndClearGoals();
            for (MacroGoal g : addPlannerGoals) {
                if (!g.getIsLocal()) {
                    for (Listener l : mListeners) {
                        l.onGoalCreated(g);
                    }
                }
                addGoal(g);
            }

            // Flag if the current goal is cancelled.
            if (mCurrentGoal != null) {
                if (mCancelGoals.contains(mCurrentGoal.getUuid())) {
                    cancelGoal = true;
                }
            }

            if (!mMacroGoals.isEmpty()) {
                // Remove all the cancelled goals or invalid robot/user goals from the system.
                LinkedList<MacroGoal> cancelledGoals = new LinkedList<>();
                for (MacroGoal mg : mMacroGoals) {
                    if (!mg.getUserUuid().equals(mExecutivePlanner.getUserUuid())
                            || !mg.getRobotUuid().equals(mExecutivePlanner.getRobotUuid())) {
                        Log.d(TAG, "Reject goal for invalid robot/user: " + mg);
                        Log.d(TAG, "Goal robot = " + mg.getRobotUuid() + " ours = "
                                + mExecutivePlanner.getRobotUuid());
                        Log.d(TAG, "Goal user = " + mg.getUserUuid() + " ours = "
                                + mExecutivePlanner.getUserUuid());
                        cancelledGoals.add(mg);
                    } else if (mCancelGoals.contains(mg.getUuid())) {
                        Log.d(TAG, "Goal cancelled: " + mg);
                        cancelledGoals.add(mg);
                        mGoalRejectionReason = "Goal cancelled";
                        processedGoal(mg, false);
                    }
                }
                mMacroGoals.removeAll(cancelledGoals);
                // Remove all cancelled goals from the storage of the planner
                for (String goal : mCancelGoals) {
                    mExecutivePlanner.clearStorage(goal);
                }
                // Reset cancelledGoals
                mCancelGoals.clear();

                // Keep trying to cancel the current goal until we actually cancel it.
                if (cancelGoal) {
                    mCancelGoals.add(mCurrentGoal.getUuid());
                }

                // Clean out old processed goals
                Set<MacroGoal> invalidEntries = new HashSet<>();
                Date limitDate = new Date();
                limitDate.setTime(limitDate.getTime() - PROCESS_TIMEOUT);
                for (Map.Entry<MacroGoal, Date> processed : mProcessedGoals.entrySet()) {
                    if (processed.getValue().before(limitDate)) {
                        invalidEntries.add(processed.getKey());
                    }
                }
                for (MacroGoal g : invalidEntries) {
                    mProcessedGoals.remove(g);
                }
            }

            // Create the worldState from raw variables.
            worldState = new WorldState(mRobotMetadata, locationBase, mapId, 0, 0, mStoredObjects,
                    batteryLow, batteryCritical, executiveState);

            // Create a list of higher priority goals first by priority and then by
            // time index, ignoring goals of lower priority than the current goal if it exists.
            LongSparseArray<List<MacroGoal>> higherPriorityGoals = new LongSparseArray<>();
            Set<Long> prioritySet = new TreeSet<>(new Comparator<Long>() {
                @Override
                public int compare(Long o1, Long o2) {
                    if (o1 > o2) {
                        return -1;
                    } else if (o1 < o2) {
                        return 1;
                    }
                    return 0;
                }
            });
            MacroGoal bestNewGoal = null;
            for (MacroGoal goal : mMacroGoals) {
                if (mCurrentGoal != null) {
                    if (goal.getPriority() <= mCurrentGoal.getPriority()) {
                        continue;
                    }
                }
                if (higherPriorityGoals.get(goal.getPriority()) == null) {
                    higherPriorityGoals.put(goal.getPriority(), new LinkedList<MacroGoal>());
                    prioritySet.add(goal.getPriority());
                }
                higherPriorityGoals.get(goal.getPriority()).add(goal);
            }

            // Find best goal first by priority, then timestamp and last by sequence, accepting
            // only those with a valid query() result or those that are not query goals.
            for (Long priority : prioritySet) {
                Collections.sort(higherPriorityGoals.get(priority), new Comparator<MacroGoal>() {
                    @Override
                    public int compare(MacroGoal o1, MacroGoal o2) {
                        if (o1.getTimestamp() == o2.getTimestamp()) {
                            if (o1.getSequence() == o2.getSequence()) {
                                return 0;
                            }
                            return o1.getSequence() < o2.getSequence() ? -1 : 1;
                        }
                        return o1.getTimestamp() < o2.getTimestamp() ? -1 : 1;
                    }
                });
                for (MacroGoal goal : higherPriorityGoals.get(priority)) {
                    Macro macro = mExecutivePlanner.getMacro(goal.getMacro());
                    if (macro != null) {
                        if (macro.isQuery()) {
                            if (mExecutivePlanner.queryGoal(worldState, goal, controller, world)) {
                                bestNewGoal = goal;
                                break;
                            }
                        } else {
                            bestNewGoal = goal;
                            break;
                        }
                    } else {
                        Log.w(TAG, "Invalid macro for goal: " + goal);
                    }
                }
                if (bestNewGoal != null) {
                    break;
                }
            }

            // If we have no goal and there are more goals, then find the highest priority goal
            // to continue executing upon. If have a current goal and a higher priority one is
            // found, we then set the preempt flag to true so we can try to preempt the goal.
            if (mCurrentGoal == null && bestNewGoal != null) {
                mMacroGoals.remove(bestNewGoal);
                mCurrentGoal = bestNewGoal;
            } else if (mCurrentGoal != null && bestNewGoal != null) {
                if (!mCurrentGoal.getUuid().equals(bestNewGoal.getUuid())) {
                    preemptGoal = true;
                }
            }

            // Update the robot metadata. If the new metadata is for the right robot/user, set it
            // as the robot.
            if (mNewRobotMetadata != null) {
                if (mExecutivePlanner.getUserUuid().equals(mNewRobotMetadata.getUserUuid())
                        && mExecutivePlanner.getRobotUuid().equals(mNewRobotMetadata.getRobotUuid())) {
                    mRobotMetadata = mNewRobotMetadata;
                    mNewRobotMetadata = null;
                }
            }
            // Clear the robot's metadata if it is invalid.
            if (mRobotMetadata != null) {
                if (!mExecutivePlanner.getUserUuid().equals(mRobotMetadata.getUserUuid())
                        || !mExecutivePlanner.getRobotUuid().equals(mRobotMetadata.getRobotUuid())) {
                    mRobotMetadata = null;
                }
            }
            // If the robot's metadata is invalid, do not allow actions to execute.
            if (mRobotMetadata == null) {
                return true;
            }

            // Store the current goal in a race-condition free manner
            currentGoal = mCurrentGoal;

            // If there is no macro, we should exit and wait for a goal.
            if (currentGoal == null) {
                return true;
            }
        }

        // Run the planner and see what it does to the goal
        ExecutivePlanner.GoalState state = mExecutivePlanner.update(
                worldState, currentGoal, cancelGoal, controller, preemptGoal, world);
        switch (state) {
            case REJECT:
                Log.i(TAG, "Reject goal: " + currentGoal);
                // Get the reason why the goal was rejected.
                mGoalRejectionReason = mExecutivePlanner.getGoalRejectionReason();
                processedGoal(currentGoal, false);
                return false;
            case COMPLETE:
                Log.i(TAG, "Cancel goal: " + currentGoal);
                processedGoal(currentGoal, true);
                return false;
            case PREEMPT:
                Log.i(TAG, "Preempt goal: " + currentGoal);
                synchronized (this) {
                    MacroGoal goal = mCurrentGoal;
                    mCurrentGoal = null;
                    addGoal(goal);
                }
                return false;
            case WAIT:
                return true;
        }

        return true;
    }

    /**
     * Change the user and/or robot uuids.
     * @param userUuid The user's uuid.
     * @param robotUuid The robot's uuid.
     */
    public synchronized void setUserAndRobotUuid(String userUuid, String robotUuid) {
        // We do not have valid robot, ignore it.
        if (robotUuid == null || userUuid == null) {
            Log.d(TAG, "Executive planner not started since robot " + robotUuid
                    + " and user = " + userUuid);
            return;
        }

        // Check if the executive is invalid.
        if (mExecutivePlanner != null) {
            if (!robotUuid.equals(mExecutivePlanner.getRobotUuid())
                    || !userUuid.equals(mExecutivePlanner.getUserUuid())) {
                Log.d(TAG, "Clearing out executive planner since robot and/or user changed");
                // Clear out the executive planner, the goal queue, and the
                // cancel list, preserving the processed_goals store.
                mNewExecutivePlanner = null;
                mCurrentGoal = null;
                mMacroGoals.clear();
                mCancelGoals.clear();
            }
        }

        // If we do not have an executive planner, create a new one.
        if (mNewExecutivePlanner == null) {
            Log.d(TAG, "New executive planner");
            mNewExecutivePlanner = new SimpleExecutivePlanner(userUuid, robotUuid, mSoundManager);
            for (Listener l : mListeners) {
                l.onNewExecutivePlanner(mNewExecutivePlanner);
            }
            setObjectLockout();
        }
    }

    /**
     * Set a lockout based on objects. The planner will not update goal until this lockout is
     * cleared. We have to do this to avoid evaluating goals against objects that might not exist.
     */
    public synchronized int setObjectLockout() {
        mObjectLockout = true;
        mObjectLockoutId++;
        return mObjectLockoutId;
    }

    /**
     * Clear an object lockout.
     *
     * @param lockoutId The lockout id.
     */
    public synchronized void clearObjectLockout(int lockoutId) {
        if (mObjectLockoutId == lockoutId && mObjectLockout) {
            mObjectLockout = false;
        }
    }

    /**
     * Add an update to an object state from firebase.
     *
     * @param uuid         The object uuid.
     * @param firebaseData The firebase data structure.
     * @param mapId        The map id.
     * @param lockoutId    The lockout id.
     */
    public synchronized void addObject(String uuid, Map<String, Object> firebaseData, String mapId,
            int lockoutId) {
        mObjectUpdates.add(new ObjectUpdate(mapId, uuid, firebaseData, lockoutId));
    }

    /**
     * Add an update to an object state from firebase.
     *
     * @param uuid      The object uuid.
     * @param lockoutId The lockout id.
     */
    public synchronized void removeObject(String uuid, int lockoutId) {
        mObjectUpdates.add(new ObjectUpdate(null, uuid, null, lockoutId));
    }

    /**
     * Add a new goal to the system.
     *
     * @param goal The firebase goal object.
     */
    public void addGoal(Map<String, Object> goal, String userUuid, String robotUuid) {
        // Do not put in completed or rejected goals.
        if (goal.containsKey("reject") || goal.containsKey("complete")) {
            return;
        }
        // If the goal is cancelled, add it to the cancelled ids
        if (goal.containsKey("cancel")) {
            cancelGoal((String) goal.get("uuid"));
        }

        ExecutivePlanner planner;
        synchronized (this) {
            planner = mNewExecutivePlanner;
        }

        // If we have no planner, the goal is invalid.
        if (planner == null) {
            return;
        }

        String macroName = (String) goal.get("name");
        Macro macro = planner.getMacro(macroName);
        if (macro != null) {
            addGoal(new MacroGoal(goal, macro, userUuid, robotUuid));
        }
    }

    /**
     * Add a new goal to the system.
     *
     * @param goal The goal object.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void addGoal(MacroGoal goal) {
        Log.i(TAG, "Add goal: " + goal);
        // If we are re-adding the current goal, ignore the current goal.
        if (mCurrentGoal != null && mCurrentGoal.getUuid().equals(goal.getUuid())) {
            return;
        }

        // If we are re-adding a processed goal, remove that goal
        if (mProcessedGoals.containsKey(goal)) {
            return;
        }

        // Remove an pre-existing versions of the goal
        LinkedList<MacroGoal> preExisting = new LinkedList<>();
        for (MacroGoal comp : mMacroGoals) {
            if (comp.getUuid().equals(goal.getUuid())) {
                preExisting.add(comp);
            }
        }
        mMacroGoals.removeAll(preExisting);

        // Add the goal.
        mMacroGoals.add(goal);

        for (Listener listener : mListeners) {
            listener.onGoalAdded(goal);
        }
    }

    /**
     * Cancel a goal.
     *
     * @param goal The goal to cancel.
     */
    public synchronized void cancelGoal(String goal) {
        mCancelGoals.add(goal);
    }



}
