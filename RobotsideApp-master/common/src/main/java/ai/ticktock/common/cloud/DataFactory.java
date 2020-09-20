package ai.cellbots.common.cloud;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseException;

import ai.cellbots.common.Strings;

/**
 * Factory to create elements from the cloud.
 * @param <T> The type of objects for the factory.
 */
final public class DataFactory<T> {
    private final Listener<T> mListener;
    private final boolean mHaveUser;
    private final boolean mHaveRobot;

    /**
     * A listener for the factory. Called to create objects.
     * @param <T> The type of objects to be created.
     */
    public interface Listener<T> {
        /**
         * Create an object of the type T.
         * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
         * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
         * @param dataSnapshot The DataSnapshot to be converted.
         * @return An object of the type, or null if it could not be created. May also throw
         *         DatabaseException from firebase.
         */
        T create(String userUuid, String robotUuid, DataSnapshot dataSnapshot);
        /**
         * Create an object of the type T.
         * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
         * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
         * @param string The JSON data string.
         * @return An object of the type, or null if it could not be created.
         */
        T create(String userUuid, String robotUuid, String string);
    }

    /**
     * Objects that support this interface require robot data to be specified.
     */
    public interface RobotUuidData {
        /**
         * Gets the robot uuid.
         * @return The robot uuid.
         */
        String getRobotUuid();
    }

    /**
     * Objects that support this interface require the user data to be specified.
     */
    public interface UserUuidData {
        /**
         * Gets the user uuid.
         * @return The user uuid.
         */
        String getUserUuid();
    }

    /**
     * Create a data factory.
     * @param listener The listener for this class.
     * @param tClass The class.
     * @param haveUser If true, then the user must be specified to create an object.
     * @param haveRobot If true, then the robot must be specified to create an object.
     */
    public DataFactory(Listener<T> listener, Class<T> tClass, boolean haveUser, boolean haveRobot) {
        mListener = listener;
        mHaveUser = haveUser;
        mHaveRobot = haveRobot;
        if (!UserUuidData.class.isAssignableFrom(tClass) && haveUser) {
            throw new Error("Class supports user, but not UserUuidData interface");
        }
        if (!RobotUuidData.class.isAssignableFrom(tClass) && haveRobot) {
            throw new Error("Class supports robot, but not RobotUuidData interface");
        }
    }

    /**
     * Get if we require the user uuid.
     * @return True if we require the user.
     */
    public boolean isUserFactory() {
        return mHaveUser;
    }

    /**
     * Get if we require the robot uuid.
     * @return True if we require the robot.
     */
    public boolean isRobotFactory() {
        return mHaveRobot;
    }

    /**
     * Gets if an object is correct for the current user and robot.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param object The object to verify.
     * @return True if the object is current.
     */
    public boolean isCurrent(String userUuid, String robotUuid, @NonNull T object) {
        if (mHaveUser) {
            UserUuidData userGetter = (UserUuidData)object;
            if (!Strings.compare(userGetter.getUserUuid(), userUuid)) {
                return false;
            }
        }
        if (mHaveRobot) {
            RobotUuidData robotGetter = (RobotUuidData)object;
            if (!Strings.compare(robotGetter.getRobotUuid(), robotUuid)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create an object using the factory class.
     * @param userUuid The uuid of the user. Could be null.
     * @param robotUuid The uuid of the robot. Could be null.
     * @param dataSnapshot The DataSnapshot.
     * @return The object created, or null if it could not be created. May also throw
     *         DatabaseException from firebase.
     */
    public T create(String userUuid, String robotUuid, DataSnapshot dataSnapshot) {
        if (userUuid == null && !mHaveUser) {
            throw new Error("User was not set.");
        }
        if (robotUuid == null && !mHaveRobot) {
            throw new Error("Robot was not set.");
        }
        return mListener.create(userUuid, robotUuid, dataSnapshot);
    }

    /**
     * Create an object using the factory class, logging error.s
     * @param parent The parent context for logging.
     * @param userUuid The uuid of the user. Could be null.
     * @param robotUuid The uuid of the robot. Could be null.
     * @param dataSnapshot The DataSnapshot.
     * @return The object created, or null if it could not be created.
     */
    public T createLogged(Context parent, String userUuid, String robotUuid, DataSnapshot dataSnapshot) {
        try {
            return create(userUuid, robotUuid, dataSnapshot);
        } catch (DatabaseException ex) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot, ex.toString());
            return null;
        }
    }

    /**
     * Create an object using the factory class.
     * @param userUuid The uuid of the user. Could be null.
     * @param robotUuid The uuid of the robot. Could be null.
     * @param string The JSON data.
     * @return The object created, or null if it could not be created. May also throw
     *         DatabaseException from firebase.
     */
    public T create(String userUuid, String robotUuid, String string) {
        if (userUuid == null && !mHaveUser) {
            throw new Error("User was not set.");
        }
        if (robotUuid == null && !mHaveRobot) {
            throw new Error("Robot was not set.");
        }
        return mListener.create(userUuid, robotUuid, string);
    }
}
