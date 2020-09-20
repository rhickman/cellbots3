package ai.cellbots.robot.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;

/**
 * Mediates the flow of action from the Executive Planner
 */
public class ActionMediator implements ThreadedShutdown {
    private final List<Controller> mControllerList;

    /**
     * Creates the mediator with a list of controllers.
     *
     * @param controllers The controllers list.
     */
    public ActionMediator(Collection<Controller> controllers) {
        mControllerList = Collections.unmodifiableList(new ArrayList<>(controllers));
        // TODO: ensure that no controller's getActionClass() is a subclass of another.
        // TODO: ensure that no controller has the raw Action class as getActionClass()
    }

    /**
     * Gets if the controllers are ready for actions.
     *
     * @return True if the actions are ready.
     */
    public synchronized boolean isReady() {
        for (Controller controller : mControllerList) {
            if (!controller.isReady()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the action of all controllers to stop.
     *
     * @return True if the system is stopped.
     */
    @SuppressWarnings("unused")
    public synchronized boolean stop() {
        boolean isStopped = true;
        for (Controller controller : mControllerList) {
            //noinspection unchecked
            controller.setAction(null);
            isStopped &= controller.isStopped();
        }
        return isStopped;
    }

    /**
     * Sets the action of the system.
     *
     * @param next The next action.
     */
    public synchronized void setAction(Action next) {
        for (Controller controller : mControllerList) {
            if (controller.getActionClass().isInstance(next)) {
                //noinspection unchecked
                controller.setAction(next);
            } else {
                //noinspection unchecked
                controller.setAction(null);
            }
        }
    }

    /**
     * Sets the fully inflated CostMap.
     *
     * @param fullyInflatedCostMap The fully inflated CostMap.
     */
    public void setFullyInflatedCostMap(CostMap fullyInflatedCostMap) {
        for (Controller controller : mControllerList) {
            controller.setFullyInflatedCostMap(fullyInflatedCostMap);
        }
    }

    /**
     * Sets the proportionally inflated CostMap.
     *
     * @param proportionallyInflatedCostMap The proportionally inflated CostMap.
     */
    public void setProportionallyInflatedCostMap(CostMap proportionallyInflatedCostMap) {
        for (Controller controller : mControllerList) {
            controller.setProportionallyInflatedCostMap(proportionallyInflatedCostMap);
        }
    }

    /**
     * Sets the transform.
     *
     * @param transform The transform.
     */
    public void setTransform(Transform transform) {
        for (Controller controller : mControllerList) {
            controller.setTransform(transform);
        }
    }

    /**
     * Gets the state of an action.
     *
     * @param query The action to check the state of.
     * @return The state of the action, or null if invalid.
     */
    public synchronized Action.State getActionState(Action query) {
        for (Controller controller : mControllerList) {
            if (controller.getActionClass().isInstance(query)) {
                //noinspection unchecked
                return controller.getActionState(query);
            }
        }
        return null;
    }

    /**
     * Shuts down the mediator.
     */
    @Override
    public void shutdown() {
        for (Controller controller : mControllerList) {
            controller.shutdown();
        }
    }

    /**
     * Shuts down the mediator and wait for it to stop.
     */
    @Override
    public void waitShutdown() {
        TimedLoop.efficientShutdown(new ArrayList<ThreadedShutdown>(mControllerList));
    }
}
