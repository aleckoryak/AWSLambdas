package com.example.author.config;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
public class DynamoDbPrimingConfig implements Resource {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbPrimingConfig.class);
    private final DynamoDbClient dynamoDbClient;

    public DynamoDbPrimingConfig(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        // Register this component as a CRaC resource so AWS Lambda can trigger its lifecycle hooks
        Core.getGlobalContext().register(this);
        log.info("DynamoDB Priming Config registered with CRaC global context.");
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        log.info("CRaC beforeCheckpoint hook triggered. Priming AWS SDK DynamoDB Client...");

        try {
            // Force the AWS SDK to eagerly load classes, initialize HTTP clients, and warm up TLS
            ListTablesResponse response = dynamoDbClient.listTables();
            log.info("Priming successful. Tables found: {}", response.tableNames().size());
        } catch (Exception e) {
            // We catch and suppress exceptions because during the deployment/snapshot phase,
            // actual network connectivity to the live DynamoDB service might not be fully established.
            // The goal here is code execution and class loading, not necessarily a successful network response.
            log.warn("Priming call executed. Exception caught (expected if network is restricted during snapshot): {}", e.getMessage());
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        // This hook runs immediately after the snapshot is restored to serve a new invocation.
        log.info("CRaC afterRestore hook completed. Application is ready to process traffic.");
    }
}
