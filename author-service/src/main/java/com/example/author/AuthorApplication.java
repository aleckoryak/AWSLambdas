package com.example.author;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.author.model.Author;
import com.example.author.repository.AuthorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootApplication
public class AuthorApplication {
    private static final Logger log = LoggerFactory.getLogger(AuthorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AuthorApplication.class, args);
    }

    /**
     * Spring Cloud Function defining the GET /authors entry point.
     * Reuses Spring's managed AuthorRepository and ObjectMapper beans.
     */
    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> getAuthors(
            AuthorRepository repository,
            ObjectMapper objectMapper) {

        return request -> {
            log.info("Processing GET authors request via API Gateway Proxy.");

            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setHeaders(Map.of(
                    "Content-Type", "application/json",
                    "X-Content-Type-Options", "nosniff"
            ));

            try {
                // 1. Fetch domain elements from DynamoDB
                List<Author> authors = repository.findAll();

                // 2. Serialize list using the shared object mapper
                String jsonBody = convertToJson(authors, objectMapper);

                response.setStatusCode(200);
                response.setBody(jsonBody);

            } catch (Exception e) {
                log.error("Execution failure during request processing", e);

                // Controlled failure response for API Gateway routing
                response.setStatusCode(500);
                response.setBody("{\"message\":\"Internal Server Error\",\"code\":500}");
            }

            return response;
        };
    }

    /**
     * Converts a list of authors to a compact JSON string.
     * Reuses the injected framework-level ObjectMapper instance to maintain high performance.
     */
    private String convertToJson(List<Author> authors, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(authors);
        } catch (JsonProcessingException e) {
            // Rethrow as a runtime exception to be handled cleanly by the upstream fallback block
            throw new RuntimeException("Serialization failure translating author collection to target schema", e);
        }
    }
}
