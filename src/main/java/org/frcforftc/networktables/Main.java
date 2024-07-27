package org.frcforftc.networktables;

import org.frcforftc.networktables.sendable.Sendable;
import org.frcforftc.networktables.sendable.SendableBuilder;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Main {
    public static void main(String[] args) {
        NetworkTablesInstance inst = NetworkTablesInstance.getDefaultInstance();
        inst.startNT4Server();
        inst.putNumber("test1", 1);
        inst.putStringArray("test2", new String[]{"t", "b", "c"});

        SendableBuilder builder = new SendableBuilder(new TestSendable()) {
            @Override
            public void publishDoubleProperty(String key, Supplier<Double> getter, Consumer<Double> setter) {

            }

            @Override
            public void publishDoubleArrayProperty(String key, Supplier<Double[]> getter, Consumer<Double[]> setter) {

            }

            @Override
            public void publishBooleanProperty(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {

            }

            @Override
            public void publishBooleanArrayProperty(String key, Supplier<Boolean[]> getter, Consumer<Boolean[]> setter) {

            }

            @Override
            public void publishStringProperty(String key, Supplier<String> getter, Consumer<String> setter) {

            }

            @Override
            public void publishStringArrayProperty(String key, Supplier<String[]> getter, Consumer<String[]> setter) {

            }

            @Override
            public void publishRawProperty(String key, Supplier<Byte[]> getter, Consumer<Byte[]> setter) {

            }

            @Override
            public void publishIntProperty(String key, Supplier<Integer> getter, Consumer<Integer> setter) {

            }

            @Override
            public void publishFloatProperty(String key, Supplier<Float> getter, Consumer<Float> setter) {

            }

            @Override
            public void publishIntArrayProperty(String key, Supplier<Integer[]> getter, Consumer<Integer[]> setter) {

            }

            @Override
            public void publishFloatArrayProperty(String key, Supplier<Float[]> getter, Consumer<Float[]> setter) {

            }
        };

        builder.post("Field", (String topic, Object value) -> inst.getServer().putTopic(topic, value));
    }

    public static class TestSendable implements Sendable {
        double[] robotPose = {1, 2, 3};

        @Override
        public void initSendable(SendableBuilder builder) {
            builder.setSmartDashboardType("Field2d");
            builder.addDoubleArrayProperty("Robot", () -> robotPose, (double[] newValue) -> {
                this.robotPose = newValue;
            });
        }
    }
}
