package com.daveai.searchengine.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SearchPropertiesBindingTests {

    @Autowired
    private SearchProperties properties;

    @Test
    void bindsValuesFromApplicationYaml() {
        // Spring converts "./documents" to "documents"; both refer to the same folder.
        assertThat(properties.repositoryPath().normalize()).isEqualTo(Path.of("documents"));
        assertThat(properties.reindexInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.autocompleteLimit()).isEqualTo(10);
    }
}
