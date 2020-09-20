package ai.cellbots.common;

import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ai.cellbots.common.data.AnimationInfo;
import ai.cellbots.common.data.DriveGoal;
import ai.cellbots.common.data.DrivePointGoal;
import ai.cellbots.common.data.PatrolDriverGoal;
import ai.cellbots.common.data.PlannerGoal;
import ai.cellbots.common.data.PointOfInterest;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.data.RobotPreferences;
import ai.cellbots.common.data.RobotSound;
import ai.cellbots.common.data.SoundInfo;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.common.data.Transform;
import ai.cellbots.common.data.VacuumSpiralGoal;

/**
 * Encapsulate access to the network.
 */

public class CloudHelper {
    private static final String TAG = CloudHelper.class.getSimpleName();
    private static final String PING_TIME_TAG = TAG + "PingTime";

    private static final String DB_ROBOT_GOALS = "robot_goals";
    private static final String DB_GOALS = "goals";
    private static final String DB_ROBOTS = "robots";
    private static final String DB_OBJECTS = "objects";
    private static final String DB_MAPS = "maps";
    private static final String DB_METADATA = "metadata";
    private static final String DB_TELEOP = "teleop";
    private static final String DB_SOUNDS = "sounds";
    private static final String DB_GLOBAL_SOUNDS = "global_sounds";
    private static final String DB_ANIMATIONS = "animations";
    private static final String DB_USER_PREFERENCES = "user_preferences";

    private static CloudHelper sInstance;

    public synchronized static CloudHelper getInstance() {
        if (sInstance == null) {
            sInstance = new CloudHelper();
        }
        return sInstance;
    }

    public interface ListResultsListener<T> {
        void onResult(List<T> results);
    }

    public interface ResultListener<T> {
        void onResult(T result);
    }

    /**
     * Sets the "ping_time" field on the cloud.
     *
     * Ping time refers to each individual timestamp that's periodically
     * pushed to the cloud. By doing so, a user can determine when the robot app is
     * working properly (ping_time changes constantly) and when the robot app is
     * frozen/hanging (pinging stops).
     *
     * @param robotUuid The robot's uuid.
     * @param timestamp Current timestamp that will be pushed to the cloud. In milliseconds.
     */
    public void setPingTime(String robotUuid, long timestamp) {
        // Sanity checks
        if (!isLoggedIn()) {
            Log.w(TAG, "User needs to be logged in first");
            return;
        }
        if (robotUuid == null) {
            Log.w(TAG, "Robot uuid is null. Cannot update ping time on cloud.");
            return;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOTS)
                .child(getCurrentUserid())
                .child(robotUuid)
                .child("ping_time")
                .setValue(timestamp);

        Log.d(TAG, "Updated ping time on cloud for robot " +  robotUuid + " to: " + timestamp);
    }

