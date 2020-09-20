package ai.cellbots.common;

import android.util.Log;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeometryTest {
    private static final String TAG = GeometryTest.class.getSimpleName();

    // Curvature error tolerance
    private static final double curvatureError = 1e-6;
    // Distance error tolerance
    private static final double distanceError = 5e-3;
    // Angle error tolerance
    private static final double angleError = 5e-3;
    // Small error used for dotProduct()
    private static final double epsilon = 1e-7;

    /**
     * Wrap angle between [-PI, PI)
     */
    @Test
    public void testWrapAngle() {
        // Wrap angle with no modification necessary (= -PI)
        double angle = Geometry.wrapAngle(-Math.PI);
        assertEquals(-Math.PI, angle, 10e-10);

        // Wrap angle with no modification necessary (= +PI)
        angle = Geometry.wrapAngle(Math.PI);
        assertEquals(-Math.PI, angle, 10e-10);

        // Wrap big angle (2.5*PI) wrapped into [-PI;PI]
        angle = Geometry.wrapAngle(2.5 * Math.PI);
        assertEquals(0.5 * Math.PI, angle, 10e-10);
    }

    /**
     * Checks angle between robot pose (position and heading) and goal node position
     */
    @Test
    public void testGetAngleBetweenPoseAndLine() {
        // Angle between robot looking up (PI/2 : Y axis) in the first quadrant and goal in second
        // quadrant
        double angle = Geometry.getAngleBetweenPoseAndLine(
                new double[]{1, 1}, Math.PI / 2, new double[]{-1, 1});
        assertEquals(Math.PI / 2, angle, 0);

        // Angle between robot looking up (PI/2 : Y axis) in the first quadrant and goal in third
        // quadrant
        angle = Geometry.getAngleBetweenPoseAndLine(
                new double[]{1, 1}, Math.PI / 2, new double[]{-1, -1});
        assertEquals(3 * Math.PI / 4, angle, 0);

        // Angle between robot in the first quadrant pointing into PI/4 and goal in fourth quadrant
        angle = Geometry.getAngleBetweenPoseAndLine(
                new double[]{1, 1}, Math.PI / 4, new double[]{1, -1});
        assertEquals(-3 * Math.PI / 4, angle, 0);
    }

    /**
     * Closer point placed in line "y = x" and at minimum distance of point (3,1)
     */
    @Test
    public void testGetIntersectionBetweenLineAndPerpendicularPoint() {
        double[] newPoint = Geometry.getIntersectionBetweenLineAndPerpendicularPoint(
                new double[]{0, 0}, new double[]{4, 4}, new double[]{3, 1});
        assertArrayEquals(new double[]{2, 2}, newPoint, 0);
    }

    /**
     * Test {getPointAtDistance} method using different line positions. This method should return a
     * new point that lies in the given line at a minimum distance from the given point.
     */
    @Test
    public void testGetPointAtDistance() {
        // Obtain new point at distance 0.5 from the point (1,1) in a diagonal line "y = x"
        double[] point = Geometry.getPointAtDistance(
                new double[]{0, 0}, new double[]{2, 2}, new double[]{1, 1}, 0.5);
        assertArrayEquals(new double[]{1.35, 1.35}, point, distanceError);

        // Obtain new point at distance 0.5 from point (1,0) in an horizontal line "y = 0"
        point = Geometry.getPointAtDistance(
                new double[]{0, 0}, new double[]{2, 0}, new double[]{1, 0}, 0.5);
        assertArrayEquals(new double[]{1.5, 0}, point, distanceError);

        // Obtain new point at distance 0.5 from point (0,1) in a vertical line "x = 2"
        point = Geometry.getPointAtDistance(
                new double[]{0, 0}, new double[]{0, 2}, new double[]{0, 1}, 0.5);
        assertArrayEquals(new double[]{0, 1.5}, point, distanceError);

        // Obtain new point at distance 0.5 from the point (-1,-1) in a diagonal line "y = -x"
        point = Geometry.getPointAtDistance(
                new double[]{0, 0}, new double[]{-2, -2}, new double[]{-1, -1}, 0.5);
        assertArrayEquals(new double[]{-1.35, -1.35}, point, distanceError);
    }

    /**
     * Test DeltaX (curvature parameter) with specific parameters. It's basic algebra.
     */
    @Test
    public void testDeltaX() {
        double xGoal = 1;
        double yGoal = 3;
        double xStart = 0;
        double yStart = 0;
        double deltaAngle = -0.32;
        double deltaX = (xGoal - xStart) * Math.cos(deltaAngle) +
                (yGoal - yStart) * Math.sin(deltaAngle);
        assertEquals(0.0055, deltaX, distanceError);
    }

    /**
     * Euclidean distance between points (0,0) and (1,3)
     */
    @Test
    public void testGetDistanceBetweenTwoPoints() {
        double distance = Geometry.getDistanceBetweenTwoPoints(
                new double[]{0, 0}, new double[]{1, 3}
        );
        assertEquals(3.16, distance, distanceError);
    }

    /**
     * Angle between pose (position and heading : origin & PI/2) and target position (1,3)
     */
    @Test
    public void testGetAngleBetweenPoseAndLineFromPreviousTest() {
        double angle = Geometry.getAngleBetweenPoseAndLine(
                new double[]{0, 0}, Math.PI / 2, new double[]{1, 3}
        );
        assertEquals(-0.32, angle, angleError);
    }

    /**
     * Compute curvature between robot pointing up (PI/2) in the origin and a path going to the
     * right
     */
    @Test
    public void testComputeCurvatureLowRotationRight() {
        double KAPPA = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{1, 3}
        );
        assertEquals(0, KAPPA, curvatureError);
    }

    /**
     * Compute curvature between robot pointing up (PI/2) in the origin and a path going to the
     * left
     */
    @Test
    public void testComputeCurvatureLowRotationLeft() {
        double KAPPA = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{-1, -3}
        );
        assertEquals(0, KAPPA, curvatureError);
    }

    /**
     * Compute curvature between robot pointing up (PI/2) in the origin and a path going to the
     * right. It needs more angular velocity than {testComputeCurvatureLowRotationRight} to reach
     * the desired goal node.
     */
    @Test
    public void testComputeCurvatureMediumRotationRight() {
        double KAPPA = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{1, 0}
        );
        assertEquals(0, KAPPA, curvatureError);
    }

    /**
     * Compute curvature between robot pointing up (PI/2) in the origin and a path going to the
     * right. It needs more angular velocity than {testComputeCurvatureMediumRotationRight} to
     * reach the desired goal node.
     */
    @Test
    public void testComputeCurvatureHighRotationRight() {
        double KAPPA = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{1, -3}
        );
        assertEquals(0, KAPPA, curvatureError);
    }

    /**
     * Similar test like  {testComputeCurvatureMediumRotationRight} but the path is farthest
     *
     * Result: This solution needs a modification in order to be able to observe changes in
     * orientation
     */
    @Test
    public void testComputeCurvatureFarPointRotationRight() {
        double KAPPA = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{10, 0}
        );
        assertEquals(0, KAPPA, curvatureError);
    }

    /**
     * The goal node is behind the original position
     */
    @Test
    public void testComputeCurvaturePoseBehind() {

        // The initial position is in the origin and heading left (PI) while the goal is in the
        // +Y-axis
        double kappa = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI,
                new double[]{1, 0}
        );
        assertEquals(2, kappa, curvatureError);

        // The curvature doesn't return a sign depending on the direction of the next point
        kappa = Geometry.computeCurvature(
                new double[]{0, 0}, -Math.PI,
                new double[]{-1, 0}
        );
        assertEquals(2, kappa, curvatureError);

        // High curvature > The initial pose is with heading up (PI/2) and the goal is behind
        kappa = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{-1, -5}
        );
        assertEquals(0, kappa, curvatureError);
    }

    /**
     * Using {NearestPoint} related with Lookahead Distance
     * Separated 10 cm for example
     *
     * The result doesn't depend on the distance between points but the angle distances
     */
    @Test
    public void testComputeCurvatureNearestPoint() {
        double kappa = Geometry.computeCurvature(
                new double[]{0, 0}, Math.PI / 2,
                new double[]{0.1, 0.3}
        );
        assertEquals(0, kappa, curvatureError);
    }

    /**
     * Tests for dot product method
     */
    @Test
    public void testDotProduct() {
        try {
            double result = Geometry.dotProduct(new double[]{0, 0}, new double[]{2, 2});
            assertEquals(result, 0, epsilon);

            result = Geometry.dotProduct(new double[]{1, -1}, new double[]{3, 4});
            assertEquals(result, -1, epsilon);

            result = Geometry.dotProduct(new double[]{-3, -2}, new double[]{-4, -1});
            assertEquals(result, 14, epsilon);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }

    /**
     * Test drawLineOnGrid() when start and end point are equal.
     *
     *   |-------|--|--|--|
     *   |p0 = p1|  |  |  |     Expected output
     *   |-------|--|--|--|       --------->       p0 = p1 = (0,0)
     *   |       |  |  |  |
     *   |-------|--|--|--|
     */
    @Test
    public void drawLineOnGridStartEqualsEnd() {
        try {
            byte[] grid = new byte[] {
                    0, 0, 0, 0,
                    0, 0, 0, 0};
            grid = Geometry.drawLineOnGrid(
                    new int[]{0,0}, new int[]{0,0}, grid, 4, 2, 0, 0, (byte)127);

            byte[] expectedGrid = new byte[] {
                    127, 0, 0, 0,
                    0,   0, 0, 0};

            assertArrayEquals(expectedGrid, grid);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }

    /**
     * Test superCoverLineFromGrid() using a vertical line
     *
     *   |--|--|
     *   |  |p0|                               p0 = (1,0)
     *   |--|--|          Expected output             |
     *   |  |  |            --------->              (1,1)
     *   |--|--|                                      |
     *   |  |p1|                                    (1,2)
     *   |--|--|
     */
    @Test
    public void drawLineOnGridWithVerticalLine() {
        try {
            byte[] grid = new byte[] {
                    0, 0,
                    0, 0,
                    0, 0};
            grid = Geometry.drawLineOnGrid(
                    new int[]{1,0}, new int[]{1,2}, grid, 2, 3, 0, 0, (byte)127);

            byte[] expectedGrid = new byte[] {
                    0, 127,
                    0, 127,
                    0, 127};

            assertArrayEquals(expectedGrid, grid);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }

    /**
     * Test drawLineOnGrid() using a horizontal line
     *
     *   |--|--|--|--|
     *   |p0|  |  |p1|       Expected output
     *   |--|--|--|--|         --------->      p0 = (0,0) -- (1,0) -- (2,0) -- p1 = (3,0)
     *   |  |  |  |  |
     *   |--|--|--|--|
     */
    @Test
    public void drawLineOnGridWithHorizontalLine() {
        try {
            byte[] grid = new byte[] {
                    0, 0, 0, 0,
                    0, 0, 0, 0};
            grid = Geometry.drawLineOnGrid(
                    new int[]{0,0}, new int[]{3,0}, grid, 4, 2, 0, 0, (byte)127);

            byte[] expectedGrid = new byte[] {
                    127, 127, 127, 127,
                    0,   0,   0,   0};

            assertArrayEquals(expectedGrid, grid);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }

    /**
     * Test drawLineOnGrid() when the end point falls out of the grid.
     *
     *     |--|--|
     *  *  |  |p0|      Expected output            p0 = (1,0)
     *     |--|--|        --------->                   /
     *  *  |  |  |                                 (0,1)
     *     |--|--|
     *  p1 |  |  |
     *     |--|--|
     */
    @Test
    public void drawLineOnGridWithOffGridEndPoint() {
        try {
            byte[] grid = new byte[] {
                    0, 0,
                    0, 0,
                    0, 0};
            grid = Geometry.drawLineOnGrid(
                    new int[]{1,0}, new int[]{-1,2}, grid, 2, 3, 0, 0, (byte)127);

            byte[] expectedGrid = new byte[] {
                      0, 127,
                    127,   0,
                      0,   0};

            assertArrayEquals(expectedGrid, grid);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }

    /**
     * Test drawLineOnGrid() using a diagonal line.
     *
     *   |--|--|--|
     *   |p0|  |  |                                 p0 = (0,0)
     *   |--|--|--|         Expected output                  \
     *   |  |  |  |            --------->                    (1,1)
     *   |--|--|--|                                              \
     *   |  |  |p1|                                              (2,2)
     *   |--|--|--|
     */
    @Test
    public void drawLineOnGridWithDiagonalLine() {
        try {
            byte[] grid = new byte[]{
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 0};
            grid = Geometry.drawLineOnGrid(
                    new int[]{0, 0}, new int[]{2, 2}, grid, 3, 3, 0, 0, (byte) 127);

            byte[] expectedGrid = new byte[]{
                    127, 0, 0,
                    0, 127, 0,
                    0, 0, 127};

            assertArrayEquals(expectedGrid, grid);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }

    /**
     * Test drawLineOnGrid() on the main diagonal of a non square grid
     *
     *   |--|--|--|--|
     *   |p0|  |  |  |         Expected output      p0 = (0,0) -- (1,0)
     *   |--|--|--|--|           --------->                           \
     *   |  |  |  |p1|                                                (2,1) -- (3,1)
     *   |--|--|--|--|
     */
    @Test
    public void drawLineOnGridNonSquareMainDiagonal() {
        try {
            byte[] grid = new byte[] {
                    0, 0, 0, 0,
                    0, 0, 0, 0};
            grid = Geometry.drawLineOnGrid(
                    new int[]{0,0}, new int[]{3,1}, grid, 4, 2, 0, 0, (byte)127);

            byte[] expectedGrid = new byte[] {
                    127, 127,   0,   0,
                      0,   0, 127, 127};

            assertArrayEquals(expectedGrid, grid);
        } catch (Exception e) {
            Log.w(TAG, "Exception");
        }
    }
}
