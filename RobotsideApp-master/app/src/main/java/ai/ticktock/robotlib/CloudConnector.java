package ai.cellbots.robotlib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.cellbots.common.Strings;
import ai.cellbots.common.Transform;
import ai.cellbots.common.World;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudStoredSingletonMonitor;
import ai.cellbots.common.cloud.TimestampManager;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.LogMessage;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.data.SmootherParams;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.ExecutivePlannerManager;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.robotapp.R;

/**
 * Beams up data to firebase and reads it down for the robot side app. Handles analytics,
 * executive planner goals and objects, robot state publication, metadata and commands.
 */
class CloudConnector implements ExecutivePlannerManager.Listener {
    @SuppressWarnings("unused")
    private static final String TAG = CloudConnector.class.getSimpleName();
    private final Context mParent;

    private FirebaseAnalyticsEvents mFirebaseEventLogger;

    private String mRobotUuid = null;
    private String mRobotVersion = null;
    private World mWorld = null;
    private String mLastUserId = null;
    private String mRobotState = null;
    private boolean mHaveLoggedData = false;

    private Transform mTransform = null;
    private double mTraversed = 0.0;

    // TODO: this value should be stored in common, to be accessed both by the companion app to set
    // goal priorities or by the robot app to query it.
    private static final long DRIVE_TO_LOCATION_PRIORITY = 100;

    // The maximum robot speed in m/s. If a robot travels faster than this speed, it is considered
    // either movement by an external force (e.g., hand carry) or relocalization. Also, any movement
    // faster than this will not be logged as traveled distance.
    private static final double MAX_LOGICAL_SPEED = 2.0;

    private final ExecutivePlannerManager mExecutive;

    private final CloudStoredSingletonMonitor<RobotMetadata> mMetadataMonitor;

    private final CloudStoredSingletonMonitor<Teleop> mTeleopMonitor;

    private final CloudAnimationManager mCloudAnimationManager;
    private final LinkedList<LogMessage> mLogMessages = new LinkedList<>();
    // Time to keep messages in the log in milliseconds
    private static final long LOG_MESSAGE_BUFFER_TIME = 10000;
    // Last time robot log state was updated
    private long mLastRobotUpdateTime;

    /**
     * Create the connector.
     *
     * @param parent           The parent Android context.
     * @param executivePlanner The executive planner.
     */
    CloudConnector(Context parent, ExecutivePlannerManager executivePlanner) {
        mParent = parent;
        mExecutive = executivePlanner;
        mExecutive.addListener(this);
        mCloudAnimationManager = new CloudAnimationManager(parent);
        mFirebaseEventLogger = FirebaseAnalyticsEvents.getInstance();

        mMetadataMonitor = new CloudStoredSingletonMonitor<>(parent, new Object(),
                CloudPath.ROBOT_METADATA_PATH, RobotMetadata.FACTORY);

        mTeleopMonitor = new CloudStoredSingletonMonitor<>(parent, new Object(),
                CloudPath.ROBOT_TELEOP_PATH, Teleop.FACTORY);

        TimestampManager.synchronize();
    }

    /**
     * Log a robot event, inferring the robot's version and world
     *
     * @param userUuid     The user's UUID.
     * @param robotUuid    The robot's UUID.
     * @param statusString A string represented the status
     */
    private void logStatusForRobot(String userUuid, String robotUuid, String statusString) {
        String version = null;
        synchronized (this) {
            if (stringCompare(robotUuid, mRobotUuid)) {
                version = mRobotVersion;
            }
        }
        logStatus(userUuid, robotUuid, version, mWorld, statusString);
    }

