package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
//import org.apache.commons.lang3.RandomStringUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
//public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
//public class ApiHandler implements RequestHandler<Request, APIGatewayV2HTTPResponse> {
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final DynamoDbClient dynamoDb = DynamoDbClient.builder()
			.region(Region.of(System.getenv("region"))) // Change region if necessary
			.build();
	private static final ObjectMapper objectMapper = new ObjectMapper();

//	public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
//	public APIGatewayV2HTTPResponse handleRequest(Request request, Context context) {
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		try {
			context.getLogger().log("Events table: " + System.getenv("target_table"));
			context.getLogger().log("Full request: " + request.toString());
			context.getLogger().log("Request body: " + request.getBody());
//			Request inputBody = objectMapper.readValue(request.getBody(), Request.class);
//			Request inputBody = request;
			Request inputBody = objectMapper.readValue(request.getBody(), Request.class);
			Integer principalId = inputBody.getPrincipalId();
			context.getLogger().log("Request principalId: " + principalId);
			Map<String, String> content = inputBody.getContent();
			context.getLogger().log("Request content: " + content);

			String uuid = UUID.randomUUID().toString();
			String createdAt = DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.now());

			Map<String, AttributeValue> event = new HashMap<>();
			event.put("id", AttributeValue.builder().s(uuid).build());
			event.put("principalId", AttributeValue.builder().n(String.valueOf(principalId)).build());
			event.put("createdAt", AttributeValue.builder().s(createdAt).build());
			event.put("body", AttributeValue.builder().m(mapStringToAttributeValue(content)).build());

			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(System.getenv("target_table"))
					.item(event)
					.build();
			dynamoDb.putItem(putItemRequest);

			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("id", uuid);
			responseBody.put("principalId", principalId);
			responseBody.put("createdAt", createdAt);
			responseBody.put("body", content);

			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			response.setStatusCode(201);
			response.setBody(objectMapper.writeValueAsString(responseBody));
			return response;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			response.setStatusCode(500);
			return response;
		}
	}

	private static Map<String, AttributeValue> mapStringToAttributeValue(Map<String, String> map) {
		Map<String, AttributeValue> attributeValueMap = new HashMap<>();
		map.forEach((key, value) -> attributeValueMap.put(key, AttributeValue.builder().s(value).build()));
		return attributeValueMap;
	}
}