package ai.cellbots.common.cloud;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.storage.FileDownloadTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.cellbots.common.FileUtil;
import ai.cellbots.common.Strings;
import ai.cellbots.common.data.FileInfo;

/**
 * Manages the download of files from a folder in the cloud. For example, the sounds and animations
 * are downloaded through cloud file managers. The folder handles synchronization properly as well.
 *
 * In the cloud, the files are stored as a list of FileInfo objects. These objects have a name and
 * ID, as well as timestamp. The ID is the database key while the name and timestamp are stored
 * fields. The ID is needed because many characters cannot be part of firebase keys, and thus the
 * ID must be some encoding of the real filename.
 *
 * On disk, the files are stored in a directory in the local storage of the application, under the
 * mDirectory file. Additionally, a file called statusFile is stored in the directory that stores
 * some metadata about each file. Within the directory, the files themselves are prefixed with
 * 1_ID where ID is the ID from the cloud. There are also files prefix with 0_N which are temporary
 * files used to during the download process.
 *
 * The file manager API is a bit tricky because of the multi-threading and synchronization states.
 * As the manager executes, it may have to switch file folders within the cloud due to user and
 * robot id changes. This logic is handled by the underlying CloudSingletonMonitor (see that class
 * for the full logic). When a change occurs, the files on disk are deleted and reloaded from the
 * cloud in parallel.
 *
 * The system stores two lists in memory. The first is the name list, which stores timestamps and
 * filenames. The filenames are updated in realtime by the database. The timestamps are updated when
 * the files are downloaded from storage. There are thus two states of synchronization: simple
 * synchronization (isSynchronized()) and full synchronized (isFullySynchronized()). In simple
 * synchronization the list of filenames is guaranteed to be a recent update from the cloud. In
 * full synchronization, the demands of full synchronization are met, additionally all files are
 * listed are readable.
 *
 * Locally the files are stored in the local storage of the Android App. Thus, other apps running on
 * the same device cannot access the files stored on disk. The CloudFileManager's access depends on
 * if user and or robot are specified in the path. The CloudFileManager prevents access to another
 * users' or robots' files locally when a user or robot switch occurs. In the database and cloud
 * storage, security rules are required to prevent download of files by unauthorized parties.
 */
public class CloudFileManager {
    private static final String TAG = CloudFileManager.class.getSimpleName();

    /**
     * The listener for the system.
     */
    public interface Listener {
        /**
         * Called whenever the status of files is updated.
         */
        void onCloudFileManagerStatusUpdate();
    }

    private final Object mLock; // The object for locking.
    private final Context mParent; // The parent context for file storage.
    private final Listener mListener; // The listener for status updates, or null.
    private final String mName; // The name of the CloudFileManager, for debug.
    private final String mDirectory; // The local directory for storing files.
    private final String mStatusFile; // The status filename in the local directory.
    private final CloudPath mStoragePath; // The storage path in the cloud storage.
    private final HashMap<String, String> mNameById = new HashMap<>(); // The names of files known.
    private final HashMap<String, Long> mTimestampById = new HashMap<>(); // The timestamps of the local files.
    private final boolean mHasUser; // True if the path has a user component.
    private final boolean mHasRobot; // True if the path has a robot component.

    private boolean mShutdown = false; // True if the system has shutdown.
    private boolean mListSync = false; // True if the list is synchronized.

    private final CloudSingletonMonitor mMonitor; // Monitors the file list.

    private String mFileUserUuid = null; // Stores the current user uuid of the files disk.
    private String mFileRobotUuid = null; // Stores the current robot uuid of the files on disk.

    private int mFileCounter = 0; // Counter for temporary files.

    /**
     * Gets the absolute path to the status file.
     * @return The status file path.
     */
    private String getStatusFilePath() {
        return new File(new File(mParent.getFilesDir(), mDirectory), mStatusFile).toString();
    }

    /**
     * Gets the filename on disk of a file.
     * @param id The file id.
     * @param timestamp The timestamp.
     * @return The filename.
     */
    private String getFileName(String id, long timestamp) {
        return "1_" + id + "_" + timestamp;
    }

