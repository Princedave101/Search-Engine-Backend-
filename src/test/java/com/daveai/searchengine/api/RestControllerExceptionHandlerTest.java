package com.daveai.searchengine.api;

import com.daveai.searchengine.extraction.DocumentExtractionException;
import com.daveai.searchengine.extraction.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that each exception the facade throws becomes the right status with an
 * {@code ExceptionResponse} body. A stub controller throws the exceptions, so this
 * test doesn't depend on {@code SearchController}.
 */
class RestControllerExceptionHandlerTest {

    private static final Path SERVER_PATH = Path.of("C:/server/secret/documents/broken.pdf");

    @RestController
    static class ThrowingController {

        @GetMapping("/bad-argument")
        String badArgument() {
            throw new IllegalArgumentException("page and pageSize must be at least 1, were 0 and 10");
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new NoSuchElementException("no document with id missing.txt");
        }

        @GetMapping("/unsupported")
        String unsupported() {
            throw new UnsupportedDocumentTypeException(Path.of("image.png"));
        }

        @GetMapping("/unreadable")
        String unreadable() {
            throw new DocumentExtractionException(SERVER_PATH, new IOException("corrupt"));
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("internal detail that must not leak");
        }

        @GetMapping("/needs-int")
        String needsInt(@RequestParam int page) {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new RestControllerExceptionHandler())
            .build();

    @Test
    void illegalArgumentIs400WithItsMessage() throws Exception {
        mvc.perform(get("/bad-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page and pageSize must be at least 1, were 0 and 10"));
    }

    @Test
    void bodyHasStatusErrorPathAndTimestamp() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("no document with id missing.txt"))
                .andExpect(jsonPath("$.path").value("/not-found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unknownDocumentIs404() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no document with id missing.txt"));
    }

    @Test
    void unsupportedTypeIs415() throws Exception {
        mvc.perform(get("/unsupported"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported document type: image.png"));
    }

    @Test
    void unreadableDocumentIs422AndHidesTheServerPath() throws Exception {
        mvc.perform(get("/unreadable"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Could not read the document"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void unexpectedErrorsAre500AndHideTheDetails() throws Exception {
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    @Test
    void missingParameterStays400() throws Exception {
        mvc.perform(get("/needs-int"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void wrongParameterTypeStays400() throws Exception {
        mvc.perform(get("/needs-int").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'page'"));
    }

    @Test
    void wrongHttpMethodStays405() throws Exception {
        mvc.perform(post("/needs-int"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }
}
