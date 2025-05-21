package com.example.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI utility to convert OpenAPI (YAML/JSON) to Protobuf (.proto).
 * Usage: java -jar openapi-to-proto.jar <input-file> <output-file>
 */
public class OpenApiToProtoCli {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -jar openapi-to-proto.jar <input-openapi.yaml> <output.proto>");
            System.exit(1);
        }
        String inputPath = args[0];
        String outputPath = args[1];

        // Parse OpenAPI spec
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(inputPath, null, null);
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            System.err.println("Errors parsing OpenAPI spec:");
            result.getMessages().forEach(m -> System.err.println("  - " + m));
            System.exit(2);
        }
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            System.err.println("Failed to parse OpenAPI document: result is null");
            System.exit(3);
        }

        // Generate .proto content
        String proto = OpenApiToProto.generateProto(openAPI);

        // Write to output file
        try {
            Path outFile = Path.of(outputPath);
            Files.writeString(outFile, proto, StandardCharsets.UTF_8);
            System.out.println("Wrote proto to " + outFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write proto file: " + e.getMessage());
            System.exit(4);
        }
    }
}
