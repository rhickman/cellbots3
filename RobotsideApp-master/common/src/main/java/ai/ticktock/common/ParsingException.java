package ai.cellbots.common;

/**
 * Exception thrown if parsing fails.
 */
@SuppressWarnings({"DeserializableClassInSecureContext", "SerializableClassInSecureContext", "SerializableHasSerializationMethods"})
public class ParsingException extends Exception {
    private static final long serialVersionUID = 7470676750875948080L;

    /**
     * Exception constructor.
     *
     * @param exception Error string.
     */
    ParsingException(String exception) {
        super(exception);
    }
}