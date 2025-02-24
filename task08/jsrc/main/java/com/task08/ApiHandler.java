package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task08.layer.OpenMeteoClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "api_handler",
    roleName = "api_handler-role",
    layers = {"weatherClient"},
    isPublishVersion = true,
    aliasName = "${lambdas_alias_name}"
)
@LambdaLayer(
    layerName = "weatherClient",
    libraries = {"lib/OpenMeteoClient.jar", "lib/commons-lang3-3.14.0.jar"}
)
@LambdaUrlConfig(
    authType = AuthType.NONE,
    invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Object, Map<String, Object>> {

    private static final OpenMeteoClient weatherClient = new OpenMeteoClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> handleRequest(Object request, Context context) {
        try {
            Map<String, Object> event = (Map<String, Object>) request;
            String path = (String) event.get("rawPath");
            Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
            Map<String, Object> httpContext = (Map<String, Object>) requestContext.get("http");
            String method = ((String) httpContext.get("method")).toUpperCase();

            // Validate path and method
            if (!"/weather".equals(path) || !"GET".equals(method)) {
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("statusCode", 400);
                errorBody.put("message", String.format("Bad request syntax or unsupported method. Request path: %s. HTTP method: %s", path, method));

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("statusCode", 400);
                errorResponse.put("body", objectMapper.writeValueAsString(errorBody));
                return errorResponse;
            }

            // Fetch weather data
            HttpResponse<String> response = weatherClient.getCurrentWeather();

            Map<String, Object> result = new HashMap<>();
            result.put("statusCode", response.statusCode());
            result.put("body", response.body());
            return result;

        } catch (URISyntaxException | IOException | InterruptedException | RuntimeException e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("statusCode", 500);
            error.put("body", "{\"message\":\"Internal server error\"}");
            return error;
        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("statusCode", 500);
            error.put("body", "{\"message\":\"Internal server error\"}");
            return error;
        }
    }
}