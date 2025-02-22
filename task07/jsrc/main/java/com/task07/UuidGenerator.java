package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.events.RuleEvents;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@LambdaHandler(
		lambdaName = "uuid_generator",
		roleName = "uuid_generator-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(targetRule = "uuid_trigger")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_bucket", value = "uuid-storage") // Replace with actual bucket name
})
@DependsOn(resourceType = ResourceType.CLOUDWATCH_RULE, name = "uuid_trigger")
@DependsOn(resourceType = ResourceType.S3_BUCKET, name = "uuid-storage") // Replace with actual bucket name
public class UuidGenerator implements RequestHandler<ScheduledEvent, String> {

	private static final String BUCKET_NAME = System.getenv("target_bucket");

	public String handleRequest(ScheduledEvent event, Context context) {
		LambdaLogger logger = context.getLogger();

		// Generate 10 random UUIDs
		Map<String, Object> uuids = new HashMap<>();
		uuids.put("ids", IntStream.range(0, 10)
				.mapToObj(i -> UUID.randomUUID().toString())
				.collect(Collectors.toList()));

		try {
			// Convert UUIDs map to JSON string
			ObjectMapper mapper = new ObjectMapper();
			String jsonString = mapper.writeValueAsString(uuids);

			// Prepare the S3 object key
			String objectKey = Instant.now().toString();

			// Upload JSON string to S3
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(System.getenv("region")).build();
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType("application/json");
			metadata.setContentLength(jsonString.length());
			PutObjectRequest putRequest = new PutObjectRequest(
					BUCKET_NAME,
					objectKey,
					new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)),
					metadata
			);
			s3Client.putObject(putRequest);

			logger.log("Successfully uploaded data to " + BUCKET_NAME + "/" + objectKey);

		} catch (Exception e) {
			logger.log("Error: " + e.getMessage());
			throw new RuntimeException(e);
		}

		return "Success";
	}
}