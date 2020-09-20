package ai.cellbots.robotlib;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;

import ai.cellbots.common.cloud.CloudFileManager;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudSingletonQueueMonitor;
import ai.cellbots.common.data.RobotSound;

/**
 * Manages sounds coming down from the cloud to be played on demand. The system uses two
 * CloudFileManagers to handle the sounds. The sounds are stored on dik in the global_sounds and
 * sounds folders. The system has the isSynchronized() flag to tell the robot when sounds are
 * synchronized and it can begin.
 */
public class CloudSoundManager {
    private static final String TAG = CloudSoundManager.class.getSimpleName();
    private final CloudSingletonQueueMonitor<RobotSound, SoundManager.Sound> mCloudQueue;
    private final CloudFileManager mLocalManager;
    private final CloudFileManager mGlobalManager;
    private final SoundManager mManager;
    private boolean mFullSync;

    /**
     * Returns true if we are synchronized with initial set of updates of sounds. The robot should
     * wait for synchronization before taking any actions as it could request a sound before
     * download of the sounds, thus leading to the sound failing to play.
     * @return True if the sounds are synchronized.
     */
    public synchronized boolean isSynchronized() {
        return mFullSync;
    }

    /**
     * Called to create the CloudSoundManager.
     * @param parent The parent context.
     */
    public CloudSoundManager(Context parent) {
        mFullSync = false;
        mManager = new SoundManager(parent);

        mLocalManager = new CloudFileManager(parent, this, "local sounds", "sound",
                "sound_list.bin", CloudPath.LOCAL_SOUNDS_PATH, CloudPath.LOCAL_SOUNDS_STORAGE_PATH);

        mGlobalManager = new CloudFileManager(parent, this,
                "global sounds", "global_sound", "sound_list.bin",
                CloudPath.GLOBAL_SOUNDS_PATH, CloudPath.GLOBAL_SOUNDS_STORAGE_PATH);

        mCloudQueue = new CloudSingletonQueueMonitor<>(parent, this, CloudPath.ROBOT_SOUND_QUEUE_PATH,
                new CloudSingletonQueueMonitor.Listener<RobotSound, SoundManager.Sound>() {
                    /**
                     * Called when to valid a new sound in the queue.
                     * @param data The element.
                     * @return True if the sound can be played.
                     */
                    @Override
                    public boolean dataElementValid(RobotSound data) {
                        synchronized (CloudSoundManager.this) {
                            // We consider elements valid if the managers have not synchronized yet,
                            // so that they sit in the queue while the managers are downloading.
                            return (!mLocalManager.isSynchronized()
                                    || mLocalManager.fileExists(data.getId())
                                    || !mGlobalManager.isSynchronized()
                                    || mGlobalManager.fileExists(data.getId()));
                        }
                    }
                    /**
                     * Called to stop a sound.
                     * @param stored The element to stop.
                     */
                    @Override
                    public void storedElementFinish(SoundManager.Sound stored) {
                        stored.stop();
                    }
                    /**
                     * Checks if the stored sound is finished.
                     * @param stored The element to check.
                     * @return True if the sound has stopped playing.
                     */
                    @Override
                    public boolean storedElementIsFinished(SoundManager.Sound stored) {
                        return !stored.isPlaying();
                    }
                    /**
                     * Create a Sound element from the cloud RobotSound.
                     * @param data The element.
                     * @return The sound.
                     */
                    @Override
                    public SoundManager.Sound storedElementFromDataElement(RobotSound data) {
                        return playSoundById(data.getId(), SoundManager.SoundLevel.SOUND_BOARD);
                    }

                    /**
                     * Called when the listener is terminated. Does nothing.
                     */
                    @Override
                    public void afterListenerTerminated() {
                        mFullSync = false;
                    }

                    /**
                     * Called when the listener is started. Does nothing.
                     */
                    @Override
                    public void beforeListenerStarted() {

                    }
                }, RobotSound.FACTORY);
        Log.i(TAG, "CloudSoundManager created");
    }

    /**
     * Shutdown the system.
     */
    public synchronized void shutdown() {
        mLocalManager.shutdown();
        mGlobalManager.shutdown();
        mCloudQueue.shutdown();
        mManager.shutdown();
    }

    /**
     * Plays a sound
     * @param sound The sound to play.
     * @param soundLevel The sound level to play.
     */
    public synchronized SoundManager.Sound playSound(String sound, SoundManager.SoundLevel soundLevel) {
        return playSound(sound, soundLevel, false);
    }

    /**
     * Plays a sound
     * @param sound The sound to play.
     * @param soundLevel The sound level to play.
     * @param looping If true, the sound will loop until stopped.
     */
    public synchronized SoundManager.Sound playSound(String sound, SoundManager.SoundLevel soundLevel, boolean looping) {
        Log.i(TAG, "Try to play sound: " + sound);
        String id = mLocalManager.getIdForName(sound);
        if (id != null) {
            File f = mLocalManager.getFile(id);
            if (f != null) {
                return mManager.playUriSound(Uri.fromFile(f), soundLevel, looping);
            }
        }
        id = mGlobalManager.getIdForName(sound);
        if (id != null) {
            File f = mGlobalManager.getFile(id);
            if (f != null) {
                return mManager.playUriSound(Uri.fromFile(f), soundLevel, looping);
            }
        }
        return null;
    }
    /**
     * Play sound by ID
     * @param soundId The sound to play.
     * @param soundLevel The sound level to play.
     */
    private synchronized SoundManager.Sound playSoundById(String soundId, SoundManager.SoundLevel soundLevel) {
        File f = mLocalManager.getFile(soundId);
        if (f == null) {
            f = mGlobalManager.getFile(soundId);
            if (f == null) {
                return null;
            }
        }
        return mManager.playUriSound(Uri.fromFile(f), soundLevel, false);
    }

    /**
     * Play a goal sound.
     * @param sound The sound type.
     * @param priority The priority.
     */
    public synchronized SoundManager.Sound playGoalSound(SoundManager.GoalSound sound, long priority) {
        boolean user = (priority >= 100);
        SoundManager.SoundLevel level =
                user ? SoundManager.SoundLevel.USER : SoundManager.SoundLevel.AUTOGEN;
        return playSound("STATIC_" + level + "_" + sound, level,
                sound == SoundManager.GoalSound.ALARM);
    }

    /**
     * Play a command sound.
     * @param sound The sound type.
     */
    public synchronized SoundManager.Sound playCommandSound(SoundManager.CommandSound sound) {
        return playSound("STATIC_" + SoundManager.SoundLevel.COMMAND + "_" + sound,
                SoundManager.SoundLevel.COMMAND);
    }

    /**
     * Sets the metadata of the robot.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid The robot uuid.
     */
    public synchronized void update(final String userUuid, final String robotUuid) {
        mLocalManager.update(userUuid, robotUuid);
        mGlobalManager.update(userUuid, robotUuid);
        mCloudQueue.update(userUuid, robotUuid);
        mManager.update();
        if (!mFullSync
                && mLocalManager.isFullySynchronized()
                && mGlobalManager.isFullySynchronized()) {
            mFullSync = true;
        }
    }

    /**
     * Stops all existing sounds at a given sound level.
     * @param level Stop sounds at this level.
     */
    public synchronized void stopAllSounds(SoundManager.SoundLevel level) {
        mManager.stopAllSounds(level);
    }

    /**
     * Stops all existing sounds at all levels.
     */
    public synchronized void stopAllSounds() {
        mManager.stopAllSounds();
    }
}
