package ai.cellbots.common;

import org.apache.commons.math3.util.MathUtils;

/**
 * Class that implements geometry methods mostly used by the Pure Pursuit class
 */
public final class Geometry {
    private static final String TAG = Geometry.class.getSimpleName();
    private static final double VERY_SHORT_NUMBER = 1e-2;
    private static final double diffTolerance = 1e-6;

    /**
     * Giving an initial position and heading, calculates the necessary curvature to reach the goal
     * point
     *
     * @param beginPosition Initial coordinates
     * @param beginAngle    Initial heading
     * @param goalPosition  Final position
     * @return Curvature parameter related with the inverse of the radius
     */
    public static double computeCurvature(
            double[] beginPosition, double beginAngle, double[] goalPosition) {

        double xGoal = goalPosition[0];
        double yGoal = goalPosition[1];
        double xStart = beginPosition[0];
        double yStart = beginPosition[1];

        // Compute distance distance from robot to goal
        double squaredDistToGoal = getSquaredDistanceBetweenTwoPoints(beginPosition, goalPosition);
        // Compute angle between robot and goal
        double deltaAngle = getAngleBetweenPoseAndLine(beginPosition, beginAngle, goalPosition);
        // X-axis distance between {beginPosition} and the nearest point
        double deltaX = (xGoal - xStart) * Math.cos(deltaAngle) +
                (yGoal - yStart) * Math.sin(deltaAngle);
        // Return curvature
        return ((-2 * deltaX) / (squaredDistToGoal +
                // If distToGoal is less than {VERY_SHORT_NUMBER}, the numerator will be constant
                // (the squared term is insignificant)
                (squaredDistToGoal < VERY_SHORT_NUMBER ? VERY_SHORT_NUMBER : 0)));
    }

    /**
     * Given a point and a line, computes a new point that lies on the shortest distance
     * (perpendicular) between both inputs
     *
     * @param initialLinePoint   Initial point of the line
     * @param finalLinePoint     Final point of the line
     * @param perpendicularPoint Perpendicular point
     * @return New point coordinates
     */
    public static double[] getIntersectionBetweenLineAndPerpendicularPoint(
            double[] initialLinePoint, double[] finalLinePoint, double[] perpendicularPoint) {
        // Math behind this implementation: https://stackoverflow.com/a/15187473

        // If both points on the line are in the same position, that point will be the solution
        if (Math.abs(initialLinePoint[0] - finalLinePoint[0]) < diffTolerance &&
                Math.abs(initialLinePoint[1] - finalLinePoint[1]) < diffTolerance) {
            return initialLinePoint;
        }

        double Px = finalLinePoint[0] - initialLinePoint[0];
        double Py = finalLinePoint[1] - initialLinePoint[1];
        // The only way to get den == 0 is with Px == Py, which is addressed on the previous check
        double den = Px * Px + Py * Py;
        double u = ((perpendicularPoint[0] - initialLinePoint[0]) * Px +
                (perpendicularPoint[1] - initialLinePoint[1]) * Py) / den;
        return new double[]{initialLinePoint[0] + u * Px, initialLinePoint[1] + u * Py};
    }

    /**
     * Given a line, and a point {pointInLine} that is part of it, calculates a new point at
     * certain
     * {distance} of {pointInLine} in the direction of the final point of the line
     * ({finalLinePoint}).
     *
     * @param initialLinePoint Initial point of the line
     * @param finalLinePoint   Final point of the line
     * @param pointInLine      Point that is part of the line
     * @param distance         Distance between {pointInLine} and the return point
     * @return Point separated at {distance} from {pointInLine}
     */
    public static double[] getPointAtDistance(
            double[] initialLinePoint, double[] finalLinePoint, double[] pointInLine,
            double distance) {
        // Math behind this implementation: https://stackoverflow.com/a/17725275
        // TODO: Verify that {pointInLine} is part of the line between points {initialLinePoint}
        // and {finalLinePoint}.
        // TODO: Check that {initialLinePoint} != {finalLinePoint}
        double Phi = Math.atan2(finalLinePoint[1] - initialLinePoint[1],
                finalLinePoint[0] - initialLinePoint[0]);

        if (Phi == Math.PI / 2 || Phi == -Math.PI / 2)
        // Vertical line
        {
            return new double[]{
                    // If Phi == 0 : Point is above
                    // Else Phi == PI : Point is below
                    pointInLine[0], pointInLine[1] + (Phi == Math.PI / 2 ? 1 : -1) * distance
            };
        }
        // Returns default calculation
        return new double[]{
                pointInLine[0] + distance * Math.cos(Phi),
                pointInLine[1] + distance * Math.sin(Phi)
        };
    }

