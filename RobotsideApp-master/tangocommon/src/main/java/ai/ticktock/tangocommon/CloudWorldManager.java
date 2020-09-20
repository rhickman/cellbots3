package ai.cellbots.tangocommon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.World;
import ai.cellbots.common.data.SmootherParams;

/**
 * Manages the cloud to tango world synchronization
 */
public class CloudWorldManager {
    private static final String TAG = CloudWorldManager.class.getSimpleName();
    private static final String KEY_BOOLEAN_USE_CLOUD_AREA_DESCRIPTION = "config_experimental_use_cloud_adf";
    private static final String CLOUD_WORLD_DATA_DIRECTORY = "cloud_world_data";
    private static final String CLOUD_WORLD_LIST_FILE = "world_list.bin";
    private static final int STEPS_PER_WORLD = 1;
    private World[] mWorlds = null;
    private Tango mTango = null;
    private final Context mParent;
    private String mUserId = null;
    private final Listener mListener;
    private PromptLink mPromptLink = null;
    private int mStepsUntilExport = 0;
    private ValueEventListener mWorldListener = null;
    private boolean mShouldDownloadWorlds = true;
    private final FirebaseAuth.AuthStateListener mAuthListener;
    private final HashMap<String, String> mWorldUserUuids = new HashMap<>();
    private boolean mShutdown = false;
    private final HashMap<String, Long> mWorldTimes = new HashMap<>(); // Store last update timestamp
    private final HashSet<String> mUploadedWorlds = new HashSet<>(); // Store the list of uploaded worlds

    public interface Listener {

        /**
         * Called to lock an export state.
         *
         * @return True if we successfully start an export state.
         */
        boolean lockExportState();

        /**
         * Called to clear an export state.
         */
        void clearExportState();

        /**
         * Get if we are in an export state.
         */
        boolean isExportState();

        /**
         * Called on update.
         */
        void onStateUpdate();
    }

    /**
     * Get the cloud world data directory.
     *
     * @return The directory containing the cloud world data as a File object.
     */
    private File getCloudWorldDataDirectory() {
        return new File(mParent.getFilesDir(), CLOUD_WORLD_DATA_DIRECTORY);
    }

    /**
     * Get the cloud world info file.
     *
     * @return The File containing the cloud world list.
     */
    private File getCloudWorldListFile() {
        return new File(getCloudWorldDataDirectory(), CLOUD_WORLD_LIST_FILE);
    }

    /**
     * Get the data file of a world.
     *
     * @param uuid The world's uuid.
     * @return The detailed world's file.
     */
    private File getWorldDataFile(String uuid) {
        return new File(getCloudWorldDataDirectory(), uuid);
    }

    /**
     * Check if a world data file exists and is valid.
     *
     * @param world The world's uuid.
     * @return True if the world exists and is valid.
     */
    private boolean isWorldDataFileValid(String world) {
        boolean exist = getWorldDataFile(world).exists();
        if (!exist) {
            Log.w(TAG, "World data file doesn't exist: " + world);
        } else {
            Log.d(TAG, "World data file exists: " + world);
        }
        boolean isFile = getWorldDataFile(world).isFile();
        if (!isFile) {
            Log.w(TAG, "World data file is not valid: " + world);
        } else {
            Log.d(TAG, "World data file is valid: " + world);
        }
        return exist && isFile;
    }

    /**
     * Create a CloudWorldManager.
     *
     * @param parent   The parent context.
     * @param listener The listener thread.
     */
    public CloudWorldManager(Context parent, Listener listener) {
        mParent = parent;
        mListener = listener;
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                String userId = user == null ? null : user.getUid();
                Log.i(TAG, "Firebase changed user id: " + userId);
                onUserIdUpdate(userId);
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(mAuthListener);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        onUserIdUpdate(user == null ? null : user.getUid());

        if (getCloudWorldDataDirectory().exists() && !getCloudWorldDataDirectory().isDirectory()) {
            if (!getCloudWorldDataDirectory().delete()) {
                throw new Error("Could not delete file that is blocking world data storage");
            }
        }
        if (!getCloudWorldDataDirectory().exists()) {
            if (!getCloudWorldDataDirectory().mkdir()) {
                throw new Error("Could not create cloud world data directory");
            }
        }
        if (!getCloudWorldListFile().exists()) {
            Log.i(TAG, "No world list file, ignoring");
            return;
        }
        if (!getCloudWorldListFile().isFile()) {
            if (!getCloudWorldListFile().delete()) {
                throw new Error("Could not delete file that is blocking world list storage");
            }
        }

        try {
            FileInputStream inputStream = new FileInputStream(getCloudWorldListFile());
            byte[] buffer = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(buffer);
            if (inputStream.read(buffer, 0, 5) != 5) {
                inputStream.close();
                Log.e(TAG, "Could not read initial bytes");
                return;
            }
            byte version = b.get();
            if (version != 0) {
                inputStream.close();
                Log.e(TAG, "Bad version: " + version);
                return;
            }
            int count = b.getInt();
            b.rewind();

            for (int i = 0; i < count; i++) {
                if (inputStream.read(buffer, 0, 4) != 4) {
                    inputStream.close();
                    mWorldTimes.clear();
                    Log.e(TAG, "Could not read name bytes for " + i);
                    return;
                }
                int len = b.getInt();
                if (len < 0) {
                    mWorldTimes.clear();
                    inputStream.close();
                    Log.e(TAG, "Could not read name, len less than zero for " + i);
                    return;
                }
                b.rewind();
                byte[] strBytes = new byte[len];
                int readBytes = inputStream.read(strBytes);
                if (readBytes != len) {
                    inputStream.close();
                    mWorldTimes.clear();
                    Log.e(TAG, "Could not read wanted " + len + " bytes for " + i + " got " + readBytes);
                    return;
                }
                if (inputStream.read(buffer, 0, 8) != 8) {
                    mWorldTimes.clear();
                    inputStream.close();
                    Log.e(TAG, "Could not read name time for " + i);
                    return;
                }
                long t = b.getLong();
                b.rewind();
                String name;
                try {
                    name = new String(strBytes);
                } catch (Exception e) {
                    mWorldTimes.clear();
                    inputStream.close();
                    Log.e(TAG, "Unable to read string", e);
                    return;
                }
                if (name.equals("")) {
                    inputStream.close();
                    mWorldTimes.clear();
                    Log.e(TAG, "Null world name written to file");
                    return;
                }
                mWorldTimes.put(name, t);
                Log.d(TAG, "World already has " + name + " at " + t);
            }
            inputStream.close();
        } catch (IOException e) {
            Log.w(TAG, "Could not read world list. The file has been ignored.", e);
        }
    }

