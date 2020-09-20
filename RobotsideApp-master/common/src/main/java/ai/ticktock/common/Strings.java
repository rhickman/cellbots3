package ai.cellbots.common;

/**
 * Utilities class.
 */
public enum Strings {
    ;

    /**
     * Compares two strings, accepting null values.
     * @param s1 The first string.
     * @param s2 The second string.
     * @return True if the strings are both null, or if they are the same.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean compare(String s1, String s2) {
        if (s1 == null) {
            return s2 == null;
        }
        return s2 != null && s1.equals(s2);
    }

    /**
     * Convert a byte (8-bit value) to a hex string.
     *
     * @param hexByte The byte to be converted to hex.
     */
    public static String byteToHexString(byte hexByte) {
        int val = hexByte;
        val &= 0xFF;
        String r = Integer.toHexString(val).toUpperCase();
        if (r.length() == 1) {
            return "0" + r;
        }
        return r.isEmpty() ? "00" : r;
    }

    /**
     * Convert a set of bytes to a hex string.
     * @param bytes The bytes to convert.
     */
    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(byteToHexString(b));
        }
        return sb.toString();
    }
}
