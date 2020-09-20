package ai.cellbots.common.cloud;

import android.util.Log;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Convert objects to and from JSON.
 */
public enum JsonSerializer {
    ;
    private static final String TAG = JsonSerializer.class.getSimpleName();

    // Excludes all elements with the firebase Exclude parameter.
    private static final ExclusionStrategy EXCLUSION_STRATEGY = new ExclusionStrategy() {
        /**
         * Determines if we should skip a field.
         * @param f The field attributes.
         * @return True if the field should be skipped since it has @Exclude.
         */
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            Annotation a = f.getAnnotation(Exclude.class);
            return a != null;
        }

        /**
         * Determines if we should skip a class.
         * @param clazz The class.
         * @return Always false, we do not skip classes.
         */
        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    // Uses firebase PropertyName to transform the JSON field names.
    private static final FieldNamingStrategy FIELD_NAMING_STRATEGY = new FieldNamingStrategy() {
        /**
         * Determines how we should translate a field's name.
         * @param f The field.
         * @return The field's raw name or the content of @PropertyName if applicable.
         */
        @Override
        public String translateName(Field f) {
            Annotation a = f.getAnnotation(PropertyName.class);
            if (a != null) {
                return ((PropertyName)a).value();
            }
            return f.getName();
        }
    };

    /**
     * Convert a data object to JSON.
     * @param arg The argument object to convert.
     * @return A JSON string representation, or null in the event of an error.
     */
    public static String toJson(Object arg) {
        Gson gson = new GsonBuilder()
                .addSerializationExclusionStrategy(EXCLUSION_STRATEGY)
                .addDeserializationExclusionStrategy(EXCLUSION_STRATEGY)
                .setFieldNamingStrategy(FIELD_NAMING_STRATEGY)
                .create();
        try {
            return gson.toJson(arg);
        } catch (Exception ex) {
            Log.i(TAG, "Exception during serialization: ", ex);
            return null;
        }
    }

    /**
     * Convert a data object from JSON.
     * @param data The JSON string.
     * @param tClass The class of the object.
     * @param <T> The type template of the object.
     * @return The object, or null if it could not be deserialized.
     */
    public static <T> T fromJson(String data, Class<T> tClass) {
        Gson gson = new GsonBuilder()
                .addSerializationExclusionStrategy(EXCLUSION_STRATEGY)
                .addDeserializationExclusionStrategy(EXCLUSION_STRATEGY)
                .setFieldNamingStrategy(FIELD_NAMING_STRATEGY)
                .create();
        try {
            return gson.fromJson(data, tClass);
        } catch (Exception ex) {
            Log.i(TAG, "Exception during deserialization: ", ex);
            return null;
        }
    }
}
