package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
//import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)

@DynamoDbTriggerEventSource(targetTable = "Configuration", batchSize = 1)
@DependsOn(name = "Configuration", resourceType = ResourceType.DYNAMODB_TABLE)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

	private static final String INSERT = "INSERT";
	private static final String MODIFY = "MODIFY";

	private static final DynamoDbClient dynamoDb = DynamoDbClient.builder()
			.region(Region.of(System.getenv("region"))) // Change region if necessary
			.build();

	public Void handleRequest(DynamodbEvent event, Context context) {
		for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
			if (INSERT.equals(record.getEventName()) || MODIFY.equals(record.getEventName())) {
				processRecord(record, context);
			}
		}		return null;
	}

	private void processRecord(DynamodbEvent.DynamodbStreamRecord record, Context context) {
		context.getLogger().log("Stream record is: " + record);
		var oldItem = record.getDynamodb().getOldImage();
		var newItem = record.getDynamodb().getNewImage();
		context.getLogger().log("Old item is: " + oldItem);
		context.getLogger().log("New item is: " + newItem);

		String itemKey = newItem.get("key").getS();
		Map<String, AttributeValue> auditItem;
		if (oldItem == null) {
			context.getLogger().log("Inserting");
			Map<String, AttributeValue> newItemValue = Map.of(
					"key", AttributeValue.builder().s(newItem.get("key").getS()).build(),
					"value", AttributeValue.builder().n(newItem.get("value").getN()).build()
			);
			context.getLogger().log("New item value is: " + newItemValue);
			auditItem = Map.of(
					"id", AttributeValue.builder().s(UUID.randomUUID().toString()).build(),
					"itemKey", AttributeValue.builder().s(itemKey).build(),
					"modificationTime", AttributeValue.builder().s(Instant.now().toString()).build(),
					"newValue", AttributeValue.builder().m(newItemValue).build()
			);
		} else {
			context.getLogger().log("Modifying");
			int oldValue = Integer.parseInt(oldItem.get("value").getN());
			int newValue = Integer.parseInt(newItem.get("value").getN());
			auditItem = Map.of(
					"id", AttributeValue.builder().s(UUID.randomUUID().toString()).build(),
					"itemKey", AttributeValue.builder().s(itemKey).build(),
					"modificationTime", AttributeValue.builder().s(Instant.now().toString()).build(),
					"updatedAttribute", AttributeValue.builder().s("value").build(),
					"oldValue", AttributeValue.builder().n(String.valueOf(oldValue)).build(),
					"newValue", AttributeValue.builder().n(String.valueOf(newValue)).build()
			);
		}

		context.getLogger().log("AuditItem is:" + auditItem);
		dynamoDb.putItem(PutItemRequest.builder()
				.tableName(System.getenv("target_table"))
				.item(auditItem)
				.build());
	}
}