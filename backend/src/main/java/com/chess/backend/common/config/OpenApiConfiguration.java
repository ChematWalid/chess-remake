package com.chess.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String APP_RESPONSE_SCHEMA = "AppResponse";
    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String REFRESH_COOKIE_SCHEME = "refreshCookie";
    private static final String INTERNAL_TOKEN_SCHEME = "internalToken";

    @Bean
    OpenAPI chessOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(REFRESH_COOKIE_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("chess_refresh"))
                .addSecuritySchemes(INTERNAL_TOKEN_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Internal-Token"));
        return new OpenAPI()
                .info(new Info().title("Chess Backend API").version("1.0.0")
                        .description("REST contracts for identity, profiles, social features, and archived chess games. "
                                + "Live moves use the C++ WebSocket server; presence and challenges use STOMP."))
                .components(components);
    }

    @Bean
    OpenApiCustomizer responseAndSecurityCustomizer() {
        return openApi -> {
            if (openApi.getComponents() != null) {
                openApi.getComponents().addSchemas(APP_RESPONSE_SCHEMA, appResponseSchema(null));
            }
            for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
                for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : pathEntry.getValue().readOperationsMap().entrySet()) {
                    PathItem.HttpMethod method = operationEntry.getKey();
                    Operation operation = operationEntry.getValue();
                    setOperationSecurity(pathEntry.getKey(), operation);
                    wrapJsonResponse(operation);
                    correctResponseStatus(pathEntry.getKey(), method, operation);
                }
            }
        };
    }

    private static void setOperationSecurity(String path, Operation operation) {
        if (path.startsWith("/api/internal/")) {
            operation.addSecurityItem(new SecurityRequirement().addList(INTERNAL_TOKEN_SCHEME));
            return;
        }
        if (path.equals("/api/auth/refresh") || path.equals("/api/auth/logout")) {
            operation.addSecurityItem(new SecurityRequirement().addList(REFRESH_COOKIE_SCHEME));
            return;
        }
        if (isPublicPath(path)) {
            return;
        }
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private static boolean isPublicPath(String path) {
        return path.equals("/api/auth/register") || path.equals("/api/auth/login")
                || path.equals("/api/auth/verify-email") || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password") || path.equals("/api/auth/oauth/exchange")
                || path.equals("/api/users/{id}/avatar");
    }

    private static void wrapJsonResponse(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((status, response) -> {
            if ("204".equals(status) || response.getContent() == null) {
                return;
            }
            MediaType json = response.getContent().get(JSON_MEDIA_TYPE);
            if (json == null && response.getContent().containsKey("*/*")) {
                json = response.getContent().remove("*/*");
                response.getContent().addMediaType(JSON_MEDIA_TYPE, json);
            }
            if (json == null || json.getSchema() == null || isBinary(json.getSchema()) || isAppResponse(json.getSchema())) {
                return;
            }
            json.setSchema(appResponseSchema(json.getSchema()));
        });
        addErrorResponse(operation, "400", "Request validation failed");
        addErrorResponse(operation, "401", "Authentication failed");
        addErrorResponse(operation, "403", "The authenticated user is not allowed to perform this action");
        addErrorResponse(operation, "404", "The requested resource does not exist");
        addErrorResponse(operation, "409", "The request conflicts with current resource state");
        addErrorResponse(operation, "500", "Unexpected server error");
    }

    private static boolean isBinary(Schema<?> schema) {
        return "string".equals(schema.getType()) && "byte".equals(schema.getFormat());
    }

    private static void correctResponseStatus(String path, PathItem.HttpMethod method, Operation operation) {
        String responseCode = responseCode(path, method);
        if (responseCode == null) {
            if (path.equals("/api/internal/games/record") && method == PathItem.HttpMethod.POST) {
                operation.getResponses().addApiResponse("201", new ApiResponse().description("Game saved"));
            }
            return;
        }
        ApiResponse response = operation.getResponses().remove("200");
        if (response == null) {
            response = new ApiResponse();
        }
        if ("204".equals(responseCode)) {
            response.setContent(null);
        }
        if (response.getDescription() == null || "OK".equals(response.getDescription())) {
            response.setDescription("204".equals(responseCode) ? "Request completed" : "Request accepted");
        }
        operation.getResponses().addApiResponse(responseCode, response);
    }

    private static String responseCode(String path, PathItem.HttpMethod method) {
        if (method == PathItem.HttpMethod.POST && (path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password") || path.equals("/api/friends/request/{username}"))) {
            return "202";
        }
        if ((method == PathItem.HttpMethod.POST && (path.equals("/api/auth/logout")
                || path.equals("/api/auth/reset-password")
                || path.equals("/api/friends/accept/{requestId}") || path.equals("/api/friends/decline/{requestId}")
                || path.equals("/api/friends/block/{userId}") || path.equals("/api/friends/invite/redeem")
                || path.equals("/api/users/me/avatar")))
                || (method == PathItem.HttpMethod.DELETE
                && (path.equals("/api/friends/{friendId}") || path.equals("/api/friends/block/{userId}")))) {
            return "204";
        }
        return null;
    }

    private static void addErrorResponse(Operation operation, String status, String description) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        Content content = new Content().addMediaType(JSON_MEDIA_TYPE,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + APP_RESPONSE_SCHEMA)));
        operation.getResponses().addApiResponse(status,
                new ApiResponse().description(description).content(content));
    }

    private static boolean isAppResponse(Schema<?> schema) {
        return schema.get$ref() != null && schema.get$ref().endsWith("/" + APP_RESPONSE_SCHEMA);
    }

    private static Schema<?> appResponseSchema(Schema<?> dataSchema) {
        Schema<?> schema = new ObjectSchema()
                .addProperty("success", new BooleanSchema())
                .addProperty("code", new IntegerSchema())
                .addProperty("errorCode", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("data", dataSchema == null ? new Schema<>() : dataSchema)
                .addProperty("errors", new MapSchema().additionalProperties(new StringSchema()))
                .addProperty("traceId", new StringSchema());
        schema.setRequired(List.of("success"));
        return schema;
    }
}
