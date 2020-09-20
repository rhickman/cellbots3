package ai.cellbots.robot.tango;

import android.content.Context;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoErrorException;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.World;
import ai.cellbots.common.data.SmootherParams;
import ai.cellbots.robot.slam.WorldManager;
import ai.cellbots.tangocommon.CloudWorldManager;

/**
 * Load a list of worlds from the Tango, and import worlds from the cloud.
 */
public class TangoWorldManager extends WorldManager {
    private static final String TAG = TangoWorldManager.class.getSimpleName();

    private final CloudWorldManager mCloudWorldManager;
    private final Tango mTango;

    // True if the manager tries to shut down herself.
    private boolean mTryShutdown;

    public TangoWorldManager(Context parent, String userUuid, Listener listener) {
        super(userUuid, listener);
        mTryShutdown = false;
        mCloudWorldManager = new CloudWorldManager(parent, new CloudWorldManager.Listener() {
            @Override
            public boolean lockExportState() {
                synchronized (TangoWorldManager.this) {
                    if (getState() == WorldManager.State.WAITING) {
                        setState(WorldManager.State.LOCKED);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void clearExportState() {
                synchronized (TangoWorldManager.this) {
                    if (getState() == WorldManager.State.LOCKED) {
                        setState(WorldManager.State.WAITING);
                        if (mTryShutdown) {
                            shutdown();
                        }
                    }
                }
            }

            @Override
            public boolean isExportState() {
                return getState() == WorldManager.State.LOCKED;
            }

            @Override
            public void onStateUpdate() {
                TangoWorldManager.this.onStateUpdate();
            }
        });
        mTango = new Tango(parent, new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        onTangoStarted();
                    }
                }).start();
            }
        });
    }

    /**
     * Called when the tango is started.
     */
    private synchronized void onTangoStarted() {
        // Skip the init process if we are already shutdown.
        if (getState() != State.INITIALIZING) {
            if (getState() != State.SHUTDOWN && getState() != State.STOPPING) {
                Log.w(TAG, "onTangoStarted() in invalid state: " + getState());
            } else {
                Log.i(TAG, "onTangoStarted() called after shutdown");
            }
            return;
        }
        setState(State.WAITING);
        mCloudWorldManager.setShouldDownloadWorlds(true);
        mCloudWorldManager.setTango(mTango);
        mCloudWorldManager.refreshAndGetWorlds();
    }

    /**
     * Sets the promptLink for cloud prompts.
     * @param promptLink The promptLink.
     */
    public void setPromptLink(CloudWorldManager.PromptLink promptLink) {
        mCloudWorldManager.setPromptLink(promptLink);
    }

    private Thread mShutdownThread;

    /**
     * Shuts down the TangoWorldManager.
     */
    @Override
    protected synchronized void onShutdown() {
        mTryShutdown = true;
        if (getState() == State.SHUTDOWN || getState() == State.STOPPING) {
            return;
        }
        if (getState() == State.WAITING || getState() == State.INITIALIZING) {
            setState(State.STOPPING);
            mShutdownThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    mCloudWorldManager.setTango(null);
                    try {
                        mTango.disconnect();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, "Ignore error:", e);
                    } catch (IllegalArgumentException e) {
                        // Do nothing, since the tango is already disconnected by service shutdown.
                        Log.w(TAG, "Ignore exception:", e);
                    }
                    synchronized (TangoWorldManager.this) {
                        mCloudWorldManager.shutdown();
                        setState(State.SHUTDOWN);
                    }
                }
            });
            mShutdownThread.start();
        }
    }

    /**
     * Shuts down the TangoWorldManager and wait for it to finish.
     */
    @Override
    protected void onWaitShutdown() {
        shutdown();
        if (mShutdownThread != null) {
            try {
                mShutdownThread.join();
            } catch (InterruptedException e) {
                Log.w(TAG, "Shutdown thread was interrupted:", e);
            }
            mShutdownThread = null;
        }
    }

    /**
     * Gets the list of worlds.
     *
     * @return An array of Worlds, or null if the list could not be obtained.
     */
    @Override
    public synchronized World[] getWorlds() {
        if (getState() != State.WAITING) {
            return null;
        }
        World[] worlds = mCloudWorldManager.getWorlds();
        if (worlds != null) {
            return worlds.clone();
        }
        return null;
    }

    /**
     * Loads a detailed world.
     *
     * @param uuid The new world uuid.
     * @return The detailed world, or null if could not be loaded.
     */
    @Override
    public synchronized DetailedWorld getDetailedWorld(String uuid) {
        if (getState() != State.WAITING) {
            return null;
        }
        World[] worlds = mCloudWorldManager.getWorlds();
        if (worlds == null) {
            return null;
        }
        for (World world : worlds) {
            if (world.getUuid().equals(uuid)) {
                return mCloudWorldManager.loadDetailedWorld(world, new SmootherParams());
            }
        }
        return null;
    }

    /**
     * Removes a world locally, forcing re-download.
     *
     * @param world The world to remove.
     */
    public synchronized void removeWorld(World world) {
        mCloudWorldManager.removeWorld(world);
    }
}
