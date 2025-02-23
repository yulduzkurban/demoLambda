package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "processor",
		roleName = "processor-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		tracingMode = TracingMode.Active
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class Processor implements RequestHandler<Object, Map<String, Object>> {

	private static final Gson gson = new Gson();

	private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";

	private static final DynamoDB dynamo = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());

	private static final HttpClient httpClient = HttpClient.newHttpClient();

	public Map<String, Object> handleRequest(Object request, Context context) {
		LambdaLogger logger = context.getLogger();
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(new URI(WEATHER_URL))
                    .build();
			HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			logger.log(httpResponse.body());

			Map<String, Object> forecast = gson.fromJson(httpResponse.body(), new TypeToken<>(){});
			forecast.remove("current_units");
			forecast.remove("current");
			((Map<String, Object>) forecast.get("hourly")).remove("relative_humidity_2m");
			((Map<String, Object>) forecast.get("hourly")).remove("wind_speed_10m");
			((Map<String, Object>) forecast.get("hourly_units")).remove("relative_humidity_2m");
			((Map<String, Object>) forecast.get("hourly_units")).remove("wind_speed_10m");
			logger.log("hourly_units: " + forecast.get("hourly_units").toString());
			logger.log("hourly: " + forecast.get("hourly_units").toString());
			logger.log("forecast after modifications: " + forecast);
			Item item = new Item()
					.withString("id", UUID.randomUUID().toString())
					.withMap("forecast", forecast);

			Table table = dynamo.getTable(System.getenv("target_table"));
			PutItemOutcome putItemOutcome = table.putItem(item);
			logger.log("Outcome is: " + putItemOutcome.toString());

		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("statusCode", 200);
		resultMap.put("body", "Hello from Lambda");
		return resultMap;
	}
}