    /**
     * Gets the absolute path of a file.
     * @param id The file id.
     * @param timestamp The timestamp.
     * @return The filename.
     */
    private String getFilePath(String id, long timestamp) {
        return new File(new File(mParent.getFilesDir(), mDirectory), getFileName(id, timestamp)).toString();
    }

    /**
     * Posts CloudFileManager status updates to the listener.
     */
    private void onStatusUpdate() {
        if (mListener != null) {
            mListener.onCloudFileManagerStatusUpdate();
        }
    }

    /**
     * Read a string from the file, reading a 32-bit byte length and then the bytes of the string.
     * @param f The file.
     * @param name The name to be printed in an error.
     * @return The string or null if there was an error.
     * @throws IOException Thrown if an IO error occurs.
     */
    private String readStringFromFile(FileInputStream f, String name) throws IOException {
        byte[] buffer = new byte[4];
        ByteBuffer b = ByteBuffer.wrap(buffer);
        b.rewind();
        if (f.read(buffer, 0, 4) != 4) {
            Log.e(TAG, mName + ": Invalid length for " + name);
            f.close();
            return null;
        }
        int count = b.getInt();
        if (count < 0) {
            Log.e(TAG, mName + ": Bad " + name + " length: " + count);
            f.close();
            return null;
        }
        byte[] strBytes = new byte[count];
        if (f.read(strBytes, 0, count) != count) {
            f.close();
            Log.e(TAG, mName + " Could not read " + name + " length: " + count);
            return null;
        }
        try {
            return new String(strBytes);
        } catch (Exception e) {
            f.close();
            Log.e(TAG, mName + " Could not read " + name + " length: " + count);
            return null;
        }
    }

    /**
     * Load the status file from disk.
     * @return True if loading was successful.
     */
    private boolean loadStatusFile() {
        String statusFilePath = getStatusFilePath();
        File statusFile = new File(statusFilePath);
        synchronized (mLock) {
            if (!(statusFile.isFile())) {
                Log.w(TAG, "Skipping " + mName + " startup, no file: " + statusFilePath);
                return false;
            }
            String user = null;
            String robot = null;
            Map<String, Long> timestampById = new HashMap<>();
            Map<String, String> nameById = new HashMap<>();
            // TODO(playerone) Use protobuf.
            try {
                FileInputStream f = new FileInputStream(statusFile);
                byte[] buffer = new byte[16];
                ByteBuffer b = ByteBuffer.wrap(buffer);
                if (f.read(buffer, 0, 2) != 2) {
                    f.close();
                    Log.e(TAG, mName + ": Could not read initial bytes");
                    return false;
                }
                byte version = b.get();
                if (version != 1) {
                    f.close();
                    Log.e(TAG, mName + ": Bad version: " + version);
                    return false;
                }
                byte flags = b.get();
                if (((flags & 0x1) == 1) != mHasUser) {
                    f.close();
                    Log.e(TAG, mName + ": File has user: " + ((flags & 0x1) == 1) + " wanted: "
                            + mHasUser);
                    Log.e(TAG,
                            "File flag: " + flags + " user: " + (flags & 0x1) + " robot: " + (flags
                                    & 0x2));
                    return false;
                }
                if (((flags & 0x2) == 2) != mHasRobot) {
                    f.close();
                    Log.e(TAG, mName + ": File has robot: " + ((flags & 0x2) == 2) + " wanted: "
                            + mHasRobot);
                    Log.e(TAG,
                            "File flag: " + flags + " user: " + (flags & 0x1) + " robot: " + (flags
                                    & 0x2));
                    return false;
                }

                if (mHasUser) {
                    user = readStringFromFile(f, "user uuid");
                    if (user == null) {
                        return false;
                    }
                }

                if (mHasRobot) {
                    robot = readStringFromFile(f, "robot uuid");
                    if (robot == null) {
                        return false;
                    }
                }

                b.rewind();
                if (f.read(buffer, 0, 4) != 4) {
                    f.close();
                    Log.e(TAG, mName + ": Could not read count bytes");
                    return false;
                }
                int count = b.getInt();
                if (count < 0) {
                    f.close();
                    Log.e(TAG, mName + ": Count bytes are negative");
                    return false;
                }

                for (int i = 0; i < count; i++) {
                    String id = readStringFromFile(f, "id element #" + i);
                    if (id == null) {
                        return false;
                    }
                    String name = readStringFromFile(f, "name element #" + i);
                    if (name == null) {
                        return false;
                    }
                    b.rewind();
                    if (f.read(buffer, 0, 8) != 8) {
                        f.close();
                        Log.e(TAG, mName + ": Could not read timestamp bytes");
                        return false;
                    }
                    long timestamp = b.getLong();
                    nameById.put(id, name);
                    if (new File(getFilePath(id, timestamp)).isFile()) {
                        timestampById.put(id, timestamp);
                    } else {
                        Log.v(TAG,
                                mName + ": File does not exist, attempting re-download: " + id + " "
                                        + name);
                    }
                }
                f.close();
            } catch (IOException e) {
                Log.e(TAG, mName + ": Could not read file", e);
                return false;
            }
            mFileRobotUuid = robot;
            mFileUserUuid = user;
            mTimestampById.clear();
            mTimestampById.putAll(timestampById);
            mNameById.clear();
            mNameById.putAll(nameById);
            onStatusUpdate();
        }
        Log.i(TAG, "Successfully read the status file: " + statusFilePath);
        return true;
    }

