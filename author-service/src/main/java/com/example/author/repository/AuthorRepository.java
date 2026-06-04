package com.example.author.repository;

import com.example.author.model.Author;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.ArrayList;
import java.util.List;

@Repository
public class AuthorRepository {
    private final DynamoDbTable<Author> productTable;

    public AuthorRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${DYNAMODB_TABLE_NAME:Authors}") String tableName) {

        // Maps the target table directly to the schema definition mapped on your model
        this.productTable = enhancedClient.table(tableName, TableSchema.fromBean(Author.class));
    }

    /**
     * Executes a scan across the target table to fetch all existing product components.
     * PageIterable manages internal network pagination automatically.
     */
    public List<Author> findAll() {
        List<Author> authors = new ArrayList<>();
        try {
            productTable.scan().items().forEach(authors::add);
        } catch (Exception e) {
            // Context propagation exception handling
            throw new RuntimeException("Failed to scan products from backend datastore", e);
        }
        return authors;
    }
}
