package org.frcforftc.networktables.sendable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefaultSendableBuilderImpl extends SendableBuilder {
    /**
     * Publishes a double property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishDoubleProperty(String key, Supplier<Double> getter, Consumer<Double> setter) {
    }

    /**
     * Publishes a double array property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishDoubleArrayProperty(String key, Supplier<Double[]> getter, Consumer<Double[]> setter) {

    }

    /**
     * Publishes a boolean property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishBooleanProperty(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {

    }

    /**
     * Publishes a boolean array property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishBooleanArrayProperty(String key, Supplier<Boolean[]> getter, Consumer<Boolean[]> setter) {

    }

    /**
     * Publishes a string property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishStringProperty(String key, Supplier<String> getter, Consumer<String> setter) {

    }

    /**
     * Publishes a string array property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishStringArrayProperty(String key, Supplier<String[]> getter, Consumer<String[]> setter) {

    }

    /**
     * Publishes a raw property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishRawProperty(String key, Supplier<Byte[]> getter, Consumer<Byte[]> setter) {

    }

    /**
     * Publishes an integer property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishIntProperty(String key, Supplier<Integer> getter, Consumer<Integer> setter) {

    }

    /**
     * Publishes a float property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishFloatProperty(String key, Supplier<Float> getter, Consumer<Float> setter) {

    }

    /**
     * Publishes an integer array property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishIntArrayProperty(String key, Supplier<Integer[]> getter, Consumer<Integer[]> setter) {

    }

    /**
     * Publishes a float array property.
     *
     * @param key    the key for the property
     * @param getter a supplier that provides the current value of the property
     * @param setter a consumer that sets the value of the property
     */
    @Override
    public void publishFloatArrayProperty(String key, Supplier<Float[]> getter, Consumer<Float[]> setter) {

    }
}