    /**
     * Adds a listener for changes in the "ping_time" field on the cloud.
     *
     * Cloud Reference: robots/userId/robotUuid/ping_time
     *
     * @param listener Listener for passing the ping time result back to the caller.
     * @param robotUuid Current robot's uuid.
     *
     * @return A ValueEventListener for changes in the "ping_time" field on the cloud.
     */
    public ValueEventListener addPingTimeListener(final ResultListener<Long> listener, String robotUuid) {
        // Sanity checks
        if (!isLoggedIn()) {
            Log.w(TAG, "User needs to be logged in first before adding ping time listener.");
            return null;
        }
        if (listener == null) {
            Log.w(TAG, "ResultListener must be initialized before adding ping time listener.");
            return null;
        }
        if (robotUuid == null) {
            Log.w(TAG, "Robot uuid is null. Cannot add listener for updating ping time on cloud.");
            return null;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        final DatabaseReference pingTimeRef =
                db.child(DB_ROBOTS).child(getCurrentUserid()).child(robotUuid).child("ping_time");
        ValueEventListener pingTimeListener =
                pingTimeRef.addValueEventListener(new ValueEventListener() {
            /**
             * Called whenever the value for key "ping_time" is changed in the cloud.
             *
             * @param dataSnapshot Current data contained in the key "ping_time".
             */
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    Long pingTime = dataSnapshot.getValue(Long.class);
                    if (pingTime == null) {
                        Log.w(PING_TIME_TAG, "Ping Time on cloud is null. Start service on robot app to update it.");
                        return;
                    }
                    Log.v(PING_TIME_TAG, "Ping Time: " + pingTime);
                    listener.onResult(pingTime);
                } catch (DatabaseException ex) {
                    Log.e(PING_TIME_TAG, "Failed to retrieve ping time: " + ex);
                }
            }

            /**
             * Called when this listener either failed at retrieving a data snapshot or
             * was removed as a result of the security and Firebase rules.
             *
             * @param databaseError Description of the listener's error.
             */
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(PING_TIME_TAG, "Error trying to retrieve ping_time: " + databaseError);
            }
        });

        Log.i(TAG, "Added ping_time listener.");
        return pingTimeListener;
    }

    /**
     * Removes the ping_time listener.
     *
     * Cloud Reference: robots/userId/robotUuid/ping_time
     *
     * @param listener The ping_time listener to remove from the caller.
     * @param robotUuid Current robot's uuid.
     */
    public void removePingTimeListener(ValueEventListener listener, String robotUuid) {
        // Sanity checks
        if (!isLoggedIn()) {
            Log.w(TAG, "User needs to be logged in first. Cannot remove ping time listener");
            return;
        }
        if (listener == null) {
            Log.w(TAG, "ValueEventListener is null. Cannot remove ping time listener.");
            return;
        }
        if (robotUuid == null) {
            Log.w(TAG, "Robot uuid is null. Cannot remove ping time listener.");
            return;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOTS)
                .child(getCurrentUserid())
                .child(robotUuid)
                .child("ping_time")
                .removeEventListener(listener);

        Log.i(TAG, "Removed ping_time listener.");
    }

    /**
     * Sends a drive goal to a given robot.
     *
     * @param goal  to drive to.
     * @param robot Robot to send the command to.
     */
    public void sendPlannerGoalToRobot(PlannerGoal goal, Robot robot) {
        // Sanity checks
        // -- needs to be logged in
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        // -- needs a robot uuid
        if (robot == null) {
            throw new IllegalArgumentException("null robot. A valid Robot instance is required");
        }
        // -- needs a valid goal
        if (goal == null) {
            throw new IllegalArgumentException(
                    "null drive goal provided. A valid DriveGoal instance is " + "required");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOT_GOALS).child(getCurrentUserid()).child(robot.uuid).child(DB_GOALS).child(
                goal.uuid).setValue(goal);
    }

    /**
     * Adds a point of interest to a map
     *
     * @param POI to add.
     * @param map to add POI to.
     */
    public void addPOI(PointOfInterest POI, String map) {
        // Sanity checks
        // -- needs to be logged in
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        // -- needs a valid point of interest
        if (POI == null) {
            throw new IllegalArgumentException(
                    "null point of interest provided. A valid PointOfInterest instance is "
                            + "required");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_OBJECTS).child(getCurrentUserid()).child(map).child(POI.uuid).setValue(POI);
    }

    /**
     * Deletes a point of interest from a map.
     *
     * @param uuid of POI object to delete.
     * @param map  to delete POI from.
     */
    public void deletePOI(String uuid, String map) {
        // Sanity checks
        // -- needs to be logged in
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        // -- needs a valid point of interest
        if (uuid == null) {
            throw new IllegalArgumentException(
                    "null POI uuid provided. A valid POI uuid instance is "
                            + "required");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        // TODO: check if the child to delete exists (as the list is updated with firebase data
        // this shouldn't be a problem, but that just in case consider this exception).
        db.child(DB_OBJECTS).child(getCurrentUserid()).child(map).child(uuid).removeValue();
    }

    public void listRobots(final ListResultsListener<Robot> listener) {
        // Sanity checks
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOTS).child(getCurrentUserid()).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        List<Robot> result = new ArrayList<>();
                        for (DataSnapshot child : data.getChildren()) {
                            try {
                                result.add(child.getValue(Robot.class));
                            } catch (DatabaseException ex) {
                                // TODO: this should call CloudLog.report... functions, but we
                                // have no Android Context for debug logging.
                                Log.e(TAG, "Firebase formatting error:", ex);
                            }
                        }
                        listener.onResult(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying for list of robots: " + databaseError);
                    }
                });
    }

    private final LinkedList<RobotListener> mRobotListeners = new LinkedList<>();

    /**
     * A listener for a robot's state.
     */
    public static final class RobotListener {
        private final String mUserUuid;
        private final String mRobotUuid;
        private final ValueEventListener mListener;

        /**
         * Create a robot listener.
         * @param userUuid The robot user uuid.
         * @param robotUuid The robot uuid.
         * @param listener The ValueEventListener to be the callback.
         */
        private RobotListener(String userUuid, String robotUuid, ValueEventListener listener) {
            mUserUuid = userUuid;
            mRobotUuid = robotUuid;
            mListener = listener;
            final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
            db.child(DB_ROBOTS).child(mUserUuid).child(mRobotUuid).addValueEventListener(mListener);
        }

        private void remove() {
            final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
            db.child(DB_ROBOTS).child(mUserUuid).child(mRobotUuid).removeEventListener(mListener);
        }
    }

    /**
     * Remove a robot listener object.
     * @param listener The listener to be removed from the system.
     */
    public void removeRobotListener(RobotListener listener) {
        listener.remove();
        while (mRobotListeners.contains(listener)) {
            mRobotListeners.remove(listener);
        }
    }

    /**
     * Watch changes in the robot state from the database
     *
     * @param listener listener for the robot state
     * @param robot    robot that is being used
     * @return The RobotListener object, which can later be removed.
     */
    public RobotListener watchRobot(final ResultListener<Robot> listener, Robot robot) {
        // Sanity checks
        String userUuid = getCurrentUserid();
        if (userUuid == null) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        if (robot == null) {
            throw new IllegalArgumentException("Robot is null");
        }
        if (robot.uuid == null) {
            throw new IllegalArgumentException("Robot uuid is null");
        }
        Log.v(TAG, "Get robot state: " + robot.uuid);
        RobotListener robotListener = new RobotListener(userUuid, robot.uuid,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        try {
                            Robot result = data.getValue(Robot.class);
                            listener.onResult(result);
                        } catch (DatabaseException ex) {
                            // TODO: this should call CloudLog.report... functions, but we
                            // have no Android Context for debug logging.
                            Log.e(TAG, "Firebase formatting error:", ex);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying robot state: " + databaseError);
                    }
                });
        mRobotListeners.add(robotListener);
        return robotListener;
    }

    /**
     * Download specific map from Database and saves it on the phone
     *
     * @param mapUuid         required map uuid
     * @param mapCompletePath Complete path of the map destination
     * @param success         Called on success
     */
    // TODO(shokman): it should return false if it fails
    public void downloadMap(final String mapUuid, final String mapCompletePath,
            final Runnable success) {

        if (getCurrentUserid() != null && mapUuid != null) {
            // TODO: Get worlds for the user and make sure this map is listed
            FirebaseStorage storage = FirebaseStorage.getInstance();
            final StorageReference reference = storage.getReference("maps")
                    .child(getCurrentUserid()).child(mapUuid);
            reference.child("dat").getFile(new File(mapCompletePath))
                    .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                            Log.d(TAG, "Downloaded " + mapUuid + " data file for user " +
                                    getCurrentUserid());
                            success.run();
                        }
                    });
        }
    }

    private final LinkedList<WorldListener> mWorldListeners = new LinkedList<>();

    /**
     * A listener for all worlds of a given user.
     */
    public static final class WorldListener {
        private final ValueEventListener mWorldListener;
        private final String mUserUuid;

        /**
         * Create WorldListener.
         * @param userUuid The user uuid.
         * @param listener The ValueEventListener to be called on update.
         */
        private WorldListener(String userUuid, ValueEventListener listener) {
            mUserUuid = userUuid;
            mWorldListener = listener;
            DatabaseReference db = FirebaseDatabase.getInstance().getReference();
            db.child("maps").child(userUuid).addValueEventListener(listener);
        }

        /**
         * Removes a a world listener.
         */
        private void remove() {
            DatabaseReference db = FirebaseDatabase.getInstance().getReference();
            db.child("maps").child(mUserUuid).addValueEventListener(mWorldListener);
        }
    }

    /**
     * Removes a world listener.
     * @param listener The listener to remove.
     */
    public void removeWorldListener(WorldListener listener) {
        listener.remove();
        while (mWorldListeners.contains(listener)) {
            mWorldListeners.remove(listener);
        }
    }

    /**
     * List all worlds in the cloud. The listener is called whenever a change is published.
     * @param listener The listener to be called with the list of World objects.
     * @return The WorldListener for later removal.
     */
    // TODO(rpham): copied from CloudWorldManager we should remove it from there.
    // TODO(rpham): it should use Firebase deserialization snapshot.fromData(clazz).
    public WorldListener watchWorlds(final ListResultsListener<World> listener) {
        String userUuid = getCurrentUserid();
        if (userUuid == null) {
            throw new IllegalStateException("User needs to be logged in first");
        }

        WorldListener worldListener = new WorldListener(userUuid,
                new ValueEventListener() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<World> result = new ArrayList<>();
                        Map<String, Map<String, Object>> output = new HashMap<>();
                        for (DataSnapshot ds : dataSnapshot.getChildren()) {
                            output.put(ds.getKey(), (Map<String, Object>) ds.getValue());
                        }
                        for (Map.Entry<String, Map<String, Object>> e : output.entrySet()) {
                            if (!e.getValue().containsKey("deleted")
                                    && World.isWorldMapValid(
                                    e.getValue())) {
                                Log.i(TAG, "Get world " + e.getKey());
                                result.add(new World(e.getValue()));
                            } else if (e.getValue().containsKey("deleted")) {
                                Log.i(TAG, "Ignored deleted world: " + e.getKey() + " "
                                        + e.getValue() + " " + World.isWorldMapValid(
                                        e.getValue()));
                            }
                        }
                        listener.onResult(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error reading worlds from cloud: " + databaseError);
                    }
                });
        mWorldListeners.add(worldListener);
        return worldListener;
    }

    /**
     * Adds listener to get specific POI from Database
     *
     * @param uuid of the object POI.
     * @param map  where the object is stored.
     */
    public void addSinglePOIListener(final ResultListener<PointOfInterest> listener, String uuid,
            String map) {
        // Sanity checks
        // -- needs to be logged in
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first.");
        }
        // -- needs a valid uuid
        if (uuid == null) {
            throw new IllegalArgumentException(
                    "null POI object uuid provided.");
        }

        // -- needs a valid map
        if (map == null) {
            throw new IllegalArgumentException(
                    "null map uuid provided.");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_OBJECTS).child(getCurrentUserid()).child(map).child(
                uuid).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        try {
                            PointOfInterest POI = dataSnapshot.getValue(PointOfInterest.class);
                            listener.onResult(POI);
                        } catch (DatabaseException ex) {
                            // TODO: this should call CloudLog.report... functions, but we
                            // have no Android Context for debug logging.
                            Log.e(TAG, "Firebase formatting error:", ex);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying for POI: " + databaseError);
                    }
                });
    }

    /**
     * Adds a listener to read list of POIs in Database and update it if changes occur.
     *
     * @param robot to add listener to.
     * @return reference to the listener added.
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public ValueEventListener addPOIListener(final ListResultsListener<PointOfInterest> listener,
            Robot robot) {
        // Sanity checks
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first.");
        }
        if (robot == null) {
            throw new IllegalArgumentException("Robot is null.");
        }
        if (robot.map == null) {
            Log.w(TAG, "Robot map is null.");
            return null;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        ValueEventListener POIListener = db.child(DB_OBJECTS).child(getCurrentUserid()).child(
                robot.map).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        List<PointOfInterest> result = new ArrayList<>();
                        for (DataSnapshot child : data.getChildren()) {
                            try {
                                result.add(child.getValue(PointOfInterest.class));
                            } catch (DatabaseException e) {
                                Log.w(TAG, "Ignore point: " + child.getKey(), e);
                            }
                        }
                        listener.onResult(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying for list of POIs: " + databaseError);
                    }
                });

        return POIListener;
    }

    /**
     * Removes a listener to the list of POIs in Database.
     *
     * @param listener to be removed.
     * @param robot    to remove the listener from.
     */
    public void removePOIListener(ValueEventListener listener, Robot robot) {
        // Sanity checks
        if (!isLoggedIn()) {
            Log.w(TAG, "User needs to be logged in first.");
            return;
        }
        if (listener == null) {
            Log.w(TAG, "Listener is null.");
            return;
        }
        if (robot == null) {
            Log.w(TAG, "Robot is null.");
            return;
        }
        if (robot.map == null) {
            Log.w(TAG, "Robot map is null.");
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_OBJECTS)
                .child(getCurrentUserid())
                .child(robot.map)
                .removeEventListener(listener);
    }

    /**
     * Adds a listener to read list of animation in Database and update it if changes occur.
     *
     * @param robot The robot UUID.
     * @return reference to the listener added.
     */
    public ValueEventListener addAnimationListener(String robot,
            final ListResultsListener<AnimationInfo> listener) {
        // Sanity checks
        final String user = getCurrentUserid();
        if (user == null) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        return db.child(DB_ROBOT_GOALS).child(user).child(robot).child(
                DB_ANIMATIONS).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        List<AnimationInfo> result = new ArrayList<>((int) data.getChildrenCount());
                        for (DataSnapshot child : data.getChildren()) {
                            try {
                                AnimationInfo info = AnimationInfo.fromFirebase(child, user);
                                Log.i(TAG, "Animation: " + info);
                                result.add(info);
                            } catch (DatabaseException e) {
                                Log.w(TAG, "Ignore sound info: " + child.getKey(), e);
                            }
                        }
                        listener.onResult(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying for list of POIs: " + databaseError);
                    }
                });
    }

    /**
     * Removes a listener to the list of sounds in Database.
     *
     * @param robot    the robot to be removed.
     * @param listener listener to be removed.
     */
    public void removeAnimationListener(String robot, ValueEventListener listener) {
        // Sanity checks
        String user = getCurrentUserid();
        if (user == null) {
            Log.w(TAG, "User needs to be logged in first");
            return;
        }
        if (listener == null) {
            Log.w(TAG, "Listener is null");
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOT_GOALS).child(user).child(robot)
                .child(DB_ANIMATIONS).removeEventListener(listener);
    }

    /**
     * Adds a listener to read list of sounds in Database and update it if changes occur.
     *
     * @return reference to the listener added.
     */
    public ValueEventListener addSoundListener(final ListResultsListener<SoundInfo> listener) {
        // Sanity checks
        final String user = getCurrentUserid();
        if (user == null) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        return db.child(DB_SOUNDS).child(user).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        List<SoundInfo> result = new ArrayList<>((int) data.getChildrenCount());
                        for (DataSnapshot child : data.getChildren()) {
                            try {
                                SoundInfo info = SoundInfo.fromFirebase(child, user);
                                result.add(info);
                            } catch (DatabaseException e) {
                                Log.w(TAG, "Ignore sound info: " + child.getKey(), e);
                            }
                        }
                        listener.onResult(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying for list of POIs: " + databaseError);
                    }
                });
    }

    /**
     * Removes a listener to the list of sounds in Database.
     *
     * @param listener to be removed.
     */
    public void removeSoundListener(ValueEventListener listener) {
        // Sanity checks
        String user = getCurrentUserid();
        if (user == null) {
            Log.w(TAG, "User needs to be logged in first");
            return;
        }
        if (listener == null) {
            Log.w(TAG, "Listener is null");
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_SOUNDS)
                .child(getCurrentUserid())
                .removeEventListener(listener);
    }

    /**
     * Adds a global listener to read list of sounds in Database and update it if changes occur.
     *
     * @return reference to the listener added.
     */
    public ValueEventListener addGlobalSoundListener(final ListResultsListener<SoundInfo> listener) {
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        return db.child(DB_GLOBAL_SOUNDS).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        List<SoundInfo> result = new ArrayList<>((int) data.getChildrenCount());
                        for (DataSnapshot child : data.getChildren()) {
                            try {
                                SoundInfo info = SoundInfo.fromFirebase(child, null);
                                result.add(info);
                            } catch (DatabaseException e) {
                                Log.w(TAG, "Ignore sound info: " + child.getKey(), e);
                            }
                        }
                        listener.onResult(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error querying for list of POIs: " + databaseError);
                    }
                });
    }

    /**
     * Removes a global listener to the list of sounds in Database.
     *
     * @param listener to be removed.
     */
    public void removeGlobalSoundListener(ValueEventListener listener) {
        if (listener == null) {
            Log.w(TAG, "Listener is null");
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_GLOBAL_SOUNDS).removeEventListener(listener);
    }


    /**
     * Set the currently playing sound
     *
     * @param sound The sound to start playing
     * @param robot The robot to play it on
     */
    public void setPlayingSound(SoundInfo sound, Robot robot) {
        String user = getCurrentUserid();
        if (user == null) {
            Log.w(TAG, "User needs to be logged in first");
            return;
        }
        if (robot == null) {
            Log.w(TAG, "Robot is null");
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOT_GOALS).child(user).child(robot.uuid)
                .child("sound").setValue(new RobotSound(sound.getId()));
    }

    /**
     * Set fleet preferences on user preferences on the DB
     *
     * @param robotPreferences preferences to be saved on DB
     */
    public void setRobotFleetPreferences(final RobotPreferences robotPreferences,
            final ResultListener<Boolean> listener) {

        final String userId = getCurrentUserid();
        if (userId == null) {
            Log.e(TAG, "Trying to save fleet preferences but the user is not logged in");
            listener.onResult(false);
            return;
        }
        if (robotPreferences == null) {
            Log.e(TAG, "Trying to save fleet preferences but the preferences were null");
            listener.onResult(false);
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_USER_PREFERENCES).child(userId).child("fleet_preferences").runTransaction(
                new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(MutableData mutableData) {
                        // Set value and report transaction success
                        mutableData.setValue(robotPreferences);
                        return Transaction.success(mutableData);
                    }

                    @Override
                    public void onComplete(DatabaseError databaseError, boolean b,
                            DataSnapshot dataSnapshot) {
                        // Transaction completed
                        listener.onResult(b);
                        Log.d(TAG, "onTransaction:onComplete:" + databaseError);
                    }
                });
    }


    /**
     * Register listener for fleet preferences value from DB
     *
     * @param listener listener to be registered
     */
    public void addRobotFleetPreferencesListener(final ResultListener<RobotPreferences> listener) {
        // Sanity checks
        final String userId = getCurrentUserid();
        if (userId == null) {
            Log.i(TAG, "User needs to be logged in first");
            return;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_USER_PREFERENCES).child(userId).child(
                "fleet_preferences").addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        try {
                            listener.onResult(data.getValue(RobotPreferences.class));
                        } catch (DatabaseException ex) {
                            // TODO: this should call CloudLog.report... functions, but we have no
                            // Android Context for debug logging.
                            Log.e(TAG, "Firebase formatting error:", ex);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG,
                                "Error querying fleet preferences from the db: " + databaseError);
                    }
                });
    }

    /**
     * Get the fleet preferences values from DB only once
     *
     * @param listener reference to the listener that receives the data
     */
    public void getRobotFleetPreferences(final ResultListener<RobotPreferences> listener) {
        // Sanity checks
        final String userId = getCurrentUserid();
        if (userId == null) {
            Log.i(TAG, "User needs to be logged in first");
            return;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_USER_PREFERENCES).child(userId).child(
                "fleet_preferences").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot data) {
                        try {
                            listener.onResult(data.getValue(RobotPreferences.class));
                        } catch (DatabaseException ex) {
                            // TODO: this should call CloudLog.report... functions, but we have no
                            // Android Context for debug logging.
                            Log.e(TAG, "Firebase formatting error:", ex);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG,
                                "Error querying fleet preferences from the db: " + databaseError);
                    }
                });
    }

    /**
     * Set map orientation on the database
     */
    public void setMapViewPose(final String mapUuid, Transform viewPose) {
        String user = getCurrentUserid();

        if (user == null) {
            Log.w(TAG, "User needs to be logged in first");
            return;
        }
        if (mapUuid == null) {
            Log.w(TAG, "Map is null");
            return;
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_MAPS).child(user).child(mapUuid).child("view_pose").setValue(viewPose);

    }

    /**
     * Get map orientation from database
     */
    public void getMapOrientation(final ResultListener<Transform> listener,
            final String mapUuid) {

        String user = getCurrentUserid();
        if (user == null) {
            Log.w(TAG, "User needs to be logged in first");
            return;
        }
        if (mapUuid == null) {
            Log.w(TAG, "Map is null");
            return;
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_MAPS).child(user).child(mapUuid).child("view_pose").
                addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot data) {
                                try {
                                    final ai.cellbots.common.data.Transform tf
                                            = data.getValue(Transform.class);
                                    listener.onResult(tf);
                                } catch (DatabaseException ex) {
                                    // TODO: this should call CloudLog.report... functions, but we
                                    // have no Android Context for debug logging.
                                    Log.e(TAG, "Firebase formatting error:", ex);
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                Log.e(TAG, "Error querying for map view orientation: "
                                        + databaseError);
                            }
                        });
    }

    /**
     * Adds metadata listener in Firebase.
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public ValueEventListener addMetadataListener(final ResultListener<RobotMetadata> listener,
                                                  Robot robot) {

        final Robot r = robot;
        ValueEventListener metadataListener = FirebaseDatabase.getInstance().getReference().child(
                DB_ROBOT_GOALS).child(getCurrentUserid()).child(robot.uuid).child(
                DB_METADATA).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() == null) {
                    return;
                }
                RobotMetadata metadata;
                try {
                    metadata = RobotMetadata.fromFirebase(getCurrentUserid(), r.uuid, dataSnapshot);
                } catch (DatabaseException ex) {
                    // TODO: this should call CloudLog.report... functions, but we
                    // have no Android Context for debug logging.
                    Log.e(TAG, "Firebase formatting error:", ex);
                    return;
                }
                listener.onResult(metadata);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error querying robot metadata: "
                        + databaseError);
            }
        });
        return metadataListener;
    }

    /**
     * Removes a listener to metadata in Firebase.
     *
     * @param listener to be removed.
     * @param robot    to remove the listener from.
     */
    public void removeMetadataListener(ValueEventListener listener, Robot robot) {
        // Sanity checks
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        if (robot == null) {
            throw new IllegalArgumentException("Robot is null");
        }
        String user = getCurrentUserid();
        if (user == null) {
            throw new IllegalStateException("User needs to be logged in first");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOT_GOALS)
                .child(user)
                .child(robot.uuid)
                .child(DB_METADATA)
                .removeEventListener(listener);
    }

    /**
     * Sends Name to Robot metadata in Firebase.
     *
     * @param userID  user UUID
     * @param RobotID robot UUID
     * @param name    name of the robot
     */
    public void sendRobotName(String userID, String RobotID, String name) {
        // Sanity checks
        // -- needs to be logged in
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        // -- needs a valid robot metadata
        if (name == null) {
            throw new IllegalArgumentException(
                    "null Robot name provided. A name is "
                            + "required");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        // Set metadata name
        db.child(DB_ROBOT_GOALS).child(userID).child(RobotID).child(DB_METADATA).child(
                "name").setValue(name);
        // Set robot name, just in case it is not publishing
        db.child(DB_ROBOTS).child(userID).child(RobotID).child("name").setValue(name);
    }

    /**
     * Adds a listener to read list of goals in Database and update it if changes occur. Then it
     * filters the list to get the active goals.
     * For now, the goal_type considered is drive. A drive goal is active when it hasn't been
     * completed nor rejected.
     *
     * @param robot to add listener to.
     * @return reference to the listener added.
     */
    // TODO: this adds a listener to all goal types and then filters desired (active) goals. This
    // should be improved to manage filtering in Firebase and download only necessary data.
    @SuppressWarnings("UnnecessaryLocalVariable")
    public ValueEventListener addActiveGoalListener(final ListResultsListener<PlannerGoal> listener,
            Robot robot) {
        // Sanity checks
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        if (robot == null) {
            throw new IllegalArgumentException("Robot is null");
        }
        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        ValueEventListener ActiveGoalListener = db.child(DB_ROBOT_GOALS)
                .child(getCurrentUserid()).child(robot.uuid).child(DB_GOALS)
                .addValueEventListener(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot data) {
                                List<PlannerGoal> result = new ArrayList<>();
                                for (DataSnapshot c : data.getChildren()) {
                                    if (c.hasChild("complete")) {
                                        continue;
                                    }
                                    if (c.hasChild("reject")) {
                                        continue;
                                    }
                                    if (c.hasChild("cancel")) {
                                        continue;
                                    }
                                    if (!c.hasChild("name")) {
                                        continue;
                                    }
                                    if (c.child("name").getValue() == null) {
                                        continue;
                                    }
                                    @SuppressWarnings("ConstantConditions")
                                    String name = c.child("name").getValue().toString();
                                    if (name == null) {
                                        continue;
                                    }
                                    if (name.equals("drive") || name.equals("driveWait")) {
                                        try {
                                            result.add(c.getValue(DriveGoal.class));
                                        } catch (DatabaseException ex) {
                                            Log.w(TAG, "Failed drive goal " + c.getKey(), ex);
                                        }
                                    } else if (name.equals("drivePoint") || name.equals(
                                            "driveWaitPoint")) {
                                        try {
                                            result.add(c.getValue(DrivePointGoal.class));
                                        } catch (DatabaseException ex) {
                                            Log.w(TAG, "Failed drivePoint goal " + c.getKey(), ex);
                                        }
                                    } else if (name.equals("vacuumSpiral")) {
                                        try {
                                            result.add(c.getValue(VacuumSpiralGoal.class));
                                        } catch (DatabaseException ex) {
                                            Log.w(TAG, "Failed vacuumSpiral goal " + c.getKey(),
                                                    ex);
                                        }
                                    } else if (name.equals("patrolDriver")) {
                                        try {
                                            result.add(c.getValue(PatrolDriverGoal.class));
                                        } catch (DatabaseException ex) {
                                            Log.w(TAG, "Failed patrolDriver goal " + c.getKey(),
                                                    ex);
                                        }
                                    } else {
                                        Log.i(TAG, "Ignored goal: " + name);
                                    }
                                }

                                listener.onResult(result);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                Log.e(TAG, "Error querying for list of active goals: "
                                        + databaseError);
                            }
                        });

        return ActiveGoalListener;
    }

    /**
     * Removes a listener to the goals in Database.
     *
     * @param listener to be removed.
     * @param robot    to remove the listener from.
     */
    public void removeActiveGoalListener(ValueEventListener listener, Robot robot) {
        // Sanity checks
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        if (robot == null) {
            throw new IllegalArgumentException("Robot is null");
        }

        final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child(DB_ROBOT_GOALS)
                .child(getCurrentUserid())
                .child(robot.uuid)
                .child(DB_GOALS)
                .removeEventListener(listener);
    }

    /**
     * Sends the teleop command to a robot.
     *
     * @param teleop The teleop command.
     * @param robot  The robot.
     */
    public void sendTeleopToRobot(Teleop teleop, Robot robot) {
        if (teleop == null || robot == null) {
            return;
        }
        String uuid = robot.uuid;
        if (uuid == null) {
            return;
        }
        String user = getCurrentUserid();
        if (user == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference().child(DB_ROBOT_GOALS)
                .child(user).child(uuid).child(DB_TELEOP).setValue(teleop);
    }

    /**
     * Returns the UUID of the currently logged-in user, if any,
     * or null otherwise.
     */
    public String getCurrentUserid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    /**
     * Returns whether there is a user logged in for this session or not.
     */
    public boolean isLoggedIn() {
        return getCurrentUserid() != null;
    }

    /**
     * Cancel all goals for a robot and user.
     *
     * @param robotUuid The robot's uuid.
     */
    public void cancelAllRobotGoals(final String robotUuid) {
        // Sanity checks
        if (!isLoggedIn()) {
            throw new IllegalStateException("User needs to be logged in first");
        }
        if (robotUuid == null) {
            throw new IllegalArgumentException("Robot uuid is null");
        }
        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(getCurrentUserid()).child(robotUuid).child("goals")
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
                                    .child(getCurrentUserid()).child(robotUuid).child("goals")
                                    .child(child.getKey()).child("cancel").setValue(true);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error cancelling active goals: " + databaseError);
                    }
                });
    }
}
