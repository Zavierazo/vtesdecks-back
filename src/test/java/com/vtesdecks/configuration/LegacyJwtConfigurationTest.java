package com.vtesdecks.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.Map;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class LegacyJwtConfigurationTest {
    @Test
    void defaultsOffAndEnvironmentOptInSurvivesNewApplicationEnvironments() throws Exception {
        assertFalse(resolve(null));
        assertTrue(resolve("true"));
        assertTrue(resolve("true"));
        assertFalse(resolve("false"));
    }

    private boolean resolve(String override) throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) { properties.load(input); }
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        if (override != null) environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-environment", Map.of("JWT_REJECT_LEGACY_TOKENS", override)));
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", properties));
        return environment.getRequiredProperty("jwt.reject-legacy-tokens", Boolean.class);
    }
}