    /**
     * Computes the necessary orientation of {initialPoint2D} between {finalPoint2D} and
     * {initialAngle}
     *
     * @param initialPoint2D Initial position [x,y]
     * @param initialAngle   Initial angle
     * @param finalPoint2D   Where the object should go
     */
    public static double getAngleBetweenPoseAndLine(
            double[] initialPoint2D, double initialAngle, double[] finalPoint2D) {

        // Obtain the goal point coordinates in {initialPoint2D} reference
        double dx = finalPoint2D[0] - initialPoint2D[0];
        double dy = finalPoint2D[1] - initialPoint2D[1];
        // Angle of the target
        double targetAngle = Math.atan2(dy, dx);
        // Angle difference between the robot ({initialAngle}) and {finalPoint2D}
        return wrapAngle(targetAngle - initialAngle);
    }

    /**
     * Wrap an angle so it is always between -PI and +PI.
     *
     * @param angle The angle to wrap
     * @return The angle wrapped around.
     */
    public static double wrapAngle(double angle) {
        return MathUtils.normalizeAngle(angle, 0.0);
    }

    /**
     * Returns the Euclidean distance between two points {p1} and {p2}
     *
     * @param p1 First point
     * @param p2 Second point
     * @return Distance between {p1} and {p2}
     */
    public static double getDistanceBetweenTwoPoints(double[] p1, double[] p2) {
        double dX = p2[0] - p1[0];
        double dY = p2[1] - p1[1];
        return Math.sqrt(dX * dX + dY * dY);
    }

    public static double getSquaredDistanceBetweenTwoPoints(double[] p1, double[] p2) {
        double dX = p2[0] - p1[0];
        double dY = p2[1] - p1[1];
        return dX * dX + dY * dY;
    }

    /**
     * Calculates the dot product between a vector and a vector
     *
     * @param v1 The first vector
     * @param v2 The second vector
     * @return The dot product value
     */
    public static double dotProduct(double v1[], double v2[]) throws Exception {
        if (v1 == null || v2 == null || (v1.length != v2.length)) {
            throw new Exception();
        }
        double r = 0.0;
        for (int i = 0; (i < v1.length) && (i < v2.length); i++) {
            r += v1[i] * v2[i];
        }
        return r;
    }

    /**
     * Connect two points in a grid through a line, catching all the grid cells that it passes
     * through.
     *
     * Reference: https://www.redblobgames.com/grids/line-drawing.html#org1da485d
     *
     * @param p0          The (x,y) coordinates for the start cell.
     * @param p1          The (x,y) coordinates for the end cell.
     * @param grid        The occupancy grid in 1D.
     * @param width       The width of the grid.
     * @param height      The height of the grid.
     * @param lowerXLimit X offset of the grid.
     * @param lowerYLimit Y offset of the grid.
     * @param cost        New cost to set on the grid.
     * @return Grid with modified costs.
     */
    @SuppressWarnings("AssignmentToForLoopParameter")
    public static byte[] drawLineOnGrid(
            int[] p0, int[] p1, byte[] grid, int width, int height, int lowerXLimit,
            int lowerYLimit, byte cost) throws Exception {
        // Sanity checks
        if (grid.length == 0) {
            throw new Exception("drawLineOnGrid: null grid");
        }
        if (width <= 0) {
            throw new Exception(
                    "drawLineOnGrid: grid width should be greater than 0");
        }
        if (height <= 0) {
            throw new Exception(
                    "drawLineOnGrid: grid height should be greater than 0");
        }
        if (cost < 0) {
            throw new Exception("drawLineOnGrid: cost should be non-negative");
        }
        // Get the distance between start and end coordinates.
        int dx = p1[0] - p0[0];
        int dy = p1[1] - p0[1];

        // Get the maximum number of cells to traverse in both axes.
        int nx = Math.abs(dx);
        int ny = Math.abs(dy);
        // Get the direction of movement in both axes.
        int signX = dx > 0 ? 1 : -1;
        int signY = dy > 0 ? 1 : -1;

        // Begin line with start point.
        int px = p0[0];
        int py = p0[1];

        // Only mark the start point as occupied if it is within grid's boundaries
        if (py - lowerYLimit >= 0 && py - lowerYLimit < height &&
                px - lowerXLimit >= 0 && px - lowerXLimit < width) {
            grid[(p0[1] - lowerYLimit) * width + p0[0] - lowerXLimit] = cost;
        }

        for (int ix = 0, iy = 0; ix < nx || iy < ny; ) {
            if (nx != 0 && ny != 0 && (0.5 + ix) / nx == (0.5 + iy) / ny) {
                // next step is diagonal
                px += signX;
                py += signY;
                ix++;
                iy++;
            } else if (ny == 0 || (nx != 0 && (0.5 + ix) / nx < (0.5 + iy) / ny)) {
                // next step is horizontal
                px += signX;
                ix++;
            } else {
                // next step is vertical
                py += signY;
                iy++;
            }

            int pointIndex = (py - lowerYLimit) * width + px - lowerXLimit;
            if (py - lowerYLimit < 0 || py - lowerYLimit >= height ||
                    px - lowerXLimit < 0 || px - lowerXLimit >= width) {
                continue;
            }
            grid[pointIndex] = cost;
        }
        return grid;
    }
}
