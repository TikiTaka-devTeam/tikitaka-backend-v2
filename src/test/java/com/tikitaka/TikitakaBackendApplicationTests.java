package com.tikitaka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.tikitaka.global.sms.SmsSender;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TikitakaBackendApplicationTests {
	@Autowired
	ObjectMapper objectMapper;

	@MockitoBean
	SmsSender smsSender;

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:0.8.6-pg16")
					.asCompatibleSubstituteFor("postgres"));

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void usesSnakeCaseForJsonWhileJavaUsesCamelCase() throws Exception {
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-08-21T03:00:00Z");
		JsonNamingSample sample = new JsonNamingSample(userId, createdAt);

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(sample));

		assertThat(json.get("user_id").asText()).isEqualTo(userId.toString());
		assertThat(json.get("created_at").asText()).isEqualTo(createdAt.toString());
		assertThat(json.has("userId")).isFalse();

		JsonNamingSample restored = objectMapper.readValue(
				"{\"user_id\":\"%s\",\"created_at\":\"%s\"}".formatted(userId, createdAt),
				JsonNamingSample.class);
		assertThat(restored).isEqualTo(sample);
	}

	record JsonNamingSample(UUID userId, Instant createdAt) {
	}

}
