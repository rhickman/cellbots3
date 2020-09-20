package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;

/**
 * Stores the metadata for a robot.
 */
@IgnoreExtraProperties
public class RobotMetadata implements DataFactory.RobotUuidData, DataFactory.UserUuidData {
    @Exclude
    private final String mUserUuid;
    @Exclude
    private final String mRobotUuid;
    @PropertyName("name")
    private final String mRobotName;
    @PropertyName("smoother_params")
    private final SmootherParams mSmootherParams;
    @PropertyName("tango_device_height")
    private final String mTangoDeviceHeight;

    public static final DataFactory<RobotMetadata> FACTORY = new DataFactory<>(
            new DataFactory.Listener<RobotMetadata>() {
                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The RobotMetadata object.
                 */
                @Override
                public RobotMetadata create(String userUuid, String robotUuid,
                        DataSnapshot dataSnapshot) {
                    return fromFirebase(userUuid, robotUuid, dataSnapshot);
                }

                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param string The JSON string.
                 * @return The RobotMetadata object.
                 */
                @Override
                public RobotMetadata create(String userUuid, String robotUuid, String string) {
                    RobotMetadata r = JsonSerializer.fromJson(string, RobotMetadata.class);
                    return r == null ? null : new RobotMetadata(userUuid, robotUuid, r);
                }
            }, RobotMetadata.class, true, true);

    public RobotMetadata() {
        mUserUuid = null;
        mRobotName = null;
        mRobotUuid = null;
        mSmootherParams = null;
        mTangoDeviceHeight = null;
    }

    public RobotMetadata(String userUuid, String robotUuid, RobotMetadata copy) {
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mRobotName = copy.getRobotName();
        mSmootherParams = copy.getSmootherParams();
        mTangoDeviceHeight = copy.getTangoDeviceHeight();
    }

    public RobotMetadata(String userUuid, String robotUuid, String robotName,
            SmootherParams smootherParams, String tangoDeviceHeight) {
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mRobotName = robotName;
        mSmootherParams = smootherParams;
        mTangoDeviceHeight = tangoDeviceHeight;
    }

    @Exclude
    public String getUserUuid() {
        return mUserUuid;
    }

    @Exclude
    public String getRobotUuid() {
        return mRobotUuid;
    }

    @PropertyName("name")
    public String getRobotName() {
        return mRobotName;
    }

    @PropertyName("smoother_params")
    public SmootherParams getSmootherParams() {
        return mSmootherParams;
    }

    @PropertyName("tango_device_height")
    public String getTangoDeviceHeight() {
        return mTangoDeviceHeight;
    }

    /**
     * Return the object from firebase.
     *
     * @param userUuid     The user uuid.
     * @param robotUuid    The robot uuid.
     * @param dataSnapshot The firebase DataSnapshot.
     * @return The metadata object or null.
     */
    @SuppressWarnings("unchecked")
    public static RobotMetadata fromFirebase(String userUuid,
            String robotUuid, DataSnapshot dataSnapshot) {
        return new RobotMetadata(userUuid, robotUuid, dataSnapshot.getValue(RobotMetadata.class));
    }

    @Override
    public String toString() {
        return "Metadata(" + mUserUuid + ", " + mRobotUuid + ", " + mRobotName + ", " +
                mSmootherParams + ", " + mTangoDeviceHeight + ", " + ")";
    }
}