    /**
     * Write a status file.
     * @return True if writing was successful.
     */
    private boolean writeStatusFile() {
        synchronized (mLock) {
            if (!createStorageFolder()) {
                return false;
            }
            try {
                // TODO: re-write using protobuf
                FileOutputStream f = new FileOutputStream(new File(getStatusFilePath()));
                byte[] buffer = new byte[16];
                ByteBuffer b = ByteBuffer.wrap(buffer);
                b.put((byte) 1); //Version = 1
                byte flags = 0;
                if (mHasUser) {
                    flags += 1;
                }
                if (mHasRobot) {
                    flags += 2;
                }
                b.put(flags);
                Log.v(TAG, mName + ": Write file, version: " + buffer[0] + " flags: " + buffer[1]);
                f.write(buffer, 0, 2);

                if (mHasUser) {
                    if (mMonitor.getUserUuid() == null) {
                        Log.e(TAG, mName + ": attempt to write without user uuid");
                        return false;
                    }
                    byte[] str = mMonitor.getUserUuid().getBytes();
                    b.rewind();
                    b.putInt(str.length);
                    f.write(buffer, 0, 4);
                    f.write(str);
                }

                if (mHasRobot) {
                    if (mMonitor.getRobotUuid() == null) {
                        Log.e(TAG, mName + ": attempt to write without robot uuid");
                        return false;
                    }
                    byte[] str = mMonitor.getRobotUuid().getBytes();
                    b.rewind();
                    b.putInt(str.length);
                    f.write(buffer, 0, 4);
                    f.write(str);
                }

                b.rewind();
                b.putInt(mTimestampById.size());
                f.write(buffer, 0, 4);

                for (Map.Entry<String, Long> e : mTimestampById.entrySet()) {
                    byte[] str = e.getKey().getBytes();
                    b.rewind();
                    b.putInt(str.length);
                    f.write(buffer, 0, 4);
                    f.write(str);

                    str = mNameById.get(e.getKey()).getBytes();
                    b.rewind();
                    b.putInt(str.length);
                    f.write(buffer, 0, 4);
                    f.write(str);

                    b.rewind();
                    b.putLong(e.getValue());
                    f.write(buffer, 0, 8);
                }

                f.close();
            } catch (IOException e) {
                Log.e(TAG, mName + ": Could not write file", e);
                return false;
            }
            mFileUserUuid = null;
            mFileRobotUuid = null;
            if (mHasUser) {
                mFileUserUuid = mMonitor.getUserUuid();
            }
            if (mHasRobot) {
                mFileRobotUuid = mMonitor.getRobotUuid();
            }
        }
        return true;
    }

    /**
     * Creates the file folder.
     *
     * @return True if the folder creation worked.
     */
    private boolean createStorageFolder() {
        String path = FileUtil.joinPath(mParent.getFilesDir().toString(), mDirectory);
        return FileUtil.createLocalPath(path);
    }

