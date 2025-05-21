package com.example.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;

public class Main {
    public static void main(String[] args) {
        OpenAPI openAPI = new OpenAPIV3Parser().read("request-in.yaml");
        String proto = OpenApiToProto.generateProto(openAPI);
        System.out.println(proto);
    }
}
