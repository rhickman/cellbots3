package ai.cellbots.robot.executive;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudSingletonMonitor;
import ai.cellbots.common.cloud.CloudStoredSingletonMonitor;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.robot.control.ActionMediator;
import ai.cellbots.robot.control.AnimationManager;
import ai.cellbots.robot.manager.SoundManager;
import ai.cellbots.robot.ros.ROSNodeManager;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * The executive planner.
 */
public abstract class Executive implements ThreadedShutdown {
    private static final String TAG = Executive.class.getSimpleName();

    // Maximum time for processing consistency in milliseconds.
    private static final long PROCESS_TIMEOUT_MILLISECOND = 24 * 60 * 60 * 1000;  // 24 hours.
    private static final int UPDATE_TIME_MILLISECOND = 100;
    private static final int MIN_PRIORITY_USER_GOAL = 100; // The lowest priority for user goals

    // Number of goals to leave when cleaning goals in the cloud.
    // TODO(playerone) Why don't we remove all of them?
    private static final int NUM_GOALS_TO_LEAVE = 5;

    private final Context mContext;  // Context
    private final ActionMediator mActionMediator; // The action mediator
    private final AnimationManager mAnimationManager;  // The animation manager
    private final SoundManager mSoundManager; // The sound manager
    private final ROSNodeManager mROSNodeManager;  // ROS node manager
    private final RobotSessionGlobals mSession; // The robot session state
    private final TimedLoop mTimedLoop; // Timed loop for executive updates
    // Stores the state of the executive state command monitor.
    private final CloudStoredSingletonMonitor<ExecutiveStateCommand> mExecutiveStateCommandMonitor;
    private final CloudSingletonMonitor mObjectsMonitor; // Monitors world object state
    private final ChildEventListener mGoalsListener; // Monitors goal state updates

    // Storage for the world objects, representing physical objects in the robot's world. For
    // example POIs and docking stations. The specific objects and type of objects are stored here.
    // The map of object uuid to world objects stored.
    private final Map<String, WorldObject> mObjects = new HashMap<>();
    // The list of object type name to world object type stored.
    private final Map<String, WorldObjectType> mObjectTypes;

    // TODO(playerone) replace these HashMap with ConcurrentHashMap and remove synchronization.
    // Storage for the goal states, which specify the next steps for the robot to complete.
    // A map of goal uuid to goal.
    private final Map<String, Goal> mGoals = new HashMap<>();
    // A map of goal type name to goal type.
    private final Map<String, GoalType> mGoalTypes;
    // A map of goal uuid to the timestamp at which it was cancelled.
    private final Map<String, Long> mCancelledGoals = new HashMap<>();
    // A map of goal uuid to the timestamp at which it was completed.
    private final Map<String, Long> mCompletedGoals = new HashMap<>();
    // A map of goal uuid to the timestamp at which it was rejected.
    private final Map<String, Long> mRejectedGoals = new HashMap<>();

    private RobotMetadata mMetadata; // The metadata of the robot
    private Transform mTransform; // Transform to the base of the robot
    private boolean mEnable = false; // Executive has been enabled by RobotManager
    private boolean mObjectsLoaded = false; // True if the world objects have been loaded
    private boolean mBatteryLow = false, mBatteryCritical = false; // Battery state
    private Goal mCurrentGoal; // The current goal that the robot is attempting

    // Stores the sequence number of generated goals. Each goal should have a new higher sequence
    // number so that if two goals are written at the same time they will execute in the correct
    // order when read back from the database.
    private final AtomicLong mSequence = new AtomicLong(0);

    private final Delegate mDelegate = new Delegate(); // Delegate for the executive

    /**
     * Get the delegate for the executive.
     *
     * @return The delegate.
     */
    @SuppressWarnings("WeakerAccess")
    protected final Delegate getDelegate() {
        return mDelegate;
    }

