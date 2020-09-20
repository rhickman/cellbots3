package ai.cellbots.robotlib;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import ai.cellbots.common.Strings;
import ai.cellbots.common.cloud.CloudFileManager;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.executive.ExecutivePlannerManager;


/**
 * Pulls down animations from the cloud and stores them.
 */
public class CloudAnimationManager {
    private static final String TAG = CloudAnimationManager.class.getSimpleName();

    private final CloudFileManager mGlobalManager;
    private final CloudFileManager mLocalManager;
    private final HashMap<String, Animation> mAnimations = new HashMap<>();
    @SuppressWarnings("unused")
    private final Context mParent;
    private String mAnimationsRobotUuid = null;
    private String mAnimationsUserUuid = null;
    private boolean mAnimationsRebuilt = false;
    private boolean mShutdown = false;

    public CloudAnimationManager(Context parent) {
        mParent = parent;

        mLocalManager = new CloudFileManager(parent, this,
                "local anims", "animation", "animation_list.bin",
                CloudPath.LOCAL_ANIMATIONS_PATH, CloudPath.LOCAL_ANIMATIONS_STORAGE_PATH,
                new CloudFileManager.Listener() {
                    @Override
                    public void onCloudFileManagerStatusUpdate() {
                        mAnimationsRebuilt = true;
                    }
                });

        mGlobalManager = new CloudFileManager(parent, this,
                "global anims", "global_animation", "animation_list.bin",
                CloudPath.GLOBAL_ANIMATIONS_PATH, CloudPath.GLOBAL_ANIMATIONS_STORAGE_PATH,
                new CloudFileManager.Listener() {
                    @Override
                    public void onCloudFileManagerStatusUpdate() {
                        mAnimationsRebuilt = true;
                    }
                });
    }

    /**
     * Parse an animation file.
     * @param filename The filename.
     * @param id The file id.
     * @param animFile The file.
     */
    private synchronized void processAnimationFile(String filename, String id, File animFile) {
        Log.d(TAG, "Build including: " + filename + " / " + id);
        try {
            FileInputStream file = new FileInputStream(animFile);
            StringBuilder fileBuffer = new StringBuilder();
            byte[] temp = new byte[128];

            while (true) {
                int ch = file.read(temp);
                if (ch <= 0) {
                    break;
                }
                fileBuffer.append(new String(temp, 0, ch));
            }

            file.close();

            List<Animation> animationList = Animation.readAnimationFile(fileBuffer.toString());
            if (animationList != null) {
                for (Animation a : animationList) {
                    mAnimations.put(a.getName(), a);
                }
            } else {
                Log.e(TAG, "Error parsing animations file: " + filename
                        + " / " + id);
            }
        } catch (IOException ex) {
            Log.w(TAG, "Failed to read animation file: " + filename
                    + " / " + id);
        }
    }

    /**
     * Parse all the files and rebuild the animations
     */
    private synchronized void rebuildAnimations() {
        Log.d(TAG, "Rebuilding animations");
        mAnimations.clear();
        for (CloudFileManager manager : new CloudFileManager[]{mGlobalManager, mLocalManager}) {
            for (String id : manager.listFileIdsSorted()) {
                processAnimationFile(manager.getFileName(id), id, manager.getFile(id));
            }
        }
        Log.d(TAG, "Animation count: " + mAnimations.size()
                + " for " + mAnimationsUserUuid + " " + mAnimationsRobotUuid);
    }

    /**
     * Sets the metadata of the robot.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid The robot uuid.
     */
    public synchronized void update(final String userUuid, final String robotUuid,
            ExecutivePlannerManager manager) {
        mGlobalManager.update(userUuid, robotUuid);
        mLocalManager.update(userUuid, robotUuid);
        // After we update the manager, if we have a new robot or user, or we have
        // new animations, then we must save that data.
        if ((!Strings.compare(robotUuid, mAnimationsRobotUuid) ||
                !Strings.compare(userUuid, mAnimationsUserUuid) ||
                mAnimationsRebuilt) && !mShutdown
                && mLocalManager.isFullySynchronized() && mGlobalManager.isFullySynchronized()
                && userUuid != null && robotUuid != null) {
            mAnimationsRebuilt = false;
            mAnimationsUserUuid = userUuid;
            mAnimationsRobotUuid = robotUuid;
            rebuildAnimations();
            // Upload the animations and set the manager
            HashMap<String, Object> animationsDb = new HashMap<>();
            for (Animation a : mAnimations.values()) {
                animationsDb.put(a.getName(), true);
            }
            FirebaseDatabase.getInstance().getReference("robot_goals")
                    .child(mAnimationsUserUuid).child(mAnimationsRobotUuid)
                    .child("animations").setValue(animationsDb);
            manager.setAnimations(userUuid, robotUuid, mAnimations);
        }
    }

    /**
     * Shutdown the system
     */
    public synchronized void shutdown() {
        if (!mShutdown) {
            mLocalManager.shutdown();
            mGlobalManager.shutdown();
            mShutdown = true;
        }
    }
}