    /**
     * Shutdown the system.
     */
    public void shutdown() {
        Log.i(TAG, "Shutdown the system");
        if (!mShutdown) {
            FirebaseAuth.getInstance().removeAuthStateListener(mAuthListener);
            clearWorldListener();
        }
        mShutdown = true;
        synchronized (mParent) {
            if (mPromptLink != null) {
                mPromptLink.freeManager(this);
                mPromptLink = null;
            }
        }
    }

    /**
     * Sets the should.
     *
     * @param shouldDownloadWorlds Should download worlds to the system.
     */
    public void setShouldDownloadWorlds(boolean shouldDownloadWorlds) {
        Log.i(TAG, "Set shouldDownloadWorlds: " + shouldDownloadWorlds);
        synchronized (mParent) {
            boolean oldValue = mShouldDownloadWorlds;
            mShouldDownloadWorlds = shouldDownloadWorlds;
            if (oldValue != shouldDownloadWorlds) {
                loadWorldsFromTango();
            }
        }
    }


    public void setTango(Tango tango) {
        synchronized (mParent) {
            mTango = tango;
        }
    }


    public static class PromptLink {
        private CloudWorldManager mCloudWorldManager = null;
        private final Activity mParent;

        public PromptLink(Activity parent) {
            mParent = parent;
        }


        @SuppressLint("LogConditional")
        private void freeManager(CloudWorldManager manager) {
            boolean failImportExport = false;
            synchronized (this) {
                if (mCloudWorldManager == manager) {
                    mCloudWorldManager = null;
                    failImportExport = !manager.isShutdown();
                }
            }
            // If we undo a manager during an operation, and we are not shutting down
            // the service, we need to fail import/export. This occurs if a user kills the android
            // activity during the import/export prompt.
            if (failImportExport) {
                manager.failImportExport(mParent);
            }
        }

        @SuppressLint("LogConditional")
        private boolean lockManager(CloudWorldManager manager) {
            synchronized (this) {
                if (mCloudWorldManager != null) {
                    Log.i(TAG, "Unable to lock manager, manager already set");
                    return false;
                }
                mCloudWorldManager = manager;
                if (mCloudWorldManager.isShutdown()) {
                    mCloudWorldManager = null;
                    return false;
                }
                return true;
            }
        }

        /**
         * Begins the export of a world to a file so it may be uploaded to the cloud.
         *
         * @param manager The manager for the export.
         * @param w       The world to be exported.
         */
        private void startWorldExport(CloudWorldManager manager, World w) {
            Log.i(TAG, "Start export of " + w);
            if (lockManager(manager)) {
                w.exportToFile(mParent);
            }
        }

        /**
         * Begins the download of a world to a file and then prompting of import to tango.
         *
         * @param manager   The manager.
         * @param w         The world.
         * @param timestamp The timestamp of the world update.
         */
        private void startWorldDownload(CloudWorldManager manager, World w, long timestamp) {
            Log.i(TAG, "Start download of " + w);
            if (lockManager(manager)) {
                manager.downloadWorldToTango(mParent, w, timestamp);
            }
        }

        /**
         * Check if an activityResult is for the PromptLink
         *
         * @param requestCode Android requestCode
         * @param resultCode  Android resultCode
         * @param data        Android data
         * @return True if the activity result should be dispatched to the PromptLink.
         */
        public boolean isActivityResult(final int requestCode, final int resultCode,
                                        final Intent data) {
            Log.i(TAG, "Check activity: result: " + requestCode + ' ' + resultCode
                    + "!=" + Activity.RESULT_CANCELED + ' ' + data);
            return World.isTangoExportIntent(requestCode) || World.isTangoImportIntent(requestCode);
        }

