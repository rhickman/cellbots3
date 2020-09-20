package ai.cellbots.robot.control;

import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Cleanup an area using the vacuum.
 */
public class CleaningManager extends Controller<VacuumSpiralAction> {
    /**
     * Create the cleanup controller.
     *
     * @param session The session.
     * @param velocityMultiplexer The velocity multiplexer.
     */
    public CleaningManager(RobotSessionGlobals session, VelocityMultiplexer velocityMultiplexer) {
        super(CleaningManager.class.getSimpleName(),
                session, velocityMultiplexer, 100, VacuumSpiralAction.class);
    }

    /**
     * Get if the controller is ready for goals to be accepted, which is immediate.
     * @return True if it is ready.
     */
    @Override
    protected boolean isReady() {
        return true;
    }

    /**
     * Called on a new action.
     *
     * @param action    The current action.
     * @param newAction True if the action is a new state.
     * @return The state of the animation.
     */
    @Override
    protected Action.State onUpdate(VacuumSpiralAction action, boolean newAction) {
        // TODO implement from Controller vacuum logic, using setTeleop() to return state
        return Action.State.REJECTED;
    }

    /**
     * Called when the system is shutdown.
     */
    @Override
    protected void onShutdown() {

    }
}
