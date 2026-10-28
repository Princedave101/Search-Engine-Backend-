package com.daveai.searchengine.api;

import com.daveai.searchengine.services.SearchEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for every T10 endpoint, run against the real application: the whole Spring
 * context, real components, and a temporary folder as the document repository.
 * <p>
 * The context is shared by all tests, so {@link #emptyRepository()} clears the folder and the
 * index before each one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    private static final Path REPOSITORY = createTempDirectory();

    @DynamicPropertySource
    static void repository(DynamicPropertyRegistry registry) {
        registry.add("search.repository-path", REPOSITORY::toString);
        registry.add("search.autocomplete-limit", () -> 3);
        registry.add("search.cors-allowed-origins", () -> "https://www.university.edu");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SearchEngine engine;

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("search-controller-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteRepository() throws IOException {
        FileSystemUtils.deleteRecursively(REPOSITORY);
    }

    @BeforeEach
    void emptyRepository() throws IOException {
        try (Stream<Path> children = Files.list(REPOSITORY)) {
            for (Path child : children.toList()) {
                FileSystemUtils.deleteRecursively(child);
            }
        }
        engine.reindexAll(); // the folder is empty, so this drops every document
    }

    /** Writes a text file into the repository, creating folders as needed. */
    private static Path writeFile(String id, String content) throws IOException {
        Path file = REPOSITORY.resolve(id);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private void givenIndexedFiles() throws IOException {
        writeFile("java.txt", "java spring framework");
        writeFile("ml/intro.txt", "machine learning with java");
        writeFile("boot.txt", "spring boot starters");
        engine.reindexAll();
    }

    // ---- GET /api/v1/search ----

    @Test
    void searchReturnsRankedResults() throws Exception {
        givenIndexedFiles();

        mvc.perform(get("/api/v1/search").param("q", "machine"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("machine"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.tookMillis").isNumber())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].documentID").value("ml/intro.txt"))
                .andExpect(jsonPath("$.results[0].title").value("intro"))
                .andExpect(jsonPath("$.results[0].snippet").value(containsString("machine")))
                .andExpect(jsonPath("$.results[0].rankingScore").isNumber());
    }

    @Test
    void searchPagesDefaultToPage1Size10() throws Exception {
        for (int i = 1; i <= 12; i++) {
            writeFile("doc-" + i + ".txt", "java");
        }
        engine.reindexAll();

        mvc.perform(get("/api/v1/search").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(12))
                .andExpect(jsonPath("$.results", hasSize(10)));
        mvc.perform(get("/api/v1/search").param("q", "java").param("page", "2"))
                .andExpect(jsonPath("$.results", hasSize(2)));
        mvc.perform(get("/api/v1/search").param("q", "java").param("page", "3").param("size", "5"))
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    void searchWithNoMatchesIsAnEmptyList() throws Exception {
        givenIndexedFiles();

        mvc.perform(get("/api/v1/search").param("q", "kotlin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void searchWithoutQIs400() throws Exception {
        mvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/search"));
    }

    @Test
    void searchWithABadPageOrSizeIs400() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "java").param("page", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", "java").param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", "java").param("size", String.valueOf(SearchEngine.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at most")));
        mvc.perform(get("/api/v1/search").param("q", "java").param("page", "30000000").param("size", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", "java").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'page'"));
    }

    // ---- GET /api/v1/autocomplete ----

    @Test
    void autocompleteReturnsSuggestions() throws Exception {
        givenIndexedFiles();

        mvc.perform(get("/api/v1/autocomplete").param("q", "s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("spring"))
                .andExpect(jsonPath("$[1]").value("starters"));
        mvc.perform(get("/api/v1/autocomplete").param("q", "s").param("limit", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void autocompleteLimitDefaultsToAndIsCappedByTheConfiguredLimit() throws Exception {
        writeFile("words.txt", "aa ab ac ad ae");
        engine.reindexAll();

        // search.autocomplete-limit is 3 in this test
        mvc.perform(get("/api/v1/autocomplete").param("q", "a"))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/v1/autocomplete").param("q", "a").param("limit", "50"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void autocompleteWithABadLimitOrNoQIs400() throws Exception {
        mvc.perform(get("/api/v1/autocomplete").param("q", "s").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/autocomplete"))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /api/v1/documents/{id} ----

    @Test
    void getDocumentReturnsTitleAndText() throws Exception {
        givenIndexedFiles();

        // the id has a slash in it
        mvc.perform(get("/api/v1/documents/ml/intro.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ml/intro.txt"))
                .andExpect(jsonPath("$.title").value("intro"))
                .andExpect(jsonPath("$.rawText").value(containsString("machine learning with java")));
    }

    @Test
    void getUnknownDocumentIs404() throws Exception {
        mvc.perform(get("/api/v1/documents/missing.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("no document with id missing.txt"))
                .andExpect(jsonPath("$.path").value("/api/v1/documents/missing.txt"));
    }

    // ---- GET /api/v1/files/{id} ----

    @Test
    void getFileReturnsTheOriginalFileInline() throws Exception {
        givenIndexedFiles();

        mvc.perform(get("/api/v1/files/ml/intro.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("machine learning with java"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("intro.txt")))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void getFileWithDownloadIsAnAttachment() throws Exception {
        givenIndexedFiles();

        mvc.perform(get("/api/v1/files/java.txt").param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")));
    }

    @Test
    void getFileOfAnUnknownDocumentIs404() throws Exception {
        // the file exists but was never indexed, so it is not served
        writeFile("secret.txt", "not indexed");

        mvc.perform(get("/api/v1/files/secret.txt"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/../pom.xml"))
                .andExpect(status().isNotFound());
    }

    // ---- POST /api/v1/documents ----

    @Test
    void uploadSavesIndexesAndReturns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lecture.txt", "text/plain",
                "distributed systems lecture".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/v1/documents/lecture.txt"))
                .andExpect(jsonPath("$.id").value("lecture.txt"))
                .andExpect(jsonPath("$.title").value("lecture"));

        assertThat(REPOSITORY.resolve("lecture.txt")).hasContent("distributed systems lecture");
        mvc.perform(get("/api/v1/search").param("q", "distributed"))
                .andExpect(jsonPath("$.results[0].documentID").value("lecture.txt"));
    }

    @Test
    void uploadOfAnUnsupportedTypeIs415() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        assertThat(REPOSITORY.resolve("photo.png")).doesNotExist();
    }

    @Test
    void uploadOfAnUnreadableFileIs422() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "broken.docx", "application/octet-stream",
                "not a word document".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Could not read the document"));
        assertThat(REPOSITORY.resolve("broken.docx")).doesNotExist();
    }

    @Test
    void uploadWithoutAFileOrWithAnEmptyFileIs400() throws Exception {
        mvc.perform(multipart("/api/v1/documents"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/documents").file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("the uploaded file is empty"));
    }

    // ---- POST /api/v1/index/refresh ----

    @Test
    void refreshIndexesNewFilesAndDropsDeletedOnes() throws Exception {
        givenIndexedFiles();
        writeFile("new.txt", "brand new document");
        Files.delete(REPOSITORY.resolve("boot.txt"));

        mvc.perform(post("/api/v1/index/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(3))
                .andExpect(jsonPath("$.removed").value(1))
                .andExpect(jsonPath("$.failed", hasSize(0)));

        mvc.perform(get("/api/v1/search").param("q", "brand"))
                .andExpect(jsonPath("$.totalHits").value(1));
        mvc.perform(get("/api/v1/search").param("q", "boot"))
                .andExpect(jsonPath("$.totalHits").value(0));
    }

    @Test
    void refreshWithGetIs405() throws Exception {
        mvc.perform(get("/api/v1/index/refresh"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // ---- other ----

    @Test
    void homeListsTheEndpoints() throws Exception {
        for (String path : new String[]{"/api/v1", "/api/v1/"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("SearchEngine"))
                    .andExpect(jsonPath("$.endpoints.search").value(startsWith("GET /api/v1/search")));
        }
    }

    @Test
    void unknownUrlIs404Json() throws Exception {
        mvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void corsAllowsTheConfiguredSite() throws Exception {
        mvc.perform(options("/api/v1/search")
                        .header(HttpHeaders.ORIGIN, "https://www.university.edu")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.university.edu"));
    }

    @Test
    void corsRejectsOtherSites() throws Exception {
        mvc.perform(options("/api/v1/search")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
