package dev.abiral.mcp.mcp_server;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Runs the same pgvector postgres as docker-compose, so tests don't need a local database.
 * The db/init scripts run on container start, giving the same schema and seeded FAQ chunks.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        var image = DockerImageName.parse("pgvector/pgvector:pg19").asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(image)
                .withDatabaseName("mcp")
                .withCopyFileToContainer(MountableFile.forHostPath("db/init"), "/docker-entrypoint-initdb.d");
    }
}