        /**
         * Promptless import, occurs when an import does not have to prompt.
         */
        private void promptlessImport(CloudWorldManager manager) {
            boolean doImport = false;
            synchronized (this) {
                if (manager == mCloudWorldManager) {
                    doImport = true;
                    mCloudWorldManager = null;
                }
            }
            if (doImport) {
                manager.importedWorld(mParent);
            }
        }

        /**
         * Failed import, occurs when an import does not have all files.
         */
        private void failedImport(CloudWorldManager manager) {
            boolean doFailure = false;
            synchronized (this) {
                if (manager == mCloudWorldManager) {
                    doFailure = true;
                    mCloudWorldManager = null;
                }
            }
            if (doFailure) {
                manager.failImportExport(mParent);
            }
        }

        /**
         * Called when we successfully exported a world.
         *
         * @param requestCode Android requestCode
         * @param resultCode  Android resultCode
         * @param data        Android data
         * @return Null if it succeeded, or an error otherwise.
         */
        public String onActivityResult(final int requestCode, final int resultCode,
                                       final Intent data) {
            Log.i(TAG, "Check activity: result: " + requestCode + ' ' + resultCode
                    + "!=" + Activity.RESULT_CANCELED + ' ' + data);
            if (World.isTangoExportIntent(requestCode) || World.isTangoImportIntent(requestCode)) {
                if (resultCode != Activity.RESULT_CANCELED) {
                    CloudWorldManager manager = null;
                    synchronized (this) {
                        if (mCloudWorldManager != null) {
                            manager = mCloudWorldManager;
                            mCloudWorldManager = null;
                        } else {
                            Log.i(TAG, "Failed import/export with null cloud world manager");
                        }
                    }
                    if (manager != null) {
                        if (World.isTangoExportIntent(requestCode)) {
                            manager.exportedWorld(mParent);
                        } else {
                            manager.importedWorld(mParent);
                        }
                    }
                } else {
                    CloudWorldManager manager = null;
                    synchronized (this) {
                        if (mCloudWorldManager != null) {
                            Log.i(TAG, "Failed import/export of world");
                            manager = mCloudWorldManager;
                            mCloudWorldManager = null;
                        } else {
                            Log.i(TAG, "Failed import/export with null cloud world manager");
                        }
                    }
                    if (manager != null) {
                        manager.failImportExport(mParent);
                    }
                    return "Could not save world: " + requestCode + ' ' + resultCode
                            + "!=" + Activity.RESULT_CANCELED + ' ' + data;
                }
            } else {
                Log.e(TAG,
                        "Invalid export/import activity result: " + requestCode + ' ' + resultCode
                                + "!=" + Activity.RESULT_CANCELED + ' ' + data);
                return "Invalid export/import activity result: " + requestCode + ' ' + resultCode
                        + "!=" + Activity.RESULT_CANCELED + ' ' + data;
            }
            return null;
        }
    }


    /**
     * Called to test if an operation has finished.
     */
    private void finishTest(Activity parent) {
        if (mStepsUntilExport == 0) {
            Log.i(TAG, "Operation has finished");
            mListener.clearExportState();
            loadWorldsFromTango();
            mListener.onStateUpdate();
            // If we have finished exporting, and we do not think any more worlds must be
            // exported, then we can cleanup the worlds left here.
            if ((mStepsUntilExport <= 0) && !mListener.isExportState() && parent != null) {
                cleanupWorlds(parent);
            }
        }
    }

    /**
     * Get the list of worlds
     */
    public World[] getWorlds() {
        World[] worlds = mWorlds;
        return worlds != null ? worlds.clone() : null;
    }

    /**
     * Get the list of worlds after refreshing the list.
     */
    public World[] refreshAndGetWorlds() {
        loadWorldsFromTango();
        return getWorlds();
    }

