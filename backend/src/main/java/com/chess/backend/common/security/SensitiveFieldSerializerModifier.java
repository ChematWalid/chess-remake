package com.chess.backend.common.security;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.List;

public final class SensitiveFieldSerializerModifier extends BeanSerializerModifier {
    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDescription,
                                                      List<BeanPropertyWriter> properties) {
        for (int index = 0; index < properties.size(); index++) {
            BeanPropertyWriter property = properties.get(index);
            if (property.getAnnotation(Sensitive.class) != null) {
                properties.set(index, new SensitiveFieldPropertyWriter(property));
            }
        }
        return properties;
    }
}
