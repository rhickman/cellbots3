package ai.cellbots.common.data;

import org.parceler.Parcel;

/**
 * Data type for the position of the robot from the Firebase database.
 */
@Parcel
public class Transform {
    public double px;
    public double py;
    public double pz;
    public double qw;
    public double qx;
    public double qy;
    public double qz;
    public double ts;

    public Transform() {
    }

    /**
     * Class constructor. Receives position in (x, y, z) and rotations as a quaternion (qw, qx, qy,
     * qz), plus a timestamp.
     */
    public Transform(double px, double py, double pz, double qw, double qx, double qy, double qz,
            double ts) {
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.qw = qw;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;
        this.ts = ts;
    }

    /**
     * Class constructor
     * @param t, transform object from RobotApp
     */
    public Transform (ai.cellbots.common.Transform t){
        px = t.getPosition(0);
        py = t.getPosition(1);
        pz = t.getPosition(2);
        qw = t.getRotation(0);
        qx = t.getRotation(1);
        qy = t.getRotation(2);
        qz = t.getRotation(3);
        ts = t.getTimestamp();
    }
}
