package com.chess.backend.common.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SensitiveFieldPropertyWriter extends BeanPropertyWriter {
    public SensitiveFieldPropertyWriter(BeanPropertyWriter property) {
        super(property);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider serializerProvider)
            throws Exception {
        if (isOwner(bean)) {
            super.serializeAsField(bean, generator, serializerProvider);
        } else if (!generator.canOmitFields()) {
            generator.writeOmittedField(getName());
        }
    }

    private boolean isOwner(Object bean) {
        if (!(bean instanceof OwnerScopedResponse response)) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof ChessPrincipal principal
                && principal.userId() == response.ownerId();
    }
}
