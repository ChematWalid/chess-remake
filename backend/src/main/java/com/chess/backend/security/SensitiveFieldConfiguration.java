package com.chess.backend.security;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class SensitiveFieldConfiguration {
    @Bean
    Module sensitiveFieldModule() {
        return new SimpleModule().setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                              List<BeanPropertyWriter> writers) {
                for (int index = 0; index < writers.size(); index++) {
                    BeanPropertyWriter writer = writers.get(index);
                    if (writer.getAnnotation(Sensitive.class) != null) writers.set(index, new SensitiveWriter(writer));
                }
                return writers;
            }
        });
    }

    private static final class SensitiveWriter extends BeanPropertyWriter {
        private SensitiveWriter(BeanPropertyWriter base) { super(base); }
        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (isOwner(bean)) super.serializeAsField(bean, gen, prov);
            else if (!gen.canOmitFields()) gen.writeOmittedField(getName());
        }
        private static boolean isOwner(Object bean) {
            if (!(bean instanceof OwnerScopedResponse scoped)) return false;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.getPrincipal() instanceof ChessPrincipal principal
                    && principal.userId() == scoped.ownerId();
        }
    }
}
