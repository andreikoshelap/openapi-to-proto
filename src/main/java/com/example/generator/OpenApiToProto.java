package com.example.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;

public class OpenApiToProto {

    public static String generateProto(OpenAPI openAPI) {
        StringBuilder proto = new StringBuilder("syntax = \"proto3\";\n\npackage generated;\n\n");
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();

        if (schemas == null) {
            return proto.toString();
        }

        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            String messageName = capitalize(entry.getKey());
            proto.append("message ").append(messageName).append(" {\n");

            Schema<?> schema = entry.getValue();
            if (schema == null) {
                proto.append("}\n\n");
                continue;
            }
            Map<String, Schema> fields = schema.getProperties();
            List<String> requiredList = schema.getRequired();

            int index = 1;
            if (fields != null) {
                for (Map.Entry<String, Schema> field : fields.entrySet()) {
                    String name = field.getKey();
                    Schema<?> fieldSchema = field.getValue();
                    boolean required = requiredList != null && requiredList.contains(name);
                    String protoType = mapType(fieldSchema);

                    proto.append("  ");
                    if (!required) {
                        proto.append("optional ");
                    }
                    proto.append(protoType)
                            .append(" ")
                            .append(name)
                            .append(" = ")
                            .append(index++)
                            .append(";\n");
                }
            }
            proto.append("}\n\n");
        }

        return proto.toString();
    }

    private static String mapType(Schema<?> schema) {
        if (schema == null) {
            return "string";
        }
        // Handle $ref to other schemas
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            return capitalize(name);
        }
        String type = schema.getType();
        if (type == null) {
            return "string";
        }
        switch (type) {
            case "integer":
                return "int32";
            case "number":
                return "double";
            case "boolean":
                return "bool";
            case "string":
                return "string";
            case "array":
                Schema<?> items = schema.getItems();
                String itemType = mapType(items);
                return "repeated " + itemType;
            case "object":
                // For free-form objects without specified properties
                return "map<string, string>";
            default:
                return "string";
        }
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
