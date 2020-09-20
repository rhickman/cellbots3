package ai.cellbots.robotlib.planning;

/**
 * Define an exception to be thrown when no path is found.
 */
public class NoPathException extends Exception {
    // Constructor
    public NoPathException() {}

    // Constructor that accepts a message
    public NoPathException(String message)
    {
        super(message);
    }
}