    /**
     * Delegate class for the executive so that objects can access a limited set of functions of
     * the executive object.
     */
    public final class Delegate {
        /**
         * Get the action mediator.
         *
         * @return The ActionMediator.
         */
        public ActionMediator getActionMediator() {
            return Executive.this.getActionMediator();
        }

        /**
         * Get the sound manager.
         *
         * @return Sound manager object.
         */
        public SoundManager getSoundManager() {
            return Executive.this.getSoundManager();
        }

        /**
         * Get the animation manager.
         *
         * @return Animation manager object.
         */
        public AnimationManager getAnimationManager() {
            return Executive.this.getAnimationManager();
        }
        /**
         * Add a new goal to the executive.
         *
         * @param goal The new goal.
         */
        public void addGoal(Goal goal) {
            Executive.this.addGoal(goal);
        }

        /**
         * Compute the next sequence element.
         *
         * @return The next sequence value.
         */
        public long getNextSequence() {
            return Executive.this.getNextSequence();
        }
    }

    /**
     * Get the next sequence number for goal generation.
     *
     * @return The sequence number.
     */
    @SuppressWarnings("WeakerAccess")
    protected final long getNextSequence() {
        return mSequence.getAndIncrement();
    }

    /**
     * Locally add a new goal to the system.
     *
     * @param goal The goal to add.
     */
    @SuppressWarnings("WeakerAccess")
    protected final synchronized void addGoal(Goal goal) {
        Log.d(TAG, "Adding a goal: " + goal.toString());
        mGoals.put(goal.getUuid(), goal);
        if (!goal.isLocal()) {
            CloudPath.ROBOT_GOALS_PATH_ENTITY.getDatabaseReference(mSession.getUserUuid(),
                    mSession.getRobotUuid(), goal.getUuid()).setValue(goal.toMap());
        }
    }

    /**
     * Internal state to be returned on executing a goal.
     */
    public enum GoalState {
        RUNNING, PREEMPT, COMPLETE, REJECT
    }

    /**
     * List all goal type names.
     *
     * @return The set of goal type names.
     */
    @SuppressWarnings("WeakerAccess")
    protected final Set<String> getGoalTypeNames() {
        return mGoalTypes.keySet();
    }

    /**
     * Get a the given goal type for a goal type name.
     *
     * @param name The goal type name.
     * @return The goal type, or null if it does not exist.
     */
    @SuppressWarnings("WeakerAccess")
    protected final GoalType getGoalType(String name) {
        if (mGoalTypes.containsKey(name)) {
            return mGoalTypes.get(name);
        }
        return null;
    }

    /**
     * Starts the executive planner.
     *
     * @param parent Parent context.
     * @param session Session.
     * @param actionMediator Robot command actionMediator.
     * @param soundManager  Sound manager.
     * @param animationManager  Animation manager.
     * @param rosNodeManager ROS node manager.
     * @param goalTypes Goal type indexed by goal type name accepted by the planner.
     */
    @SuppressWarnings("WeakerAccess")
    protected Executive(Context parent, RobotSessionGlobals session, ActionMediator actionMediator,
            SoundManager soundManager, AnimationManager animationManager,
            ROSNodeManager rosNodeManager, Map<String, GoalType> goalTypes) {
        this(parent, session, actionMediator, soundManager, animationManager, rosNodeManager,
                WorldObjectType.createHardcodedObjects(), goalTypes);
    }