    /**
     * Creates the cloud manager.
     * @param parent The parent context to access the disk files.
     * @param lock The lock for synchronization purposes.
     * @param name The name of the FileManager, for debug printing.
     * @param directory The directory to store files on disk.
     * @param statusFile The status filename within the directory.
     * @param databasePath The database path to read files from.
     * @param storagePath The storage path to read files from.
     */
    public CloudFileManager(@NonNull Context parent, @NonNull Object lock,  @NonNull String name,
            @NonNull String directory, @NonNull String statusFile,
            @NonNull CloudPath databasePath, @NonNull CloudPath storagePath) {
        this(parent, lock, name, directory, statusFile, databasePath, storagePath, null);
    }

    /**
     * Creates the cloud manager.
     * @param parent The parent context to access the disk files.
     * @param lock The lock for synchronization purposes.
     * @param name The name of the FileManager, for debug printing.
     * @param directory The directory to store files on disk.
     * @param statusFile The status filename within the directory.
     * @param databasePath The database path to read files from.
     * @param storagePath The storage path to read files from.
     * @param listener The listener for the class.
     */
    public CloudFileManager(@NonNull Context parent, @NonNull Object lock, @NonNull String name,
            @NonNull String directory, @NonNull String statusFile,
            @NonNull CloudPath databasePath, @NonNull CloudPath storagePath, Listener listener) {
        mLock = lock;
        mParent = parent;
        mName = name;
        mListener = listener;
        mDirectory = directory;
        mStatusFile = statusFile;
        mStoragePath = storagePath;
        mHasUser = databasePath.hasUserPath();
        mHasRobot = databasePath.hasRobotPath();
        mMonitor = new CloudSingletonMonitor(mLock, databasePath,
                new CloudSingletonMonitor.Listener() {
                    @Override
                    public void onDataSnapshot(DataSnapshot dataSnapshot) {
                        onData(dataSnapshot);
                    }

                    @Override
                    public void afterListenerTerminated() {
                        mListSync = false;
                    }

                    @Override
                    public void beforeListenerStarted() {
                        Log.v(TAG, mName  + ": start listener");
                        if ((mHasRobot && !Strings.compare(mFileRobotUuid, mMonitor.getRobotUuid()))
                                || (mHasUser && !Strings.compare(mFileUserUuid, mMonitor.getUserUuid()))) {
                            mFileRobotUuid = null;
                            mFileUserUuid = null;
                            mTimestampById.clear();
                            mNameById.clear();
                            Log.v(TAG, mName + ": delete all files");
                            // Update the listener
                            onStatusUpdate();
                        }
                    }
                });

        if (mStoragePath.hasUserPath() != mHasUser) {
            throw new Error("Storage path must include the user if and only if the db path does");
        }
        if (mStoragePath.hasRobotPath() != mHasRobot) {
            throw new Error("Storage path must include the robot if and only if the db path does");
        }
        if (!mStoragePath.hasEntityPath()) {
            throw new Error("The storage path must include the entity");
        }
        if (databasePath.hasEntityPath()) {
            throw new Error("The database path must not include the entity");
        }

        char c = mStatusFile.charAt(0);
        if (c == '0' || (c >= '1' && c <= '9')) {
            throw new Error(name + ": status file invalid: "
                    + mStatusFile + " - must not start with a number");
        }

        loadStatusFile();
    }

