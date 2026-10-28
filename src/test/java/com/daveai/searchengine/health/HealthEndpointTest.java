package com.daveai.searchengine.health;

import com.daveai.searchengine.services.SearchEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /actuator/health against the whole application, with a temporary document repository. */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    private static final Path REPOSITORY = createTempDirectory();

    @DynamicPropertySource
    static void repository(DynamicPropertyRegistry registry) {
        registry.add("search.repository-path", REPOSITORY::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SearchEngine engine;

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("health-endpoint-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterEach
    void restoreRepository() throws IOException {
        Files.createDirectories(REPOSITORY);
    }

    @AfterAll
    static void deleteRepository() throws IOException {
        FileSystemUtils.deleteRecursively(REPOSITORY);
    }

    @Test
    void healthIsUpWithEveryComponent() throws Exception {
        Files.writeString(REPOSITORY.resolve("welcome.txt"), "welcome");
        engine.reindexAll();

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.documentRepository.status").value("UP"))
                .andExpect(jsonPath("$.components.documentRepository.details.writable").value(true))
                .andExpect(jsonPath("$.components.searchIndex.status").value("UP"))
                .andExpect(jsonPath("$.components.searchIndex.details.documentsStored").value(1))
                .andExpect(jsonPath("$.components.searchIndex.details.lastRefresh.indexed").value(1))
                .andExpect(jsonPath("$.components.diskSpace.status").value("UP"));
    }

    @Test
    void oneComponentCanBeCheckedOnItsOwn() throws Exception {
        mvc.perform(get("/actuator/health/documentRepository"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthIsDownWith503WhenTheRepositoryIsGone() throws Exception {
        FileSystemUtils.deleteRecursively(REPOSITORY);

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.documentRepository.status").value("DOWN"))
                .andExpect(jsonPath("$.components.documentRepository.details.error").value("folder does not exist"));
    }
}
