package ai.cellbots.pathfollower;

/**
 * Follows goals read in by the controller.
 */
@SuppressWarnings("unused")
public class GoalPathFollower extends PathFollower {

    @Override
    protected void onUpdateSetGoal() {
        PathFollowerState state = getPathFollowerState();
        if (getCurrentGoalPoint() == null) {
            clearGoal();
            return;
        }

        if (getCurrentGoalPointState() == GoalPointState.NEW) {
            setGoal(getCurrentGoalPoint().getTransform(), getCurrentGoalPoint().getAction());
            setCurrentGoalPointState(GoalPointState.RUNNING);
        } else {
            if (state == PathFollowerState.STATE_NO_GOAL) {
                setCurrentGoalPointState(GoalPointState.COMPLETED);
            } else if (state == PathFollowerState.STATE_REJECT_GOAL) {
                setCurrentGoalPointState(GoalPointState.REJECTED);
            }
        }
    }
}