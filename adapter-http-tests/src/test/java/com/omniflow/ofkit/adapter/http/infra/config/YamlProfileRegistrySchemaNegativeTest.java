package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistrySchemaNegativeTest {
    @Test
    void fails_when_profile_missing_rules() throws Exception {
        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schemaIs = Thread.currentThread().getContextClassLoader().getResourceAsStream("profiles-schema.json")) {
            assertNotNull(schemaIs, "schema resource not found");
            JsonSchema schema = schemaFactory.getSchema(schemaIs);
            ObjectMapper om = new ObjectMapper();
            String invalid = "{\n" +
                    "  \"ofkit\": {\n" +
                    "    \"http\": {\n" +
                    "      \"profiles\": {\n" +
                    "        \"bad\": {\n" +
                    "          \"generic_problem\": {\"type\":\"about:blank\",\"title\":\"t\",\"status\":502}\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";
            var node = om.readTree(invalid);
            Set<ValidationMessage> errors = schema.validate(node);
            assertFalse(errors.isEmpty(), "expected schema validation errors");
        }
    }
}

