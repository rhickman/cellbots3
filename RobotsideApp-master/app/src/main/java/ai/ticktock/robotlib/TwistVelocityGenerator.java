package ai.cellbots.robotlib;

import geometry_msgs.Vector3;

/**
 * Twist velocity generator.
 */
public interface TwistVelocityGenerator {
    void fillAngular(Vector3 angular);
    void fillLinear(Vector3 linear);
}