    /**
     * Starts the executive planner.
     *
     * @param parent Parent context.
     * @param session Session.
     * @param actionMediator Robot command actionMediator.
     * @param soundManager  Sound manager.
     * @param animationManager  Animation manager.
     * @param rosNodeManager ROS node manager.
     * @param objectTypes Object types to handle in the planner.
     * @param goalTypes Goal type indexed by goal type name accepted by the planner.
     */
    @SuppressWarnings("WeakerAccess")
    protected Executive(final Context parent, final RobotSessionGlobals session,
            ActionMediator actionMediator, SoundManager soundManager,
            AnimationManager animationManager, ROSNodeManager rosNodeManager,
            Map<String, WorldObjectType> objectTypes, Map<String, GoalType> goalTypes) {
        mContext = parent;
        mSession = session;
        mActionMediator = actionMediator;
        mSoundManager = soundManager;
        mAnimationManager = animationManager;
        mROSNodeManager = rosNodeManager;
        mObjectTypes = Collections.unmodifiableMap(new HashMap<>(objectTypes));
        mGoalTypes = Collections.unmodifiableMap(new HashMap<>(goalTypes));
        mEnable = false;

        HashMap<String, Object> goalTypesInCloud = new HashMap<>();
        for (GoalType goalType : mGoalTypes.values()) {
            goalTypesInCloud.put(goalType.getName(), goalType.toMap());
        }
        CloudPath.ROBOT_GOAL_TYPES_PATH.getDatabaseReference(session.getUserUuid(),
                session.getRobotUuid()).setValue(goalTypesInCloud);

        cleanUpRobotGoals(NUM_GOALS_TO_LEAVE);

        mExecutiveStateCommandMonitor = new CloudStoredSingletonMonitor<>(parent, this,
                CloudPath.ROBOT_EXECUTIVE_STATE_PATH, ExecutiveStateCommand.FACTORY);
        mExecutiveStateCommandMonitor.update(mSession.getUserUuid(), mSession.getRobotUuid());

        mObjectsMonitor = createObjectMonitor();
        if (session.getWorld() != null) {
            mObjectsMonitor.update(session.getUserUuid(), session.getRobotUuid(),
                    session.getWorld().getUuid());
        }

        mGoalsListener = createGoalListener();
        mTimedLoop = createTimedLoop();
        Log.i(TAG, "Created");
    }

    /**
     * Creates a cloud singleton monitor object.
     *
     * @return Cloud singleton monitor object.
     */
    private CloudSingletonMonitor createObjectMonitor() {
        return new CloudSingletonMonitor(this, CloudPath.MAP_OBJECTS_PATH,
                new CloudSingletonMonitor.Listener() {
                    @Override
                    public void onDataSnapshot(DataSnapshot dataSnapshot) {
                        Log.d(TAG, "New DataSnapshot in Object monitor");
                        List<WorldObject> objects = WorldObject.fromFirebase(dataSnapshot,
                                mSession.getWorld().getUuid(), mObjectTypes);
                        mObjects.clear();
                        LinkedList<Transform> poiTransforms = new LinkedList<>();
                        LinkedList<String> poiNames = new LinkedList<>();
                        for (WorldObject object : objects) {
                            mObjects.put(object.getUuid(), object);
                            if (object.getType().getName().equals("point_of_interest")
                                    && object.getValue("location") != null
                                    && object.getValue("name") != null) {
                                poiTransforms.add((Transform) object.getValue("location"));
                                poiNames.add(object.getValue("name").toString());
                            }
                        }
                        mROSNodeManager.publishPointOfInterestPoses(poiTransforms, poiNames);
                        mObjectsLoaded = true;
                    }

                    @Override
                    public void afterListenerTerminated() {
                    }

                    @Override
                    public void beforeListenerStarted() {
                    }
                });
    }

