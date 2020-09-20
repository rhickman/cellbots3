package ai.cellbots.common.data;

import java.util.UUID;

/**
 * AnimationGoal, plays animations on the robot.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal", "AssignmentToSuperclassField"})
public class AnimationGoal extends PlannerGoal {
    private static final String NAME = "animation";
    private static final String VERSION = "1.0.0";

    // Goal type name.
    private final String name;
    // Goal type parameters.
    private final AnimationParams parameters = new AnimationParams();
    // Timestamp when the goal was created. In milliseconds.
    private final long timestamp;
    // Goal version.
    private final String version;
    // Priority of the goal, default = 100.
    private final long priority;

    /**
     * Constructs AnimationGoal.
     */
    public AnimationGoal() {
        name = NAME;
        version = VERSION;
        super.uuid = UUID.randomUUID().toString();
        timestamp = System.currentTimeMillis();
        priority = 100;
    }
    /**
     * Constructs AnimationGoal.
     *
     * @param animationInfo The animation to play.
     */
    public AnimationGoal(AnimationInfo animationInfo) {
        name = NAME;
        version = VERSION;
        super.uuid = UUID.randomUUID().toString();
        parameters.animation = animationInfo.getName();
        timestamp = System.currentTimeMillis();
        priority = 100;
    }

    // Getters. Needed for serialization.
    public String getName() { return name; }
    public long getTimestamp() { return timestamp; }
    public String getVersion() {
        return version;
    }
    public long getPriority() {
        return priority;
    }
    public String getParameters() { return parameters.toString(); }
}
