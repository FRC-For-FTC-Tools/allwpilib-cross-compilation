package org.frcforftc.networktables.sendable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.frcforftc.networktables.NetworkTablesValue;

public class SendableBuilder {
    ObjectNode m_data;
    ObjectMapper mapper = new ObjectMapper();

    public ObjectNode getData() {
        return m_data;
    }

    public void addProperty(String name, NetworkTablesValue value) {

        if (value.get() instanceof Sendable) {
            //TODO
        }

        ArrayNode array = mapper.createArrayNode();

        switch (value.getType()) {
            case Boolean:
                m_data.put(name, value.<Boolean>getAs());
                break;
            case Double:
                m_data.put(name, value.<Double>getAs());
                break;
            case Int:
                m_data.put(name, value.<Integer>getAs());
                break;
            case Float:
                m_data.put(name, value.<Float>getAs());
                break;
            case Raw:
                m_data.put(name, value.<Byte>getAs());
                break;
            case String:
                m_data.put(name, value.<String>getAs());
                break;
            case BooleanArray:
                for (var val : value.<boolean[]>getAs()) {
                    array.add(val);
                }
                m_data.set(name, array);
                break;
            case DoubleArray:
                for (var val : value.<double[]>getAs()) {
                    array.add(val);
                }
                m_data.set(name, array);
                break;
            case IntArray:
                for (var val : value.<int[]>getAs()) {
                    array.add(val);
                }
                m_data.set(name, array);
                break;
            case FloatArray:
                for (var val : value.<float[]>getAs()) {
                    array.add(val);
                }
                m_data.set(name, array);
                break;
            case StringArray:
                for (var val : value.<String[]>getAs()) {
                    array.add(val);
                }
                m_data.set(name, array);
                break;
            case Unknown:
                throw new RuntimeException("Invalid type specified");
        }

    }
}