    /**
     * Return the details of the worlds from the tango.
     */
    private void loadWorldsFromTango() {
        Log.i(TAG, "Load worlds from Tango.");
        synchronized (mParent) {
            List<String> uuids;
            if (mTango == null) {
                mWorlds = null;
                return;
            }
            try {
                uuids = mTango.listAreaDescriptions();
            } catch (SecurityException ex) {
                Log.wtf(TAG, "Security error with listAreaDescriptions", ex);
                uuids = null;
            } catch (TangoErrorException ex) {
                Log.wtf(TAG, "Tango error with listAreaDescriptions", ex);
                uuids = null;
            }

            if (uuids == null) {
                mWorlds = null;
                return;
            }

            // Load the metadata to the World() objects
            World[] r = new World[uuids.size()];
            for (int i = 0; i < uuids.size(); i++) {
                String name;
                TangoAreaDescriptionMetaData metadata;
                try {
                    metadata = mTango.loadAreaDescriptionMetaData(uuids.get(i));
                } catch (SecurityException ex) {
                    Log.e(TAG, "Security error with loadAreaDescriptionMetaData", ex);
                    mWorlds = null;
                    return;
                } catch (TangoErrorException ex) {
                    Log.e(TAG, "Tango error with loadAreaDescriptionMetaData", ex);
                    mWorlds = null;
                    return;
                }
                byte[] nameBytes = metadata.get(TangoAreaDescriptionMetaData.KEY_NAME);
                if (nameBytes != null) {
                    name = new String(nameBytes);
                } else {
                    name = "ERROR LOADING NAME: " + uuids.get(i);
                }
                r[i] = new World(uuids.get(i), name);
            }

            // Ensure the world is properly stored in the cloud
            for (World w : r) {
                worldIsProperlyStoredInCloud(w);
            }

            Log.i(TAG, "Listing cloud worlds");
            listCloudWorlds();

            mWorlds = r;
        }
        mListener.onStateUpdate();
    }

    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////// BEGIN UPLOAD PROCESS ////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    /**
     * Check if the world is uploaded to the cloud properly. First checks to see if it is
     * accurately represented in the firebase database, and then it checks to see if it is
     * the DAT and ADF files are present. In the event of a failure, it calls worldNeedsUpload().
     *
     * @param w The world.
     */
    private void worldIsProperlyStoredInCloud(final World w) {
        final String user = mUserId;
        if (user == null) {
            Log.i(TAG, "User is null");
            return;
        }

        synchronized (mParent) {
            if (mUploadedWorlds.contains(w.getUuid())) {
                Log.i(TAG, "Uploaded world contains " + w.getName());
                return;
            }
        }

        // We only update the files that have a valid DAT file. Some worlds may lack this
        // information and are considered invalid to upload to the cloud. Attempting to upload
        // will lead to an infinite loop because they will never be successfully uploaded.
        if (!isWorldDataFileValid(w.getUuid())) {
            Log.w(TAG, "Invalid local world: " + w.getUuid() + " " + w.getName());
            return;
        }

        // Only upload worlds with the correct user id
        String worldUserId = getWorldUserUuid(w.getUuid());
        if (worldUserId == null) {
            Log.w(TAG, "Local world ignored for having invalid user uuid: "
                    + w.getUuid() + " " + w.getName());
            return;
        }
        if (!user.equals(worldUserId)) {
            Log.w(TAG, "World for wrong user: " + user + " != " + getWorldUserUuid(w.getUuid())
                    + " for " + w.getUuid() + " " + w.getName());
            return;
        }

        if (mShutdown) {
            Log.i(TAG, "world is worldIsProperlyStoredInCloud exited for shutdown");
            return;
        }

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("maps").child(user).child(w.getUuid()).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> v = (Map<String, Object>) dataSnapshot.getValue();
                        Log.i(TAG, "OnDataChange");
                        if ((v != null) && World.isWorldMapValid(v)) {
                            List<String> filenames = new ArrayList<>();
                            filenames.add("adf");
                            filenames.add("dat");
                            checkWorldFiles(user, w, filenames);
                        } else {
                            worldNeedsUpload(w);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        worldNeedsUpload(w);
                    }
                }
        );
    }

    /**
     * Loop through a series of files in a map, checking them with the cloud. If a file does not
     * exist, it will call worldNeedsUpload()
     *
     * @param user      The username for the cloud.
     * @param w         The world for the cloud.
     * @param filenames The filenames to test.
     */
    private void checkWorldFiles(final String user, final World w, List<String> filenames) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference reference = storage.getReference("maps").child(user);

        final List<String> nextFiles = new ArrayList<>();
        String testFile = null;
        for (String f : filenames) {
            if (testFile == null) {
                testFile = f;
            } else {
                nextFiles.add(f);
            }
        }

        if (testFile == null) {
            return;
        }

        if (mShutdown) {
            Log.i(TAG, "CheckWorldFiles shutdown");
            return;
        }

        Log.d(TAG, "Testing file for world: " + w.getUuid() + ": " + testFile);
        try {
            reference.child(w.getUuid()).child(testFile).getDownloadUrl().addOnFailureListener(
                    new OnFailureListener() {
                        @SuppressLint("LogConditional")
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            if (isWorldDataFileValid(w.getUuid())) {
                                Log.d(TAG, "World needs upload: " + w.getUuid(), e);
                                worldNeedsUpload(w);
                            } else {
                                Log.e(TAG,
                                        "Bad world (no data): " + w.getUuid() + ' ' + w.getName());
                            }
                        }
                    }).addOnSuccessListener(new OnSuccessListener<Uri>() {
                @Override
                public void onSuccess(Uri uri) {
                    if (!nextFiles.isEmpty()) {
                        checkWorldFiles(user, w, nextFiles);
                    } else {
                        synchronized (mParent) {
                            mUploadedWorlds.add(w.getUuid());
                        }
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            Log.w(TAG, "Rejected execution for " + w.getUuid() + " " + w.getName());
        }
    }

    /**
     * Called to signal that a world has been updated locally.
     *
     * @param uuid The world's uuid.
     */
    private void onWorldUpdated(String uuid) {
        synchronized (mParent) {
            if (mUploadedWorlds.contains(uuid)) {
                mUploadedWorlds.remove(uuid);
            }
        }
    }

    /**
     * Called internally when we know we need to upload a world to the cloud
     *
     * @param w The world to upload.
     */
    private void worldNeedsUpload(final World w) {
        PromptLink promptLink;
        synchronized (mParent) {
            promptLink = mPromptLink;
        }
        if (promptLink != null && !mShutdown) {
            if (mListener.lockExportState()) {
                promptLink.startWorldExport(this, w);
                // Calls exportedWorld() after prompt.
            } else {
                Log.i(TAG, "Upload failed to lock export state");
            }
        } else if (promptLink == null) {
            Log.i(TAG, "Upload failed since prompt link was null");
        } else {
            Log.i(TAG, "Upload failed for shutdown");
        }
    }

    /**
     * Check if we can upload a world (all files exported).
     *
     * @param parent The parent context.
     * @param w      The world.
     * @return True if we can upload it.
     */
    private boolean canUploadWorld(final Context parent, final World w) {
        final String userId = mUserId;
        return (userId != null && isWorldDataFileValid(w.getUuid()) &&
                new File(w.getAdfFile(parent)).exists() &&
                userId.equals(getWorldUserUuid(w.getUuid())));
    }

    /**
     * Called when we successfully export a world from an activity
     *
     * @param parent The parent activity.
     */
    private void exportedWorld(Activity parent) {
        synchronized (mParent) {
            Log.i(TAG, "World was exported");
            if (!mListener.isExportState()) {
                Log.i(TAG, "Ignored export since we are not in world state");
                return;
            }
            if (mWorlds != null) {
                for (World w : mWorlds) {
                    if (canUploadWorld(parent, w)) {
                        mStepsUntilExport += STEPS_PER_WORLD;
                        uploadWorld(parent, w);
                    }
                }
            }
            finishTest(parent);
        }
    }

    /**
     * Actually do the work of uploading a world to the cloud.
     *
     * @param parent The parent activity context.
     * @param w      The world itself.
     */
    @SuppressLint("LogConditional")
    private void uploadWorld(final Context parent, final World w) {
        Log.i(TAG, "Upload world: " + w.getName());
        // Ignore maps that do not have data ready
        if (!canUploadWorld(parent, w)) {
            return;
        }

        final String user = mUserId;
        if (user == null) {
            return;
        }

        String fileUser = getWorldUserUuid(w.getUuid());
        Log.d(TAG, "Try to upload world: " + w.getName() + ' ' + w.getUuid() + " " +
                ((fileUser != null) ? fileUser : "NULL"));

        if (fileUser == null || !fileUser.equals(user)) {
            Log.w(TAG, "Try to upload world failed, file user " +
                    ((fileUser != null) ? fileUser : "NULL") + " wanted " + user);
            return;
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference reference = storage.getReference("maps").child(user);

        Log.d(TAG, "Try to upload world: " + w.getName() + ' ' + w.getUuid());

        reference.child(w.getUuid()).child("adf")
                .putFile(Uri.fromFile(new File(w.getAdfFile(parent))))
                .addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (!new File(w.getAdfFile(parent)).delete()) {
                            Log.e(TAG, "Failed to delete locally stored file: "
                                    + w.getAdfFile(parent));
                        }

                        // Upload the DAT file
                        FirebaseStorage storage = FirebaseStorage.getInstance();
                        StorageReference reference = storage.getReference("maps").child(user);
                        reference.child(w.getUuid()).child("dat")
                                .putFile(Uri.fromFile(getWorldDataFile(w.getUuid())))
                                .addOnCompleteListener(
                                        new OnCompleteListener<UploadTask.TaskSnapshot>() {
                                            @Override
                                            public void onComplete(
                                                    @NonNull Task<UploadTask.TaskSnapshot> task) {
                                                // Save the database.
                                                DatabaseReference db =
                                                        FirebaseDatabase.getInstance()
                                                                .getReference();

                                                Map<String, Object> dbMap = new HashMap<>();
                                                dbMap.put("name", w.getName());
                                                dbMap.put("uuid", w.getUuid());
                                                dbMap.put("timestamp", ServerValue.TIMESTAMP);

                                                // Only update children here instead of set the
                                                // value, since there may be deleted flags or robots
                                                // in the map and we do not want to remove those.
                                                db.child("maps").child(user).child(w.getUuid())
                                                        .updateChildren(dbMap,
                                                                new DatabaseReference
                                                                        .CompletionListener() {
                                                                    @Override
                                                                    public void onComplete(
                                                                            DatabaseError
                                                                                    databaseError,
                                                                            DatabaseReference databaseReference) {
                                                                        worldTransmitted();
                                                                    }
                                                                });
                                            }
                                        });
                    }
                });
    }

    /**
     * World has been transmitted.
     */
    @SuppressLint("LogConditional")
    private void worldTransmitted() {
        synchronized (mParent) {
            mStepsUntilExport--;
            Log.i(TAG, "Transmitted, steps left: " + mStepsUntilExport);
            if (mStepsUntilExport <= 0) {
                mStepsUntilExport = 0;
                finishTest(null);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    ///////////////////////////// END UPLOAD PROCESS /////////////////////////////
    //////////////////////////////////////////////////////////////////////////////


    //////////////////////////////////////////////////////////////////////////////
    /////////////////////////// BEGIN DOWNLOAD PROCESS ///////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    /**
     * Lists all worlds in the cloud. Called at startup to set up the list, or when
     * world is changed on disk.
     */
    private void listCloudWorlds() {
        final String user = mUserId;
        if (user == null) {
            return;
        }

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("maps").child(user).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Map<String, Map<String, Object>> output = new HashMap<>();
                        for (DataSnapshot ds : dataSnapshot.getChildren()) {
                            if (ds.getValue() != null) {
                                output.put(ds.getKey(), (Map<String, Object>) ds.getValue());
                            }
                        }
                        onCloudWorldStateChanged(output);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error reading worlds from cloud: " + databaseError);
                    }
                }
        );
    }

    /**
     * Called when we get the states of the worlds. Called by listCloudWorlds() or by
     * the mWorldListener, which detects if the state of worlds in the cloud has changed.
     * E.g. another device uploaded a world.
     *
     * @param states The worlds in the cloud keyed by UUID.
     */
    private void onCloudWorldStateChanged(Map<String, Map<String, Object>> states) {
        if (states == null) {
            return;
        }
        Set<String> worlds;
        synchronized (mParent) {
            worlds = new HashSet<>();
            if (mWorlds != null) {
                for (World w : mWorlds) {
                    if (isWorldDataFileValid(w.getUuid())) {
                        worlds.add(w.getUuid());

                        // If the world is improperly stored in the cloud, and we have it locally,
                        // we should upload it again.
                        String worldUuid = w.getUuid();
                        if (states.containsKey(worldUuid)) {
                            if (!World.isWorldMapValid(states.get(worldUuid))
                                    && !states.get(w.getUuid()).containsKey("deleted")) {
                                worldIsProperlyStoredInCloud(w);
                            }
                        }
                    }
                }
            }

            // Only import worlds from cloud if it is enabled in the preferences.
            if (mShouldDownloadWorlds) {
                Log.d(TAG, "Import worlds from cloud enabled. Currently have "
                        + states.size() + " world states in the cloud");
                for (Map.Entry<String, Map<String, Object>> e : states.entrySet()) {
                    long time = 1, currentTime = 0;
                    if (e.getValue().containsKey("timestamp")) {
                        time = Long.parseLong(e.getValue().get("timestamp").toString());
                    }
                    if (mWorldTimes.containsKey(e.getKey())
                            && (mWorlds == null || worlds.contains(e.getKey()))) {
                        currentTime = mWorldTimes.get(e.getKey());
                    }
                    if (worlds.contains(e.getKey()) && e.getValue().containsKey("deleted")
                            && World.isWorldMapValid(e.getValue())) {
                        Log.i(TAG, "Deleting world: " + e.getKey());
                        removeWorld(new World(e.getValue()));
                    } else if (time > currentTime
                            && !e.getValue().containsKey("deleted")
                            && World.isWorldMapValid(e.getValue())) {
                        Log.i(TAG, "Get world " + e.getKey() + ", current update time " + time +
                                " on file " + currentTime);
                        if (mPromptLink != null) {
                            // Calls downloadWorldToTango() after locking
                            mPromptLink.startWorldDownload(this, new World(e.getValue()), time);
                            break;
                        } else {
                            Log.i(TAG, "Get world ignored for no PromptLink");
                        }
                    } else if (!e.getValue().containsKey("deleted")) {
                        Log.v(TAG, "Not downloading, time in cloud " + time + ", time on disk: "
                                + currentTime + ", valid: " + World.isWorldMapValid(e.getValue()));
                    }
                }
            } else {
                Log.d(TAG, "Import worlds from cloud disabled");
            }
        }
        mListener.onStateUpdate();
    }

    /**
     * Download a world from the cloud and import it to the tango.
     *
     * @param parent    The parent activity.
     * @param w         The world to import.
     * @param timestamp The timestamp of the world update.
     */
    private void downloadWorldToTango(final Activity parent, final World w, final long timestamp) {
        final String user = mUserId;
        if (user == null) {
            Log.e(TAG, "Tried to load a world with a null user");
            PromptLink promptLink = mPromptLink;
            if (promptLink != null) {
                promptLink.failedImport(this);
            }
            return;
        }

        if (!mListener.lockExportState()) {
            Log.i(TAG, "Locked export state failed in downloadWorldToTango");
            PromptLink promptLink = mPromptLink;
            if (promptLink != null) {
                promptLink.failedImport(this);
            }
            return;
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        final StorageReference reference = storage.getReference("maps")
                .child(user).child(w.getUuid());

        reference.child("dat").getFile(getWorldDataFile(w.getUuid()))
                .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG, "Downloaded " + w.getUuid() + " data file");
                        boolean haveAdf;
                        synchronized (mParent) {
                            if (mTango != null) {
                                try {
                                    ArrayList<String> worlds = mTango.listAreaDescriptions();
                                    haveAdf = worlds.contains(w.getUuid());
                                } catch (Exception exception) {
                                    Log.w(TAG, "Exception getting worlds", exception);
                                    haveAdf = true;
                                }
                            } else {
                                // If we have no tango, then we do not need to download the ADF
                                // file, since we have no use for it. If this occurs, then we will
                                // complete download the next time the tango is started.
                                haveAdf = true;
                            }
                            mWorldTimes.put(w.getUuid(), timestamp);
                            try {
                                FileOutputStream f = new FileOutputStream(
                                        getCloudWorldListFile());
                                byte[] buffer = new byte[16];
                                ByteBuffer b = ByteBuffer.wrap(buffer);
                                b.put((byte)0);
                                b.putInt(mWorldTimes.size());
                                f.write(buffer, 0, 5);
                                b.rewind();

                                for (Map.Entry<String, Long> worldTime : mWorldTimes.entrySet()) {
                                    byte[] str = worldTime.getKey().getBytes();
                                    b.putInt(str.length);
                                    f.write(buffer, 0, 4);
                                    b.rewind();
                                    f.write(str);
                                    b.putLong(worldTime.getValue());
                                    f.write(buffer, 0, 8);
                                    b.rewind();
                                }
                                f.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not write world_list.bin", e);
                            }
                        }
                        if (!haveAdf) {
                            Log.d(TAG, "Download ADF for world: " + w.getUuid());
                            reference.child("adf").getFile(new File(w.getAdfFile(parent)))
                                    .addOnSuccessListener(
                                            new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                                                @Override
                                                public void onSuccess(
                                                        FileDownloadTask.TaskSnapshot
                                                                nTaskSnapshot) {
                                                    Log.d(TAG, "Downloaded ADF for world: "
                                                            + w.getUuid() + ", importing...");
                                                    // Prompts user and then executes
                                                    // importedWorld()
                                                    w.importFromFile(parent);
                                                }
                                            })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.e(TAG, "Failed to download world: " + w.getUuid());
                                            synchronized (mParent) {
                                                // Will blacklist the world until the timestamp
                                                // of the world is changed, after a new upload
                                                mWorldTimes.put(w.getUuid(), timestamp);
                                                if (mPromptLink != null) {
                                                    // Calls failedImportExport() if we are in the correct state.
                                                    mPromptLink.failedImport(
                                                            CloudWorldManager.this);
                                                } else {
                                                    finishTest(parent);
                                                }
                                            }
                                        }
                                    });
                        } else {
                            Log.d(TAG, "Already have ADF for world: " + w.getUuid());
                            synchronized (mParent) {
                                if (mPromptLink != null) {
                                    // Calls failedImportExport() if we are in the correct state.
                                    mPromptLink.promptlessImport(CloudWorldManager.this);
                                } else {
                                    finishTest(parent);
                                }
                            }
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "Failed to download world: " + w.getUuid());
                synchronized (mParent) {
                    // Will blacklist the world until the timestamp
                    // of the world is changed, after a new upload
                    mWorldTimes.put(w.getUuid(), timestamp);
                    if (mPromptLink != null) {
                        // Calls importedWorld() if we are in the correct state.
                        mPromptLink.failedImport(CloudWorldManager.this);
                    } else {
                        finishTest(parent);
                    }
                }
            }
        });
    }

    /**
     * Called when a world is successfully imported
     *
     * @param parent The parent activity.
     */
    private void importedWorld(Activity parent) {
        synchronized (mParent) {
            finishTest(parent);
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////// END DOWNLOAD PROCESS ////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    /**
     * Cache the user uuids of the various worlds
     *
     * @param uuid The uuid of the world in question
     * @return The world user uuid, potentially null.
     */
    private String getWorldUserUuid(String uuid) {
        synchronized (mWorldUserUuids) {
            if (mWorldUserUuids.containsKey(uuid)) {
                return mWorldUserUuids.get(uuid);
            }
            try {
                String fileUser = DetailedWorld.getWorldUserUuid(
                        new FileInputStream(getWorldDataFile(uuid)), uuid);
                mWorldUserUuids.put(uuid, fileUser);
                return fileUser;
            } catch (IOException e) {
                Log.i(TAG, "Error reading world user uuid, ignoring: " + uuid);
                return null;
            }
        }
    }

    /**
     * Clean up the worlds by getting rid of garbage files.
     * @param parent The Activity to manage.
     */
    @SuppressLint("LogConditional")
    private void cleanupWorlds(Activity parent) {
        synchronized (mParent) {
            if (mWorlds == null) {
                return;
            }
            for (World w : mWorlds) {
                File f = new File(w.getAdfFile(parent));
                if (f.exists()) {
                    Log.i(TAG, "Deleting garbage file: " + f.getAbsolutePath());
                    if (!f.delete()) {
                        Log.e(TAG, "Failed to delete file " + f.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * Removes the world listener if it exists.
     */
    private void clearWorldListener() {
        synchronized (mParent) {
            if (mWorldListener != null && mUserId != null) {
                FirebaseDatabase.getInstance().getReference("maps").child(mUserId)
                        .removeEventListener(mWorldListener);
            }
        }
    }

    /**
     * Called when the user id changes.
     *
     * @param currentUserId The current user id.
     */
    private void onUserIdUpdate(String currentUserId) {
        synchronized (mParent) {
            if (((currentUserId == null) && (mUserId != null))
                    || ((currentUserId != null) && (mUserId == null))
                    || ((currentUserId != null) && !currentUserId.equals(mUserId))) {
                clearWorldListener();
                mUserId = currentUserId;

                if (currentUserId != null) {
                    mWorldListener = FirebaseDatabase.getInstance().getReference("maps")
                            .child(currentUserId)
                            .addValueEventListener(new ValueEventListener() {
                                @SuppressWarnings("unchecked")
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    Log.i(TAG, "World state changed: " + dataSnapshot.getValue());
                                    onCloudWorldStateChanged(
                                            (Map<String, Map<String, Object>>) dataSnapshot
                                                    .getValue());

                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                }
            }
        }
    }

    /**
     * Removes a world from the tango.
     *
     * @param rmWorld rmWorld The world to remove from the system.
     */
    public void removeWorld(World rmWorld) {
        synchronized (mParent) {
            if (mTango == null) {
                Log.i(TAG, "Tried to remove world but there is no tango.");
                return;
            }
            if (!mListener.lockExportState()) {
                Log.i(TAG, "Remove failed because we could not lock the listener state");
                return;
            }

            try {
                mTango.deleteAreaDescription(rmWorld.getUuid());
            } catch (TangoInvalidException e) {
                Log.w(TAG, "Exception trying to remove world ADF", e);
            }
            try {
                mParent.deleteFile(rmWorld.getUuid());
            } catch (Exception e) {
                Log.w(TAG, "Exception trying to remove world file", e);
            }

            finishTest(null);
        }
    }


    /**
     * Called when we fail to upload or download a world.
     *
     * @param parent The parent activity.
     */
    private void failImportExport(Activity parent) {
        synchronized (mParent) {
            finishTest(parent);
        }
    }

    /**
     * Set the prompt link.
     * @param link The prompt link. Could be null to clear the link.
     */
    public void setPromptLink(PromptLink link) {
        synchronized (mParent) {
            if (mPromptLink != null) {
                mPromptLink.freeManager(this);
            }
            mPromptLink = link;
            listCloudWorlds();
        }
    }

    /**
     * Saves a world from the Tango.
     *
     * @param tango    The tango.
     * @param mapName  The human-readable name of the map.
     * @param fileData The file data to be saved (from generateFileData).
     */
    public void saveFromTango(Tango tango, String mapName, byte[] fileData) {
        String uuid = saveFromTango(mParent, tango, mapName, fileData);
        if (uuid != null) {
            Log.i(TAG, "Flagging update");
            onWorldUpdated(uuid);
            Log.i(TAG, "Flagged update");
        } else {
            Log.w(TAG, "Uuid is null");
        }
    }

    /**
     * Saves a world from the Tango.
     * @param parent   The parent Android context for saving the files within.
     * @param tango    The tango.
     * @param mapName  The human-readable name of the map.
     * @param fileData The file data to be saved (from generateFileData).
     * @return The uuid of the new world.
     */
    public static String saveFromTango(Context parent, Tango tango, String mapName, byte[] fileData) {
        Log.i(TAG, "Saving: " + mapName);
        String uuid = tango.saveAreaDescription();
        if (uuid == null) {
            return null;
        }
        Log.i(TAG, "Saved ADF: " + uuid + " with " + mapName);
        TangoAreaDescriptionMetaData metadata = tango.loadAreaDescriptionMetaData(uuid);
        metadata.set(TangoAreaDescriptionMetaData.KEY_NAME, mapName.getBytes());
        Log.i(TAG, "Writing metadata for: " + uuid);
        tango.saveAreaDescriptionMetadata(uuid, metadata);

        File baseFile = new File(parent.getFilesDir(), CLOUD_WORLD_DATA_DIRECTORY);
        final String outputMapPath = new File(baseFile, uuid).toString();
        Log.i(TAG, "Writing file under: " + outputMapPath);
        try {
            FileOutputStream outputStream = new FileOutputStream(new File(outputMapPath), false);
            outputStream.write(fileData);
            outputStream.close();
        } catch (IOException e) {
            throw new java.lang.Error(e);
        }
        Log.i(TAG, "Saved map with UUID:" + uuid);
        return uuid;
    }

    /**
     * Load a world in to the tango for replay.
     *
     * @param w The world to load.
     * @param config The tango configuration.
     */
    public static void loadToTango(World w, TangoConfig config) {
        if (w.isVPS()) {
            config.putBoolean(KEY_BOOLEAN_USE_CLOUD_AREA_DESCRIPTION, true);
        } else {
            config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, w.getUuid());
        }
    }

    /**
     * Load a detailed world to the tango for replay.
     *
     * @param w The world to load.
     * @param params The smoother params.
     * @return The detailed world.
     */
    public DetailedWorld loadDetailedWorld(World w, SmootherParams params) {
        if (w.isVPS()) {
            return DetailedWorld.VPS_WORLD;
        }
        synchronized (mParent) {
            FileInputStream fs;
            try {
                fs = new FileInputStream(getWorldDataFile(w.getUuid()));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to load data file for world: ", e);
                return null;
            }
            return DetailedWorld.loadDetailedWorldFromInputStream(w.getUuid(), w.getName(), fs, params);
        }
    }

    /**
     * Gets if the manager is shutdown.
     * @return True if it is shutdown.
     */
    private boolean isShutdown() {
        return mShutdown;
    }
}