    /**
     * Creates a new goal listener.
     *
     * @return Goal listener object.
     */
    private ChildEventListener createGoalListener() {
        return CloudPath.ROBOT_GOALS_PATH
                .getDatabaseReference(mSession.getUserUuid(), mSession.getRobotUuid())
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        Goal goal = Goal.fromFirebase(mContext, dataSnapshot, mGoalTypes,
                                mSession.getUserUuid(), mSession.getRobotUuid());
                        if (goal == null) {
                            if (dataSnapshot.getKey() != null) {
                                rejectGoal(dataSnapshot.getKey());
                            }
                            return;
                        }
                        synchronized (Executive.this) {
                            if (!mGoals.containsKey(goal.getUuid())) {
                                mGoals.put(goal.getUuid(), goal);
                            }
                            onChildChanged(dataSnapshot, null);
                        }
                        cleanUpRobotGoals(NUM_GOALS_TO_LEAVE);
                    }

                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                        synchronized (Executive.this) {
                            if (dataSnapshot == null) {
                                return;
                            }
                            if (dataSnapshot.hasChild("cancel")) {
                                mCancelledGoals.put(dataSnapshot.getKey(), new Date().getTime());
                            }
                            if (dataSnapshot.hasChild("complete")) {
                                mCompletedGoals.put(dataSnapshot.getKey(), new Date().getTime());
                            }
                            if (dataSnapshot.hasChild("reject")) {
                                mRejectedGoals.put(dataSnapshot.getKey(), new Date().getTime());
                            }
                        }
                        cleanUpRobotGoals(NUM_GOALS_TO_LEAVE);
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                        synchronized (Executive.this) {
                            if (dataSnapshot != null) {
                                mCancelledGoals.put(dataSnapshot.getKey(), new Date().getTime());
                            }
                        }
                        cleanUpRobotGoals(NUM_GOALS_TO_LEAVE);
                    }

                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                        synchronized (Executive.this) {
                            if (s != null) {
                                mCancelledGoals.put(s, new Date().getTime());
                            }
                        }
                        onChildAdded(dataSnapshot, s);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
    }

    /**
     * Creates a timed loop for this executive planner.
     *
     * @return Timed loop object.
     */
    private TimedLoop createTimedLoop() {
        return new TimedLoop(TAG, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                Executive.this.update();
                return true;
            }

            @Override
            public void shutdown() {
                mExecutiveStateCommandMonitor.shutdown();
                mObjectsMonitor.shutdown();
                CloudPath.ROBOT_GOALS_PATH
                        .getDatabaseReference(mSession.getUserUuid(), mSession.getRobotUuid())
                        .removeEventListener(mGoalsListener);
                onShutdown();
            }
        }, UPDATE_TIME_MILLISECOND);
    }

    /**
     * Starts the executive planner. Called after sounds, animations, and map are synchronized.
     */
    public void enable() {
        mEnable = true;
    }

    /**
     * Sets the robot's metadata.
     *
     * @param metadata The robot's metadata.
     */
    public void setMetadata(RobotMetadata metadata) {
        mMetadata = metadata;
    }

    /**
     * Sets the robot's current position.
     *
     * @param transform The transform for the current robot position.
     */
    public void setTransform(Transform transform) {
        mTransform = transform;
    }

    /**
     * Sets the battery state from the robot.
     *
     * @param low True if the battery state is low.
     * @param critical True if the battery state is critical.
     */
    public void setBatteryState(boolean low, boolean critical) {
        mBatteryLow = low;
        mBatteryCritical = critical;
    }

    /**
     * Removes the given goals in the goal store - e.g. rejected and completed goals. First removes
     * all goals from the mGoals and then removes timed out entries for the given goal.
     *
     * @param goalStateStore The goal state store to update and update from.
     * @param timestamp      The update start timestamp.
     */
    private synchronized void removeGoalsInExecutive(
            Map<String, Long> goalStateStore, long timestamp) {
        LinkedList<String> removeGoals = new LinkedList<>();
        for (Map.Entry<String, Long> goalState : goalStateStore.entrySet()) {
            if (mGoals.containsKey(goalState.getKey())) {
                mGoals.remove(goalState.getKey());
            }
            if (goalState.getValue() - PROCESS_TIMEOUT_MILLISECOND > timestamp) {
                removeGoals.add(goalState.getKey());
            }
        }
        for (String goal : removeGoals) {
            goalStateStore.remove(goal);
        }
    }

    /**
     * Updates the executive planner internally. Called at every UPDATE_TIME_MILLISECOND.
     */
    private synchronized void update() {
        long timestamp = new Date().getTime();
        Transform tf = mTransform;
        ExecutiveStateCommand.ExecutiveState executiveState;

        // Do not start unless we are localized.
        if (tf == null) {
            return;
        }

        // Do nothing while we are mapping.
        if (mSession.getWorld() == null) {
            mActionMediator.setAction(null);
            return;
        }

        // If we have not loaded objects or been enabled yet, exit out.
        if (!mEnable || !mObjectsLoaded) {
            return;
        }

        // Update the state of the executive planner monitor.
        mExecutiveStateCommandMonitor.update(mSession.getUserUuid(), mSession.getRobotUuid());
        if (!mExecutiveStateCommandMonitor.isSynchronized()) {
            return;
        }
        if (mExecutiveStateCommandMonitor.getCurrent() != null) {
            executiveState = mExecutiveStateCommandMonitor.getCurrent().getExecutiveMode();
        } else {
            executiveState = ExecutiveStateCommand.DEFAULT_EXECUTIVE_MODE;
        }

        // Repeat until there are no new goals or we are waiting for a goal to complete.
        while (true) {
            // Remove all the rejected and completed goals from the local storage.
            removeGoalsInExecutive(mRejectedGoals, timestamp);
            removeGoalsInExecutive(mCompletedGoals, timestamp);

            // Create the world state.
            WorldState worldState = new WorldState(getSession().getWorld(), mMetadata, tf,
                    mObjects, mBatteryLow, mBatteryCritical, executiveState);

            // Compute the best goal.
            Goal bestGoal = getBestGoal(worldState, mGoals.values());

            if (bestGoal != null) {
                // If the goal is already cancelled, then we must reject it and continue the search
                // for a new goal for the robot
                if (mCancelledGoals.containsKey(bestGoal.getUuid())) {
                    rejectGoal(bestGoal);
                    continue;
                }
            }

            // Flags for processGoal().
            boolean preempt = false;
            boolean newGoal = false;
            boolean cancel;

            // If we have no current goal, set the current goal to best the best goal.
            if (mCurrentGoal == null) {
                if (bestGoal == null) {
                    // If we have no best goal, then we do not need to process any goal.
                    getActionMediator().setAction(null);
                    break;
                }
                if (worldState.getState(Types.WorldStateKey.ROBOT_EXECUTIVE_STATE)
                        == ExecutiveStateCommand.ExecutiveState.STOP
                        && bestGoal.getPriority() < MIN_PRIORITY_USER_GOAL) {
                    // If the best goal has too low a priority, we keep it in queue.
                    getActionMediator().setAction(null);
                    break;
                }
                newGoal = true;
                mCurrentGoal = bestGoal;
            } else if (bestGoal != null && !bestGoal.equals(mCurrentGoal)
                    && mCurrentGoal.getPriority() < bestGoal.getPriority()) {
                // If we have a best goal and the best goal has higher priority than the current
                // goal, we request preemption of the lower-priority goal.
                preempt = true;
            }

            // If we have a current goal, and the goal is to be cancelled, then set the flag.
            cancel = mCancelledGoals.containsKey(mCurrentGoal.getUuid());

            // Preempt if we are not allowing random goals.
            if (worldState.getState(Types.WorldStateKey.ROBOT_EXECUTIVE_STATE)
                    == ExecutiveStateCommand.ExecutiveState.STOP
                    && mCurrentGoal.getPriority() < MIN_PRIORITY_USER_GOAL) {
                preempt = true;
            }

            // Process the goal.
            GoalState state = processGoal(worldState, mCurrentGoal, newGoal, cancel, preempt);

            // Handle the result state.
            if (state == GoalState.PREEMPT) {
                // If the preempt was accepted, clear out the current goal so we can pick a new one.
                if (!preempt) {
                    throw new Error("Executive planner is not properly implemented - returned "
                            + " preempt state without preempt for goal: " + mCurrentGoal);
                }
                mCurrentGoal = null;
            } else if (state == GoalState.COMPLETE) {
                completeGoal(mCurrentGoal);
                mCurrentGoal = null;
            } else if (state == GoalState.REJECT) {
                rejectGoal(mCurrentGoal);
                mCurrentGoal = null;
            } else if (state == GoalState.RUNNING) {
                // The goal is running is we need to wait until a future update to the executive
                // planner before we complete the goal
                break;
            }
        }

        // Call onUpdate() so the executive can do internal work.
        onUpdate();
    }

    /**
     * Rejects a goal.
     *
     * @param goalUuid The uuid of the goal to reject.
     */
    private void rejectGoal(String goalUuid) {
        mRejectedGoals.put(goalUuid, new Date().getTime());
        Map<String, Object> childMap = new HashMap<>();
        childMap.put("reject", "true");
        childMap.put("reject_timestamp", ServerValue.TIMESTAMP);
        CloudPath.ROBOT_GOALS_PATH_ENTITY.getDatabaseReference(mSession.getUserUuid(),
                mSession.getRobotUuid(), goalUuid).updateChildren(childMap);
    }

    /**
     * Completes a goal.
     *
     * @param goalUuid The uuid of the goal to complete.
     */
    private void completeGoal(String goalUuid) {
        mCompletedGoals.put(goalUuid, new Date().getTime());
        Map<String, Object> childMap = new HashMap<>();
        childMap.put("complete", "true");
        childMap.put("complete_timestamp", ServerValue.TIMESTAMP);
        CloudPath.ROBOT_GOALS_PATH_ENTITY.getDatabaseReference(mSession.getUserUuid(),
                mSession.getRobotUuid(), goalUuid).updateChildren(childMap);
    }

    /**
     * Rejects a goal.
     *
     * @param goal The goal to reject.
     */
    private void rejectGoal(Goal goal) {
        rejectGoal(goal.getUuid());
    }

    /**
     * Completes a goal.
     *
     * @param goal The goal to complete.
     */
    private void completeGoal(Goal goal) {
        completeGoal(goal.getUuid());
    }

    /**
     * Finds the best goal in a collection of goals.
     *
     * @param worldState The state of the planner's current world.
     * @param goals      The goals collection.
     * @return The best goal, or null if no goal can be executed.
     */
    private Goal getBestGoal(WorldState worldState, Collection<Goal> goals) {
        // Sort the currently acceptable goals by priority
        ArrayList<Goal> sortedGoals = new ArrayList<>(goals.size());
        sortedGoals.addAll(goals);
        Collections.sort(sortedGoals);

        // Find the best goal by looping through the sorted goals.
        for (Goal goal : sortedGoals) {
            if (mGoalTypes.get(goal.getType()).isQuery()) {
                if (query(worldState, goal)) {
                    return goal;
                }
            } else {
                return goal;
            }
        }
        return null;
    }

    /**
     * Gets the action mediator.
     *
     * @return The action mediator.
     */
    @SuppressWarnings("WeakerAccess")
    protected final ActionMediator getActionMediator() {
        return mActionMediator;
    }

    /**
     * Gets the sound manager.
     *
     * @return The sound manager.
     */
    @SuppressWarnings("WeakerAccess")
    protected final SoundManager getSoundManager() {
        return mSoundManager;
    }

    /**
     * Gets the animation manager.
     *
     * @return Animation manager object.
     */
    @SuppressWarnings("WeakerAccess")
    protected final AnimationManager getAnimationManager() {
        return mAnimationManager;
    }

    /**
     * Gets the session.
     *
     * @return The session.
     */
    @SuppressWarnings("WeakerAccess")
    protected final RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Called to query whether a goal is possible to process or not. This method could be called
     * more than once per onUpdate() or not at all.
     *
     * @param worldState The state of the planner's current world.
     * @param goal       The goal to query for.
     * @return True if the goal can be started.
     */
    protected abstract boolean query(WorldState worldState, Goal goal);

    /**
     * Processes a goal. This method could be called more than once per onUpdate() call or not
     * at all.
     *
     * @param worldState The state of the planner's current world.
     * @param goal       The current goal to process.
     * @param isNew      True if the goal is new.
     * @param cancel     True if the goal should be cancelled.
     * @param preempt    True if the goal should be preempted.
     * @return The goal state.
     */
    protected abstract GoalState processGoal(WorldState worldState, Goal goal,
            boolean isNew, boolean cancel, boolean preempt);

    /**
     * Updates the executive. Called after all goals have been processed.
     */
    protected abstract void onUpdate();

    /**
     * Shuts down the executive.
     */
    @SuppressWarnings("EmptyMethod")
    protected abstract void onShutdown();

    /**
     * Shuts down the executive.
     */
    @Override
    final public void shutdown() {
        mTimedLoop.shutdown();
    }

    /**
     * Waits for the executive manager to shut down.
     */
    @Override
    final public void waitShutdown() {
        mTimedLoop.waitShutdown();
    }

    /**
     * Cleans up a user's robot goals. This code moves all goals that are completed except for the
     * last few by completion time into the robot's old_goals tab. This speeds up the visualization
     * and helps ensure that old goal removal (the process of removing such goals from cloud and
     * putting them in another database) occurs fast.
     *
     * @param goalsToLeave The number of goals to leave when clearing the robot goals.
     */
    private void cleanUpRobotGoals(final int goalsToLeave) {
        final String userUuid = mSession.getUserUuid();
        final String robotUuid = mSession.getRobotUuid();
        CloudPath.ROBOT_GOALS_PATH.getDatabaseReference(userUuid, robotUuid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // List all goals that are completed or rejected and have timestamps.
                        LinkedList<DataSnapshot> blackList = new LinkedList<>();
                        for (DataSnapshot c : dataSnapshot.getChildren()) {
                            if ((c.hasChild("complete") || c.hasChild("reject"))
                                    && (c.hasChild("complete_timestamp")
                                    || c.hasChild("reject_timestamp"))) {
                                blackList.add(c);
                            }
                        }
                        // Sort the goals by completed or rejected timestamps, in ascending order.
                        Collections.sort(blackList, new Comparator<DataSnapshot>() {
                            @Override
                            public int compare(DataSnapshot o1, DataSnapshot o2) {
                                long o1Time = o1.hasChild("complete_timestamp")
                                        ? (long) o1.child("complete_timestamp").getValue()
                                        : (long) o1.child("reject_timestamp").getValue();
                                long o2Time = o2.hasChild("complete_timestamp")
                                        ? (long) o2.child("complete_timestamp").getValue()
                                        : (long) o2.child("reject_timestamp").getValue();
                                if (o1Time == o2Time) {
                                    return 0;
                                }
                                return o1Time < o2Time ? -1 : 1;
                            }
                        });

                        // Remove goals until 'goalsToLeave' goals remain.
                        while (blackList.size() > goalsToLeave) {
                            DataSnapshot migrate = blackList.getFirst();

                            // Copy the data into the requisite old_goals key.
                            CloudPath.ROBOT_OLD_GOALS_PATH_ENTITY.getDatabaseReference(userUuid,
                                    robotUuid, migrate.getKey()).setValue(migrate.getValue());

                            // Create a removal transaction, only remove the goal if it was the
                            // same as the one written to old_goals. This prevents deletion of a
                            // goal that is being modified during the copy of the goal.
                            final Object compare = migrate.getValue();
                            CloudPath.ROBOT_GOALS_PATH_ENTITY.getDatabaseReference(userUuid,
                                    robotUuid, migrate.getKey()).runTransaction(
                                    new Transaction.Handler() {
                                        @Override
                                        public Transaction.Result doTransaction(
                                                MutableData mutableData) {
                                            // If the compare is correct, we null out the value.
                                            if (compare != null && compare.equals(
                                                    mutableData.getValue())) {
                                                mutableData.setValue(null);
                                            }
                                            return Transaction.success(mutableData);
                                        }

                                        @Override
                                        public void onComplete(DatabaseError databaseError,
                                                boolean b, DataSnapshot dataSnapshot) {
                                        }
                                    });

                            blackList.remove(0);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
    }
}