    /**
     * Called when a new DataSnapshot is taken.
     * @param dataSnapshot The DataSnapshot.
     */
    private void onData(DataSnapshot dataSnapshot) {
        Log.v(TAG, mName + ": file storage changed");
        synchronized (mLock) {
            if (!createStorageFolder()) {
                Log.e(TAG, "Ignore listener value: unable to create storage folder");
                return;
            }
            final String userUuid = mMonitor.getUserUuid();
            final String robotUuid = mMonitor.getRobotUuid();
            mNameById.clear();
            for (DataSnapshot child : dataSnapshot.getChildren()) {
                FileInfo fileInfoT = null;
                try {
                    fileInfoT = FileInfo.fromFirebase(child);
                } catch (DatabaseException error) {
                    CloudLog.reportFirebaseFormattingError(mParent, child, error.toString());
                }
                if (fileInfoT == null || fileInfoT.getTimestamp() < 0
                        || fileInfoT.getId() == null || fileInfoT.getName() == null) {
                    continue;
                }
                mNameById.put(fileInfoT.getId(), fileInfoT.getName());
                if (mTimestampById.containsKey(fileInfoT.getId())) {
                    if (mTimestampById.get(fileInfoT.getId()) == fileInfoT.getTimestamp()) {
                        continue;
                    }
                }

                mFileCounter++;
                if (mFileCounter < 0) {
                    mFileCounter = 0;
                }
                final FileInfo fileInfo = fileInfoT;
                final String fn = new File(new File(mParent.getFilesDir(), mDirectory),
                        "0_" + mFileCounter).getAbsolutePath();
                Log.v(TAG, "Download file: " + fn + " from " + fileInfo.getId() + " "
                        + fileInfoT.getName());
                mStoragePath.getStorageReference(userUuid, robotUuid, fileInfo.getId())
                        .getFile(new File(fn))
                        .addOnSuccessListener(
                                new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            FileDownloadTask.TaskSnapshot taskSnapshot) {
                                        Log.v(TAG, mName + ": Save file: " + fileInfo.getName());
                                        synchronized (mLock) {
                                            // If we are shutdown, ignore data
                                            if (mShutdown) {
                                                deleteFile(fn);
                                                return;
                                            }
                                            // Ignore the file if it is not owned by the correct
                                            // robot and/or user.
                                            if ((!Strings.compare(userUuid, mMonitor.getUserUuid()) && mHasUser)
                                                    || (!Strings.compare(mMonitor.getRobotUuid(), robotUuid) && mHasRobot)) {
                                                deleteFile(fn);
                                                return;
                                            }
                                            // If the timestamp is already set, bail if we are
                                            // introducing an old version of the file.
                                            if (mTimestampById.containsKey(fileInfo.getId())) {
                                                if (mTimestampById.get(fileInfo.getId())
                                                        >= fileInfo.getTimestamp()) {
                                                    deleteFile(fn);
                                                    return;
                                                }
                                            }
                                            // Rename the temporary file to the correct file.
                                            if (!new File(fn)
                                                    .renameTo(new File(getFilePath(fileInfo.getId(),
                                                            fileInfo.getTimestamp())))) {
                                                Log.w(TAG, "Failed to rename the temporary file");
                                                return;
                                            }

                                            // Store the new timestamp, so that the file will be
                                            // complete.
                                            mTimestampById.put(fileInfo.getId(),
                                                    fileInfo.getTimestamp());

                                            // Clean up the files before we save
                                            cleanupFiles();

                                            // Write the information
                                            writeStatusFile();

                                            // Update the listener
                                            onStatusUpdate();
                                        }

                                    }
                                })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                synchronized (mLock) {
                                    Log.w(TAG, "File failed to download: " + fn);
                                    if (mShutdown) {
                                        return;
                                    }
                                    // Do not delete the file because we could end up deleting
                                    // another file.
                                    deleteFile(fn);
                                }
                            }
                        });
            }

            cleanupFiles();

            // The initial list has been synchronized, so we can now start reading.
            Log.i(TAG, "List sync done: " + mName);
            mListSync = true;

            // Update the listener
            onStatusUpdate();
        }
    }

    /**
     * Cleans up the files on disk.
     */
    private void cleanupFiles() {
        synchronized (mLock) {
            Set<String> badIds = new HashSet<>();
            for (String id : mTimestampById.keySet()) {
                if (!mNameById.containsKey(id)) {
                    Log.v(TAG, mName
                            + ": A file is stored locally but is no longer in the database: " + id);
                    badIds.add(id);
                }
            }
            for (String id : badIds) {
                mTimestampById.remove(id);
            }
            Set<String> whitelist = new HashSet<>();
            whitelist.add(mStatusFile);
            for (Map.Entry<String, Long> f : mTimestampById.entrySet()) {
                whitelist.add(getFileName(f.getKey(), f.getValue()));
            }
            for (File fn : new File(mParent.getFilesDir(), mDirectory).listFiles()) {
                if (whitelist.contains(fn.getName())) {
                    continue;
                }
                // Ignore temporary files.
                if (!fn.getName().isEmpty() && fn.getName().substring(0, 1).equals("0")) {
                    continue;
                }
                Log.v(TAG, "Delete file: " + fn.getName());
                if (!fn.delete()) {
                    Log.e(TAG, "Unable to delete file: " + fn.getName());
                }
            }
        }
    }

    /**
     * Gets if the system is synchronized. If true, the list of file names is a recent
     * update of the data from cloud.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    // TODO(playerone) Remove this lock.
    public boolean isSynchronized() {
        synchronized (mLock) {
            return mListSync;
        }
    }

    /**
     * Gets if the system is fulled synchronized, e.g. all files are downloaded and the system has
     * a recent update of the data from firebase.
     */
    public boolean isFullySynchronized() {
        // TODO(playerone) Remove this lock. Also below.
        synchronized (mLock) {
            if (!isSynchronized()) {
                return false;
            }
            for (String id : mNameById.keySet()) {
                if (getFile(id) == null) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Check if a key with an ID exists within the database. This does not means the file exists,
     * only that it is a valid file that will in the future exist on disk. To determine if the file
     * actually can be read from disk, use getFile()
     * @param id Get the ID of the file.
     * @return True if the names contains the file.
     */
    public boolean fileExists(@NonNull String id) {
        synchronized (mLock) {
            return mNameById.containsKey(id);
        }
    }

    /**
     * Get the ID of a file for a given name.
     * @param name The name of the file.
     * @return The ID of the file, if it exists, else null.
     */
    public synchronized String getIdForName(@NonNull String name) {
        synchronized (mLock) {
            String id = null;
            long bestTime = -1;
            for (Map.Entry<String, String> e : mNameById.entrySet()) {
                if (e.getValue().equals(name)) {
                    if (id == null
                            || (mTimestampById.containsKey(e.getKey())
                            && mTimestampById.get(e.getKey()) > bestTime)) {
                        id = e.getKey();
                        bestTime = mTimestampById.containsKey(e.getKey())
                                ? mTimestampById.get(e.getKey()) : -1;
                    }
                }
            }
            return id;
        }
    }

    /**
     * Load a file from the system.
     * @param id The ID of the file.
     * @return A File or null if it is not on disk.
     */
    public File getFile(String id) {
        synchronized (mLock) {
            if (!mTimestampById.containsKey(id)) {
                return null;
            }
            File f = new File(getFilePath(id, mTimestampById.get(id)));
            if (!f.exists()) {
                return null;
            }
            return f;
        }
    }

    /**
     * Delete a file from disk.
     * @param fn The filename to delete.
     */
    private void deleteFile(String fn) {
        if (new File(fn).exists()) {
            if (!new File(fn).delete()) {
                Log.e(TAG, mName + ": Unable to delete temporary file: " + fn);
            }
        }
    }

    /**
     * Get the list of all files known in the database. Some of the returned files may not
     * exist on disk.
     * @return The file ID list.
     */
    @SuppressWarnings("unused")
    public List<String> listFileIds() {
        synchronized (mLock) {
            return Collections.unmodifiableList(new ArrayList<>(mNameById.keySet()));
        }
    }

    /**
     * Get the list of all files known in the database, sorted by names. Some of the returned
     * files may not exist on disk.
     * @return The file ID list.
     */
    public List<String> listFileIdsSorted() {
        synchronized (mLock) {
            ArrayList<String> ls = new ArrayList<>(mNameById.keySet());
            Collections.sort(ls, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return mNameById.get(o1).compareTo(mNameById.get(o2));
                }
            });
            return Collections.unmodifiableList(ls);
        }
    }

    /**
     * Get the name of a file given its ID.
     * @param id The file ID.
     * @return The name of the file, or null.
     */
    public String getFileName(String id) {
        synchronized (mLock) {
            if (mNameById.containsKey(id)) {
                return mNameById.get(id);
            }
            return null;
        }
    }

    /**
     * Shutdown the system.
     */
    public void shutdown() {
        synchronized (mLock) {
            mShutdown = true;
            mMonitor.shutdown();
        }
    }

    /**
     * Update to set the system user and robot.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     */
    public void update(final String userUuid, final String robotUuid) {
        synchronized (mLock) {
            if (mShutdown) {
                return;
            }
            mMonitor.update(userUuid, robotUuid);
        }
    }
}
