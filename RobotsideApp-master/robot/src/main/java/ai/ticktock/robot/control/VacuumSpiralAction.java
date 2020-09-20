package ai.cellbots.robot.control;

/**
 * Vacuum a spiral pattern.
 */
public class VacuumSpiralAction extends Action {
    private final long mTime;

    /**
     * Vacuum a spiral pattern.
     * @param completeBy Unix millisecond timestamp to complete the action by. Negative means infinite time.
     * @param time The time to vacuum for, in milliseconds.
     */
    public VacuumSpiralAction(long completeBy, long time) {
        super(completeBy);
        mTime = time;
        if (time < 0) {
            throw new IllegalArgumentException("Vacuum time must be positive");
        }
    }

    /**
     * Get the time.
     * @return The time to vacuum for, in milliseconds.
     */
    @SuppressWarnings("unused")
    public long getTime() {
        return mTime;
    }
}