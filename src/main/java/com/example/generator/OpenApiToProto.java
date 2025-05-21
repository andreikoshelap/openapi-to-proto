package com.example.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.List;
import java.util.Map;

public class OpenApiToProto {
    public static String generateProto(OpenAPI openAPI) {
        StringBuilder proto = new StringBuilder();
        // Header and imports
        proto.append("syntax = \"proto3\";\n\n");
        proto.append("package generated;\n");
        proto.append("import \"google/api/annotations.proto\";\n");
        proto.append("import \"google/protobuf/struct.proto\";\n");
        proto.append("import \"google/protobuf/empty.proto\";\n\n");

        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        if (schemas == null) schemas = Map.of();
        // 1) Generate enums and messages

        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            // top-level enums
            Schema<?> schema = entry.getValue();
            List<?> enumValues = schema.getEnum();
            if (enumValues != null && !enumValues.isEmpty()) {
                String enumName = capitalize(entry.getKey());
                proto.append("enum ").append(enumName).append(" {\n");
                for (int i = 0; i < enumValues.size(); i++) {
                    String raw = enumValues.get(i).toString();
                    String constName = normalizeEnumMember(raw);
                    proto.append("  ").append(constName)
                            .append(" = ").append(i).append(";\n");
                }
                proto.append("}\n\n");
            }
            // messages
            Map<String, Schema> fields = schema.getProperties();
            if (fields == null || fields.isEmpty()) continue;
            proto.append("message ").append(capitalize(entry.getKey())).append(" {\n");
            // inline field enums
            for (Map.Entry<String, Schema> field : fields.entrySet()) {
                List<?> fe = field.getValue().getEnum();
                if (fe != null && !fe.isEmpty()) {
                    String inlineEnum = capitalize(field.getKey()) + "Enum";
                    proto.append("  enum ").append(inlineEnum).append(" {\n");
                    for (int i = 0; i < fe.size(); i++) {
                        String raw = fe.get(i).toString();
                        String constName = normalizeEnumMember(raw);
                        proto.append("    ").append(constName)
                                .append(" = ").append(i).append(";\n");
                    }
                    proto.append("  }\n");
                }
            }
            List<String> requiredList = schema.getRequired();
            int idx = 1;
            for (Map.Entry<String, Schema> field : fields.entrySet()) {
                String name = field.getKey();
                Schema<?> fs = field.getValue();
                boolean required = requiredList != null && requiredList.contains(name);
                String type = mapType(name, fs);
                proto.append("  ");
                if (!required) proto.append("optional ");
                proto.append(type).append(" ").append(name)
                        .append(" = ").append(idx++).append(";\n");
            }
            proto.append("}\n\n");
        }


        // Generate service with HTTP annotations
        proto.append("service ApiService {\n");
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String rawPath = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                PathItem.HttpMethod httpMethod = opEntry.getKey();
                Operation operation = opEntry.getValue();
                // Determine RPC name
                String rpcName = operation.getOperationId();
                if (rpcName == null || rpcName.isBlank()) {
                    rpcName = httpMethod.name().toLowerCase() + rawPath.replaceAll("[\\{\\}/]","_");
                    rpcName = capitalize(rpcName);
                }
                // Determine request and response types
                String requestType = resolveRequestType(operation, rpcName);
                String responseType = resolveResponseType(operation, rpcName);

                // Build RPC
                proto.append("  rpc ").append(rpcName)
                        .append("(").append(requestType).append(") returns (")
                        .append(responseType).append(") {");

                // Add HTTP annotation
                proto.append("\n    option (google.api.http) = {");
                proto.append("\n      ").append(httpMethod.name().toLowerCase())
                        .append(": \"").append(rawPath).append("\"");
                // for methods with body
                if (httpMethod == PathItem.HttpMethod.POST ||
                        httpMethod == PathItem.HttpMethod.PUT ||
                        httpMethod == PathItem.HttpMethod.PATCH) {
                    proto.append("\n      body: \"*\"");
                }
                proto.append("\n    };\n  }\n");
            }
        }
        proto.append("}\n");
        return proto.toString();
    }

    private static String resolveRequestType(Operation operation, String rpcName) {
        RequestBody rb = operation.getRequestBody();
        if (rb != null && rb.getContent() != null && !rb.getContent().isEmpty()) {
            Schema<?> schema = rb.getContent().values().iterator().next().getSchema();
            if (schema.get$ref() != null) {
                return schema.get$ref().substring(schema.get$ref().lastIndexOf('/')+1);
            }
            return rpcName + "Request";
        }
        return "google.protobuf.Empty";
    }

    private static String resolveResponseType(Operation operation, String rpcName) {
        ApiResponse apiResp = operation.getResponses().entrySet().stream()
                .filter(e -> e.getKey().startsWith("2") || e.getKey().equals("default"))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (apiResp != null && apiResp.getContent() != null && !apiResp.getContent().isEmpty()) {
            Schema<?> schema = apiResp.getContent().values().iterator().next().getSchema();
            if (schema.get$ref() != null) {
                return schema.get$ref().substring(schema.get$ref().lastIndexOf('/')+1);
            }
            return rpcName + "Response";
        }
        return "google.protobuf.Empty";
    }

    // mapType, normalizeEnumMember, capitalize unchanged...
    private static String mapType(String fieldName, Schema<?> schema) {
        if (schema == null) return "string";
        if (schema.get$ref() != null) {
            return capitalize(schema.get$ref()
                    .substring(schema.get$ref().lastIndexOf('/') + 1));
        }
        List<?> ev = schema.getEnum();
        if (ev != null && !ev.isEmpty()) {
            return capitalize(fieldName) + "Enum";
        }
        String type = schema.getType();
        if (type == null) return "string";
        switch (type) {
            case "integer": return "int32";
            case "number": return "double";
            case "boolean": return "bool";
            case "string": return "string";
            case "array": return "repeated " + mapType(fieldName, schema.getItems());
            case "object": return "map<string, string>";
            default: return "string";
        }
    }

    private static String normalizeEnumMember(String raw) {
        return raw.toUpperCase().replaceAll("[^A-Z0-9]", "_");
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0,1).toUpperCase() + name.substring(1);
    }
}
