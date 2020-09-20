package ai.cellbots.common;

import java.io.File;

/**
 * Utility class for file operations.
 */

public final class FileUtil {
    /**
     * Creates a local directory. Does not do anything if the directory already exists.
     *
     * @param path Path to create.
     * @return True if the directory already exists or is created successfully.
     */
    public static boolean createLocalPath(String path) {
        File file = new File(path);
        if (file.exists()) {
            return true;
        }
        if (file.mkdirs()) {
            return true;
        }
        return false;
    }

    /**
     * Joins two paths.
     *
     * @param path1 Path to join.
     * @param path2 Path to join.
     * @return Joined path
     */
    public static String joinPath(String path1, String path2) {
        // java.nio.file.Paths has a better way of joining path. However, the package requires
        // the minimum SDK version 26 while we use 23 for making ASUS Zenfone to work properly.
        return new File(path1, path2).getPath();
    }

    /**
     * Joins three paths.
     *
     * @param path1 Path to join.
     * @param path2 Path to join.
     * @param path3 Path to join.
     * @return Joined path
     */
    public static String joinPath(String path1, String path2, String path3) {
        return joinPath(joinPath(path1, path2), path3);
    }

    /**
     * Joins four paths.
     *
     * @param path1 Path to join.
     * @param path2 Path to join.
     * @param path3 Path to join.
     * @param path4 Path to join.
     * @return Joined path
     */
    public static String joinPath(String path1, String path2, String path3, String path4) {
        return joinPath(joinPath(joinPath(path1, path2), path3), path4);
    }
}
