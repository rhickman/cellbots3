package ai.cellbots.pathfollower;

import org.junit.Assert;
import org.junit.Test;

import ai.cellbots.common.Transform;

public class PurePursuitTest {

    private static final double curvatureLowError = 1e-6;
    private static final double curvatureMediumError = 5e-3;

    /**
     * The target node is located "n" positions above in the y direction
     */
    @Test
    public void testShouldMove() {
        PurePursuit pp = new PurePursuit(0.5 /* Lookahead distance */);

        // The method should request for a new node because the current node is closer than the
        // lookahead distance
        boolean askForNextNode = pp.shouldMove(
                new Transform(0, 0, 0, 0),
                new double[]{0, 0.4}
        );
        Assert.assertTrue(askForNextNode);

        // Send one node that is farthest than the lookahead distance, so the
        // robot must not move (return false)
        askForNextNode = pp.shouldMove(
                new Transform(0, 0, 0, 0),
                new double[]{0, 1}
        );
        Assert.assertFalse(askForNextNode);
    }

    /**
     * Case 1: The robot is aligned with the next 2 nodes.
     * The robot must move forwards only and not rotate.
     */
    @Test
    public void testGoToNodeNoRotation() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, Math.PI / 2),
                new double[]{0, 1},
                new double[]{0, 2}
        );
        Assert.assertEquals(0,  /* Expected value */
                KAPPA,          /* Actual value */
                curvatureLowError  /* Accepted tolerance between those values */
        );
    }

    /**
     * Case 2: Two diagonal points
     */
    @Test
    public void testGoToNodeLowRotationRight() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, Math.PI / 2),
                new double[]{1, 3},
                new double[]{2, 4}
        );
        Assert.assertEquals(0, KAPPA, curvatureLowError);
    }

    /**
     * Case 3: Rotation to the left > Far target node
     */
    @Test
    public void testGoToNodeFarRotationLeft() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, Math.PI / 2),
                new double[]{10, 0},
                new double[]{0, 10}
        );
        Assert.assertEquals(0, KAPPA, curvatureLowError);
    }

    /**
     * Case 4: Rotation to the left > Far target node > Oriented towards it
     * Solution: ROTATION TO THE LEFT > NEGATIVE SIGN
     */
    @Test
    public void testGoToNodeFarRotationLeftOriented() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, 0),
                new double[]{10, 0},
                new double[]{0, 10}
        );
        Assert.assertEquals(-0.28, KAPPA, curvatureMediumError);
    }

    /**
     * Case 5: Rotation to the right > Far target node > Oriented towards it
     * Solution: ROTATION TO THE RIGHT > POSITIVE SIGN
     */
    @Test
    public void testGoToNodeFarRotationRightOriented() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, Math.PI),
                new double[]{-10, 0},
                new double[]{0, 10}
        );
        Assert.assertEquals(0.28, KAPPA, curvatureMediumError);
    }

    /**
     * Case 6: 90 degrees of rotation
     */
    @Test
    public void testGoToNode90DegreesAligned() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, Math.PI / 2),
                new double[]{3, 0},
                new double[]{6, 0}
        );
        Assert.assertEquals(0, KAPPA, curvatureLowError);
    }

    /**
     * Case 7: 90 degrees of rotation > Far target node
     */
    @Test
    public void testGoToNode90DegreesFarTargetNode() {
        PurePursuit pp = new PurePursuit(0.5);
        double KAPPA = pp.computeCurvatureToReach(
                new Transform(0, 0, 0, Math.PI / 2),
                new double[]{3, 3},
                new double[]{6, 3}
        );
        Assert.assertEquals(0, KAPPA, curvatureLowError);
    }

}