    /**
     * Log a robot event.
     *
     * @param userUuid     The user's UUID. If null, it will be firebase's user.
     * @param robotUuid    The robot's UUID.
     * @param robotVersion The robot's version.
     * @param world        The world.
     * @param statusString A string representing the status.
     */
    public void logStatus(String userUuid, String robotUuid, String robotVersion, World world,
            String statusString) {
        String worldUuid = (world != null) ? world.getUuid() : null;
        mFirebaseEventLogger.logStatusEvent(mParent, robotUuid, robotVersion, worldUuid,
                statusString);

        // Comment status in the robot state
        mRobotState = statusString;

        // Store the log message
        if (userUuid == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                synchronized (mLogMessages) {
                    mLogMessages.add(new LogMessage(user.getUid(), robotUuid,
                            statusString, new Date().getTime()));
                }
            }
        } else {
            synchronized (mLogMessages) {
                mLogMessages.add(new LogMessage(userUuid, robotUuid,
                        statusString, new Date().getTime()));
            }
        }
    }

    /**
     * Cancel all goals for a robot and user.
     *
     * @param userUuid  The user's uuid
     * @param robotUuid The robot's uuid.
     */
    public void cancelAllRobotGoals(final String userUuid, final String robotUuid) {
        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(userUuid).child(robotUuid).child("goals")
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot == null) {
                            return;
                        }
                        if (dataSnapshot.getChildrenCount() <= 0) {
                            return;
                        }
                        for (DataSnapshot child : dataSnapshot.getChildren()) {
                            FirebaseDatabase.getInstance().getReference("robot_goals")
                                    .child(userUuid).child(robotUuid).child("goals")
                                    .child(child.getKey()).child("cancel").setValue(true);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
    }

    /**
     * Clean up a user's robot goals. This code moves all goals that are completed except for the
     * last five by completion time into the robot's old_goals tab. This speeds up the visualization
     * and helps ensure that old goal removal (the process of removing such goals from firebase and
     * putting them in another database) occurs fast.
     *
     * @param userUuid  The user's uuid
     * @param robotUuid The robot's uuid.
     */
    private void cleanUpRobotGoals(final String userUuid, final String robotUuid) {
        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(userUuid).child(robotUuid).child("goals")
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

                        // Remove goals until there are only five left.
                        while (blackList.size() > 5) {
                            DataSnapshot migrate = blackList.getFirst();

                            // Copy the data into the requisite old_goals key
                            FirebaseDatabase.getInstance().getReference("robot_goals")
                                    .child(userUuid).child(robotUuid).child("old_goals")
                                    .child(migrate.getKey()).setValue(migrate.getValue());

                            // Create a removal transaction, only remove the goal if it was the
                            // same as the one written to old_goals. This prevents deletion of a
                            // goal that is being modified during the copy of the goal.
                            final Object compare = migrate.getValue();
                            FirebaseDatabase.getInstance().getReference("robot_goals")
                                    .child(userUuid).child(robotUuid).child("goals")
                                    .child(migrate.getKey()).runTransaction(
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

    /**
     * Sets the metadata of the robot.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid The robot uuid.
     */
    private void updateMetadataListener(final String userUuid, final String robotUuid) {
        mMetadataMonitor.update(userUuid, robotUuid);
        mExecutive.setRobotMetadata(mMetadataMonitor.getCurrent());
    }

    /**
     * Gets the metadata of the robot if available.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid The robot uuid.
     * @return The metadata.
     */
    public RobotMetadata getMetadata(final String userUuid, final String robotUuid) {
        RobotMetadata m = mMetadataMonitor.getCurrent();
        if (m != null && userUuid != null && robotUuid != null
                && userUuid.equals(m.getUserUuid()) && robotUuid.equals(m.getRobotUuid())) {
            return m;
        }
        return null;
    }


    /**
     * Gets the teleop of the robot.
     *
     * @return The teleop
     */
    public Teleop getTeleop() {
        return mTeleopMonitor.getCurrent();
    }

    /**
     * Sets the teleop of the robot.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid The robot uuid.
     */
    private void updateTeleopListener(final String userUuid, final String robotUuid) {
        mTeleopMonitor.update(userUuid, robotUuid);
    }

    /**
     * Log a robot position update.
     *
     * @param robotUuid       The robot's UUID.
     * @param robotVersion    The robot's version.
     * @param isLocalized     The robot's localization state.
     * @param world           The world.
     * @param location        The new location.
     * @param baseLocation    The location of the robot base.
     * @param batteryStatuses The status of the robot base batteries.
     * @param path            The robot's current path.
     * @param mappingRunId    The robot's current mapping run.
     * @return True if a new session has been created.
     */
    boolean logUpdate(String robotUuid, String robotVersion, boolean isLocalized, World world,
                      Transform location, Transform baseLocation, BatteryStatus[] batteryStatuses,
                      List<Transform> path, String mappingRunId) {
        TimestampManager.synchronize();
        if (!TimestampManager.isSynchronized()) {
            return false;
        }

        boolean newSession = !mHaveLoggedData;
        mHaveLoggedData = true;

        String currentUserId = null;
        synchronized (this) {
            // Update the robot version and UUID.
            if (!stringCompare(robotUuid, mRobotUuid)) {
                mRobotUuid = robotUuid;
                newSession = true;
            }
            if (!stringCompare(robotVersion, mRobotVersion)) {
                mRobotVersion = robotVersion;
                newSession = true;
            }

            // New session if we switch worlds
            FirebaseAuth fbAuth = FirebaseAuth.getInstance();
            if (fbAuth != null) {
                FirebaseUser fbUser = fbAuth.getCurrentUser();
                if (fbUser != null) {
                    currentUserId = fbUser.getUid();
                }
            }

            // New session if a new user
            if (!stringCompare(currentUserId, mLastUserId)) {
                mLastUserId = currentUserId;
                newSession = true;
            }
        }

        // If we switched worlds new session
        String deleteMapUuid = null;
        if (world != mWorld) {
            if (mWorld != null) {
                deleteMapUuid = mWorld.getUuid();
            }
            mWorld = world;
            newSession = true;
        }

        // Manage the metadata goals.
        updateMetadataListener(currentUserId, mRobotUuid);

        // Manage the teleop commands
        updateTeleopListener(currentUserId, mRobotUuid);

        // If the robot is connected, has a valid world, is localized, and has a user id, then we
        // upload the current position of the robot to firebase, and afterwards set the robot
        // firebase relations to ensure the clients can find the robot.
        if ((mRobotUuid != null) && (currentUserId != null)
                && mMetadataMonitor.isSynchronized()) {
            final String userId = currentUserId;
            final String deleteMap = deleteMapUuid;
            final String setRobot = mRobotUuid;
            final String setWorld = mWorld != null ? mWorld.getUuid() : null;
            final long updateTime = new Date().getTime();
            Map<String, Object> robotState = new HashMap<>();
            robotState.put("last_update_time", ServerValue.TIMESTAMP);
            robotState.put("local_time", updateTime);
            robotState.put("uuid", setRobot);
            robotState.put("localized", isLocalized);
            if (mappingRunId != null) {
                robotState.put("mapping_run_id", mappingRunId);
            }
            if (baseLocation != null) {
                robotState.put("tf", baseLocation.toMap());
            }
            robotState.put("version", mRobotVersion);
            if (setWorld != null) {
                robotState.put("map", setWorld);
            }
            robotState.put("state", mRobotState);
            Map<String, Object> pathMap = new HashMap<>();
            robotState.put("path", pathMap);
            if (path != null) {
                for (int i = 0; i < path.size(); i++) {
                    Transform goal = path.get(i);
                    String goalName = "goal_" + i;
                    pathMap.put(goalName, goal.toMap());
                }
            }
            Map<String, Object> batteryStateMap = new HashMap<>();
            robotState.put("batteries", batteryStateMap);
            if (batteryStatuses != null) {
                for (BatteryStatus status : batteryStatuses) {
                    batteryStateMap.put(status.getName(), status.toFirebase());
                }
            }

            // Add all log messages to the robot that are for the correct robot, and remove
            // messages that have timed out from the log messages store.
            Map<String, Object> messageMap = new HashMap<>();
            synchronized (mLogMessages) {
                LinkedList<LogMessage> removeMessage = new LinkedList<>();
                for (LogMessage m : mLogMessages) {
                    if (setRobot.equals(m.getRobotUuid()) && userId.equals(m.getUserUuid())) {
                        messageMap.put(m.getUuid(), m);
                    }
                    if (m.getTimestamp() < updateTime - LOG_MESSAGE_BUFFER_TIME) {
                        removeMessage.add(m);
                    }
                }
                mLogMessages.removeAll(removeMessage);
            }
            robotState.put("log_messages", messageMap);

            RobotMetadata metadata = mMetadataMonitor.getCurrent();
            if (metadata != null) {
                robotState.put("name", metadata.getRobotName());
            }
            // Set the key robots/<user>/<robot_uuid>
            FirebaseDatabase.getInstance().getReference("robots").child(currentUserId)
                    .child(setRobot).setValue(robotState,
                    new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(DatabaseError databaseError,
                                DatabaseReference databaseReference) {
                            // Set the maps/<user>/<map_uuid>/robots/<robot_uuid> so that the robot
                            // can be found by clients scanning the map
                            if (setWorld != null) {
                                FirebaseDatabase.getInstance().getReference("maps").child(userId)
                                        .child(setWorld).child("robots")
                                        .child(setRobot).setValue("true");
                            }
                            // If we have an old map (I.E. we just transitioned maps) then delete
                            // the
                            // maps/<user>/<map_uuid>/robots/<robot_uuid>. This will be fine even
                            // if we
                            // are unable to set the value, since the robot will
                            if (deleteMap != null) {
                                FirebaseDatabase.getInstance().getReference("maps").child(userId)
                                        .child(deleteMap).child("robots").child(
                                        setRobot).removeValue();
                            }
                        }
                    });

            // Manage the goals from the executive.
            manageExecutive(currentUserId);

            // Manage the animations
            mCloudAnimationManager.update(currentUserId, mRobotUuid, mExecutive);
        }

        if (newSession) {
            // In a new session, write out the last distance regardless of amount if it is non-zero.
            if (mTraversed != 0.0) {
                Log.d(TAG, "Sending event on new session: " + mTraversed);
                String worldUuid = (world != null) ? world.getUuid() : null;
                mFirebaseEventLogger.logDistanceEvent(mParent, mRobotUuid, mRobotVersion, worldUuid,
                        mTraversed);
            }
            mTransform = null;
            mTraversed = 0.0;
        } else {
            if (mTraversed > 1.0) {
                String worldUuid = (world != null) ? world.getUuid() : null;
                mFirebaseEventLogger.logDistanceEvent(mParent, mRobotUuid, mRobotVersion, worldUuid,
                        mTraversed);
                mTraversed = 0.0;
            }
        }

        long currentTime = System.currentTimeMillis();
        if ((mTransform != null) && (location != null)) {
            // Since this does not require quick computation, using sqrt() is ok.
            double distanceFromLastPosition =
                    Math.sqrt(mTransform.planarDistanceToSquared(location));
            double secondsFromLastPosition = (currentTime - mLastRobotUpdateTime) / 1000.0;
            // Make sure before report that speed of the robot is logical
            if (distanceFromLastPosition < MAX_LOGICAL_SPEED * secondsFromLastPosition) {
                mTraversed += distanceFromLastPosition;
            } else {
                Log.e(TAG, "Travel report skipped: traveled " + distanceFromLastPosition +
                        " m within " + secondsFromLastPosition + " seconds");
                logStatusForRobot(currentUserId, mRobotUuid,
                        mParent.getString(R.string.robot_state_impossible_speed));
            }
        }
        mTransform = location;
        mLastRobotUpdateTime = currentTime;

        return newSession;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean stringCompare(String s1, String s2) {
        return Strings.compare(s1, s2);
    }

    // Keep track of which robot the we are listening to in the cloud
    private String mLastWatchRobotUuid = null;
    private String mLastWatchUserId = null;
    private String mLastWatchMapId = null;
    private ChildEventListener mRobotListener = null;
    private ChildEventListener mObjectListener = null;

    // Store the goals from the executive
    private final Set<MacroGoal> mCompletedGoals = new HashSet<>();
    private final Set<MacroGoal> mRejectedGoals = new HashSet<>();

    private void manageExecutive(String currentUserId) {
        boolean forceObjectReload = false;
        // If the robot's uuid or the user id have changed, reload the goal state monitor.
        if (!stringCompare(mLastWatchRobotUuid, mRobotUuid)
                || !stringCompare(mLastWatchUserId, currentUserId)) {
            if (mLastWatchRobotUuid != null && mLastWatchUserId != null && mRobotListener != null) {
                FirebaseDatabase.getInstance().getReference("robot_goals")
                        .child(mLastWatchUserId)
                        .child(mLastWatchRobotUuid)
                        .child("goals").removeEventListener(mRobotListener);
            }
            if (mLastWatchUserId != null && mLastWatchMapId != null && mObjectListener != null) {
                FirebaseDatabase.getInstance().getReference("objects")
                        .child(mLastWatchUserId)
                        .child(mLastWatchMapId).removeEventListener(mObjectListener);
                mObjectListener = null;
            }
            mLastWatchRobotUuid = mRobotUuid;
            mLastWatchUserId = currentUserId;
            synchronized (this) {
                mCompletedGoals.clear();
                mRejectedGoals.clear();
            }
            if (mLastWatchRobotUuid != null && mLastWatchUserId != null) {
                final String robotUuid = mLastWatchRobotUuid;
                final String robotUserUuid = mLastWatchUserId;
                mExecutive.setUserAndRobotUuid(robotUserUuid, robotUuid);
                mRobotListener = FirebaseDatabase.getInstance().getReference("robot_goals")
                        .child(robotUserUuid)
                        .child(robotUuid)
                        .child("goals")
                        .addChildEventListener(new ChildEventListener() {
                            @SuppressWarnings("unchecked")
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                mExecutive.addGoal((Map<String, Object>) dataSnapshot.getValue(),
                                        robotUserUuid, robotUuid);
                                logStatusForRobot(robotUserUuid, robotUuid,
                                        mParent.getString(R.string.robot_state_new_goal_added));
                                // Report "Goal Added" event to Firebase Analytics.
                                mFirebaseEventLogger.reportGoalAddedFirebaseEvent(mParent);
                            }

                            @SuppressWarnings("unchecked")
                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                mExecutive.addGoal((Map<String, Object>) dataSnapshot.getValue(),
                                        robotUserUuid, robotUuid);
                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {
                                mExecutive.cancelGoal(dataSnapshot.getKey());
                            }

                            @SuppressWarnings("unchecked")
                            @Override
                            public void onChildMoved(DataSnapshot dataSnapshot, String fromName) {
                                mExecutive.cancelGoal(fromName);
                                mExecutive.addGoal((Map<String, Object>) dataSnapshot.getValue(),
                                        robotUserUuid, robotUuid);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                            }
                        });
                FirebaseDatabase.getInstance().getReference("robot_goals")
                        .child(robotUserUuid)
                        .child(robotUuid)
                        .child("goals")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot goal : dataSnapshot.getChildren()) {
                                    mExecutive.addGoal((Map<String, Object>) goal.getValue(),
                                            robotUserUuid, robotUuid);
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                            }
                        });
                cleanUpRobotGoals(robotUserUuid, robotUuid);
                forceObjectReload = true;
            }
        }

        // If we have changed maps or reloaded the executive then update.
        World w = mWorld;
        final String currentMap = w != null ? w.getUuid() : null;
        if (forceObjectReload || !stringCompare(mLastWatchMapId, currentMap)) {
            if (mLastWatchUserId != null && mLastWatchMapId != null && mObjectListener != null) {
                FirebaseDatabase.getInstance().getReference("objects")
                        .child(mLastWatchUserId)
                        .child(mLastWatchMapId).removeEventListener(mObjectListener);
                mObjectListener = null;
            }
            mLastWatchMapId = currentMap;
            if (mLastWatchRobotUuid != null && mLastWatchUserId != null
                    && mLastWatchMapId != null) {
                final int lockoutId = mExecutive.setObjectLockout();
                FirebaseDatabase.getInstance().getReference("objects")
                        .child(mLastWatchUserId)
                        .child(mLastWatchMapId).addChildEventListener(new ChildEventListener() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        mExecutive.addObject(dataSnapshot.getKey(),
                                (Map<String, Object>) dataSnapshot.getValue(),
                                currentMap, lockoutId);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                        mExecutive.addObject(dataSnapshot.getKey(),
                                (Map<String, Object>) dataSnapshot.getValue(),
                                currentMap, lockoutId);
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                        mExecutive.removeObject(dataSnapshot.getKey(), lockoutId);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String fromName) {
                        mExecutive.removeObject(fromName, lockoutId);
                        mExecutive.addObject(dataSnapshot.getKey(),
                                (Map<String, Object>) dataSnapshot.getValue(),
                                currentMap, lockoutId);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
                FirebaseDatabase.getInstance().getReference("objects")
                        .child(mLastWatchUserId)
                        .child(mLastWatchMapId).addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot object : dataSnapshot.getChildren()) {
                                    mExecutive.addObject(object.getKey(),
                                            (Map<String, Object>) object.getValue(),
                                            currentMap, lockoutId);
                                }
                                mExecutive.clearObjectLockout(lockoutId);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                            }
                        });
            }
        }

        // Clean out the rejected/set goals
        if (mLastWatchRobotUuid != null && mLastWatchUserId != null && mRobotListener != null) {
            boolean updatedGoals = false;
            synchronized (this) {
                for (MacroGoal goal : mRejectedGoals) {
                    if (!mLastWatchUserId.equals(goal.getUserUuid())) {
                        continue;
                    }
                    if (!mLastWatchRobotUuid.equals(goal.getRobotUuid())) {
                        continue;
                    }
                    Map<String, Object> childMap = new HashMap<>();
                    childMap.put("reject", "true");
                    childMap.put("reject_timestamp", ServerValue.TIMESTAMP);
                    FirebaseDatabase.getInstance().getReference("robot_goals")
                            .child(mLastWatchUserId)
                            .child(mLastWatchRobotUuid)
                            .child("goals")
                            .child(goal.getUuid())
                            .updateChildren(childMap);
                    updatedGoals = true;
                }
                for (MacroGoal goal : mCompletedGoals) {
                    if (!mLastWatchUserId.equals(goal.getUserUuid())) {
                        continue;
                    }
                    if (!mLastWatchRobotUuid.equals(goal.getRobotUuid())) {
                        continue;
                    }
                    Map<String, Object> childMap = new HashMap<>();
                    childMap.put("complete", "true");
                    childMap.put("complete_timestamp", ServerValue.TIMESTAMP);
                    FirebaseDatabase.getInstance().getReference("robot_goals")
                            .child(mLastWatchUserId)
                            .child(mLastWatchRobotUuid)
                            .child("goals")
                            .child(goal.getUuid())
                            .updateChildren(childMap);
                    updatedGoals = true;
                }
                mRejectedGoals.clear();
                mCompletedGoals.clear();
            }
            if (updatedGoals) {
                cleanUpRobotGoals(mLastWatchUserId, mLastWatchRobotUuid);
            }
        }
    }

    /**
     * Called by the ExecutivePlannerManager when a goal is rejected.
     *
     * @param goal   The goal that is rejected.
     * @param reason why the goal is rejected.
     */
    @Override
    public void onGoalRejected(MacroGoal goal, String reason) {
        synchronized (this) {
            mRejectedGoals.add(goal);
        }
        if (reason != null) {
            logStatusForRobot(goal.getUserUuid(), goal.getRobotUuid(), reason);
            mFirebaseEventLogger.reportGoalRejectedFirebaseEvent(mParent, reason);
        } else {
            // if no reason for rejecting the goal was set, mark it as unknown.
            logStatusForRobot(goal.getUserUuid(), goal.getRobotUuid(),
                    mParent.getString(R.string.robot_state_reject_unknown));
            mFirebaseEventLogger.reportGoalRejectedFirebaseEvent(mParent, "Unknown");
        }
    }

    /**
     * Called by the ExecutivePlannerManager when a goal is completed.
     *
     * @param goal The goal that is completed.
     */
    @Override
    public void onGoalCompleted(MacroGoal goal) {
        synchronized (this) {
            mCompletedGoals.add(goal);
        }
        logStatusForRobot(goal.getUserUuid(), goal.getRobotUuid(),
                mParent.getString(R.string.robot_state_goal_completed));
        // Report "Goal Reached" event to Firebase Analytics.
        mFirebaseEventLogger.reportGoalReachedFirebaseEvent(mParent);
    }

    /**
     * Called when a goal is created.
     */
    @Override
    public void onGoalCreated(MacroGoal goal) {
        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(goal.getUserUuid()).child(goal.getRobotUuid()).child("goals")
                .child(goal.getUuid()).setValue(goal.toMap());
        // Report "Goal Added" event to Firebase Analytics
        mFirebaseEventLogger.reportGoalAddedFirebaseEvent(mParent);
    }

    /**
     * Called when a goal is added. If the goal is a drive-to-location goal, the event is reported
     * to Firebase.
     *
     * @param goal The added goal.
     */
    @Override
    public void onGoalAdded(MacroGoal goal) {
        if (goal.getPriority() == DRIVE_TO_LOCATION_PRIORITY) {
            // Report "Drive-to-Location Goal Added" event to Firebase Analytics.
            mFirebaseEventLogger.reportDriveToLocationGoalAddedFirebaseEvent(mParent);
        }
    }

    /**
     * Called when a new planner is created.
     *
     * @param planner The planner created.
     */
    @Override
    public void onNewExecutivePlanner(ExecutivePlanner planner) {
        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(planner.getUserUuid()).child(planner.getRobotUuid())
                .child("goal_types").updateChildren(planner.getMacroMaps());
        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(planner.getUserUuid()).child(planner.getRobotUuid())
                .child("object_types").updateChildren(planner.getObjectTypeMaps());
    }

    /**
     * Called when smoother params are modified by the user. Sends them to Firebase.
     *
     * @param smootherParams to send to Firebase.
     */
    public void onNewSmootherParams(SmootherParams smootherParams) {
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        String userUuid = mMetadataMonitor.getUserUuid();
        String robotUuid = mMetadataMonitor.getRobotUuid();
        if (userUuid == null || robotUuid == null) {
            return;
        }
        if (smootherParams != null) {
            db.child("robot_goals").child(userUuid).child(robotUuid).child(
                    "metadata").child("smoother_params").setValue(smootherParams);
        } else {
            db.child("robot_goals").child(userUuid).child(robotUuid).child(
                    "metadata").child("smoother_params").removeValue();
        }
    }

    /**
     * Gets smoother parameters from updated robot metadata.
     *
     * @return SmootherParams from robot metadata.
     */
    public SmootherParams getSmootherParams() {
        RobotMetadata metadata = mMetadataMonitor.getCurrent();
        if (metadata == null) {
            return null;
        }
        return metadata.getSmootherParams();
    }

    /**
     * Called when device height is modified after a calibration stage. Sends it to Firebase.
     *
     * @param height double to send to Firebase.
     */
    @SuppressLint("DefaultLocale")
    public void onNewTangoDeviceHeight(double height) {
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        String userUuid = mMetadataMonitor.getUserUuid();
        String robotUuid = mMetadataMonitor.getRobotUuid();
        if (userUuid == null || robotUuid == null) {
            return;
        }
        db.child("robot_goals").child(userUuid).child(robotUuid).child(
                "metadata").child("tango_device_height").setValue(String.format("%.2f", height));
    }

    /**
     * Gets tango device height from updated robot metadata.
     *
     * @return Device height from robot metadata.
     */
    public double getTangoDeviceHeight() {
        RobotMetadata metadata = mMetadataMonitor.getCurrent();
        if (metadata == null) {
            Log.v(TAG, "Tried to read metadata but returned null");
            return 0;
        }
        return Double.valueOf(metadata.getTangoDeviceHeight());
    }

    /**
     * Shutdown the system.
     */
    public void shutdown() {
        mCloudAnimationManager.shutdown();
        mMetadataMonitor.shutdown();
        mTeleopMonitor.shutdown();
    }
}

