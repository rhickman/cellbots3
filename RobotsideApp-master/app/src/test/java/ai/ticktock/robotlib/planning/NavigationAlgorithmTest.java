package ai.cellbots.robotlib.planning;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;

import ai.cellbots.common.Transform;

/**
 * Unit test for findPath() method in NavigationAlgorithm class. It is designed to find a path
 * between two points on a grid as a set of transforms and compare it to the expected output.
 */
public class NavigationAlgorithmTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    /**
     * Look for a path between origin at the center of the grid and goal in the upper left corner.
     * Path should go through the main diagonal of the matrix.
     */
    @Test
    public void testFindPathMainDiagonal() throws NoPathException {
        NavigationAlgorithm navigator;

        // First make a navigation Grid
        int NAV_GRID_MAX_ROW = 5;
        int NAV_GRID_MAX_COL = 5;
        int[][] mNavigationGrid = new int[NAV_GRID_MAX_ROW][NAV_GRID_MAX_COL];

        // Settings
        Transform origin = new Transform(2, 2, 0, 0);
        // Place Goal on upper left corner of the map.
        Transform goal = new Transform(0, 0, 0, 0);

        // Expected path for the current settings
        List<Transform> expected_path = new ArrayList<>();
        expected_path.add(new Transform(1.0, 1.0, 0, 0));
        expected_path.add(new Transform(0.0, 0.0, 0, 0));

        // New navigation algorithm
        navigator = new NavigationAlgorithm(mNavigationGrid);
        assertThat(navigator.findPath(origin, goal), is(expected_path));
    }

    /**
     * Look for a path between origin at the center of the grid and goal in the upper right corner.
     * Path should go through the minor diagonal of the matrix.
     */
    @Test
    public void testFindPathMinorDiagonal() throws NoPathException {
        NavigationAlgorithm navigator;

        // First make a navigation Grid
        int NAV_GRID_MAX_ROW = 5;
        int NAV_GRID_MAX_COL = 5;
        int[][] mNavigationGrid = new int[NAV_GRID_MAX_ROW][NAV_GRID_MAX_COL];

        // Settings
        Transform origin = new Transform(NAV_GRID_MAX_ROW / 2, NAV_GRID_MAX_COL / 2, 0, 0);
        // Place Goal on upper right corner of the map.
        Transform goal = new Transform(0, NAV_GRID_MAX_COL - 1, 0, 0);

        // Expected path for the current settings
        List<Transform> expected_path = new ArrayList<>();
        expected_path.add(new Transform(1.0, 3.0, 0, 0));
        expected_path.add(new Transform(0.0, 4.0, 0, 0));

        // New navigation algorithm
        navigator = new NavigationAlgorithm(mNavigationGrid);
        assertThat(navigator.findPath(origin, goal), is(expected_path));
    }

    /**
     * Look for a path between origin at the center of the grid and goal on the grid's border, right
     * above origin. It is a column matrix. Path should go in a straight line.
     */
    @Test
    public void testFindPathStraight() throws NoPathException {
        NavigationAlgorithm navigator;

        // First make a navigation Grid
        int NAV_GRID_MAX_ROW = 5;
        int NAV_GRID_MAX_COL = 1;
        int[][] mNavigationGrid = new int[NAV_GRID_MAX_ROW][NAV_GRID_MAX_COL];

        // Settings
        Transform origin = new Transform(NAV_GRID_MAX_ROW / 2, NAV_GRID_MAX_COL / 2, 0, 0);
        // Place Goal right above origin.
        Transform goal = new Transform(0, NAV_GRID_MAX_COL - 1, 0, 0);

        // Expected path for the current settings
        List<Transform> expected_path = new ArrayList<>();
        expected_path.add(new Transform(1.0, 0.0, 0, 0));
        expected_path.add(new Transform(0.0, 0.0, 0, 0));

        // New navigation algorithm
        navigator = new NavigationAlgorithm(mNavigationGrid);
        assertThat(navigator.findPath(origin, goal), is(expected_path));
    }

    /**
     * Look for a path between origin and goal, when origin is out of map. This should throw an
     * error.
     */
    @Test
    public void testFindPathOriginOutOfMapError() throws NoPathException {
        NavigationAlgorithm navigator;

        // First make a navigation Grid
        int NAV_GRID_MAX_ROW = 5;
        int NAV_GRID_MAX_COL = 5;
        int[][] mNavigationGrid = new int[NAV_GRID_MAX_ROW][NAV_GRID_MAX_COL];

        // Settings
        // Place origin out of map
        Transform origin = new Transform(NAV_GRID_MAX_ROW, 0, 0, 0);
        Transform goal = new Transform(0, 0, 0, 0);

        exception.expect(Error.class);
        exception.expectMessage("Origin is out of map");

        // New navigation algorithm
        navigator = new NavigationAlgorithm(mNavigationGrid);
        navigator.findPath(origin, goal);
    }

    /**
     * Look for a path between origin and goal, when goal is out of map. This should throw an error.
     */
    @Test
    public void testFindPathGoalOutOfMapError() throws NoPathException {
        NavigationAlgorithm navigator;

        // First make a navigation Grid
        int NAV_GRID_MAX_ROW = 5;
        int NAV_GRID_MAX_COL = 5;
        int[][] mNavigationGrid = new int[NAV_GRID_MAX_ROW][NAV_GRID_MAX_COL];

        // Settings
        Transform origin = new Transform(NAV_GRID_MAX_ROW - 1, 0, 0, 0);
        // Place Goal out of map
        Transform goal = new Transform(8, 0, 0, 0);

        exception.expect(Error.class);
        exception.expectMessage("Goal is out of map");

        // New navigation algorithm
        navigator = new NavigationAlgorithm(mNavigationGrid);
        navigator.findPath(origin, goal);
    }

    /**
     * Look for a path between origin and goal, when goal ist out of the area that the robot can
     * reach by making its allowed movements. This should throw a NoPathException.
     */
    @Test
    public void testFindNoPathException() throws NoPathException {
        NavigationAlgorithm navigator;

        // First make a navigation Grid
        int NAV_GRID_MAX_ROW = 5;
        int NAV_GRID_MAX_COL = 5;
        int[][] mNavigationGrid = new int[NAV_GRID_MAX_ROW][NAV_GRID_MAX_COL];

        exception.expect(NoPathException.class);
        exception.expectMessage("Goal is out of reachable area");

        // Settings
        Transform origin = new Transform(0, 0, 0, 0);
        // Place Goal Right up from origin
        Transform goal = new Transform(NAV_GRID_MAX_ROW - 1, 0, 0, 0);

        // New navigation algorithm
        navigator = new NavigationAlgorithm(mNavigationGrid);
        navigator.findPath(origin, goal);
    }
}