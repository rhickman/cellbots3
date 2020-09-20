package ai.cellbots.common;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.UUID;

import ai.cellbots.common.data.PointOfInterest;
import ai.cellbots.common.data.PointOfInterestVars;
import ai.cellbots.common.data.Transform;
import ai.cellbots.common.utils.PointOfInterestValidator;

public class PointOfInterestValidatorTest {

    private PointOfInterest poi;

    @Before
    public void setUp() {
        poi = new PointOfInterest();
        poi.name = "Unused Name";
        poi.type = "point_of_interest";
        poi.uuid = UUID.randomUUID().toString();
        poi.variables = new PointOfInterestVars();

        // Range for each transform value: -10000.0 to 10000.0 (in meters) for testing
        double min = -10000.0;
        double max = 10000.0;

        poi.variables.location = new Transform(
                randDouble(min, max),
                randDouble(min, max),
                randDouble(min, max),
                randDouble(min, max),
                randDouble(min, max),
                randDouble(min, max),
                randDouble(min, max),
                randDouble(0, Double.MAX_VALUE)
        );
        poi.variables.name = "My Real POI Name";
    }

    @After
    public void tearDown() {
        poi = null;
    }

    @Test
    public void shouldReturnTrueIfAllFieldsAreValid() {
        assertTrue();
    }

    /**
     * This name is a duplicate of PointOfInterest.variables.name and unused.
     * Should be true no matter what.
     */
    @Test
    public void shouldReturnTrueIfNameIsNull() {
        poi.name = null;
        assertTrue();
    }

    /**
     * This name is a duplicate of PointOfInterest.variables.name and unused.
     * Should be true no matter what.
     */
    @Test
    public void shouldReturnTrueIfNameIsEmpty() {
        poi.name = "";
        assertTrue();
    }

    @Test
    public void shouldReturnFalseForNullPOI() {
        poi = null;
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfTypeIsNotPointOfInterest() {
        poi.type = "some type";
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfTypeIsNull() {
        poi.type = null;
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfTypeIsEmpty() {
        poi.type = "";
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfUUIDIsNull() {
        poi.uuid = null;
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfUUIDIsEmpty() {
        poi.uuid = "";
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfUUIDIsInvalid() {
        poi.uuid = "some string but not a uuid";
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfVariablesIsNull() {
        poi.variables = null;
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfVariablesLocationIsNull() {
        poi.variables.location = null;
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfVariablesNameIsNull() {
        poi.variables.name = null;
        assertFalse();
    }

    @Test
    public void shouldReturnFalseIfVariablesNameIsEmpty() {
        poi.variables.name = "";
        assertFalse();
    }

    private void assertTrue() {
        Assert.assertEquals(true, PointOfInterestValidator.areAllFieldsValid(poi));
    }

    private void assertFalse() {
        Assert.assertEquals(false, PointOfInterestValidator.areAllFieldsValid(poi));
    }

    private double randDouble(double min, double max) {
        Random random = new Random();
        return min + (max - min) * random.nextDouble();
    }
}
