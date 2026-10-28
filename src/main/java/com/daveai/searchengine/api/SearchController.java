package com.daveai.searchengine.api;

import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.RefreshReport;
import com.daveai.searchengine.models.SearchResponse;
import com.daveai.searchengine.services.SearchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

    private final SearchEngine searchEngine;

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam("q") String query,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(name = "size", defaultValue = "10") int pageSize) {

        // the facade checks page and size, and the advice turns its IllegalArgumentException into a 400
       SearchResponse response = searchEngine.search(query, page, pageSize);

        return ResponseEntity.ok(response);
    }


    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> getSuggestions(@RequestParam("q") String query,
                                                       @RequestParam(defaultValue = "${search.autocomplete-limit}") int limit) {
        return ResponseEntity.ok(searchEngine.autocomplete(query, limit));
    }

    // ids are relative paths such as notes/welcome.txt; {*id} captures the rest of the URL, slashes included
    @GetMapping("/documents/{*id}")
    public ResponseEntity<Document> getDocument(@PathVariable String id) {
        return ResponseEntity.ok(searchEngine.getDocument(stripLeadingSlash(id)));
    }


    @GetMapping("/files/{*id}")
    public ResponseEntity<Resource> getDocumentFile(@PathVariable String id,
                                                    @RequestParam(defaultValue = "false") boolean download) {
        Path file = searchEngine.getDocumentFile(stripLeadingSlash(id));
        String name = file.getFileName().toString();
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(name, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // an uploaded HTML file is shown from this site; sandbox it so its scripts can't run here
                .header("Content-Security-Policy", "sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Document> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("the uploaded file is empty");
        }
        Document document;
        try (InputStream content = file.getInputStream()) {
            document = searchEngine.uploadFile(file.getOriginalFilename(), content);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/documents/{id}")
                .buildAndExpand(document.id())
                .toUri();
        return ResponseEntity.created(location).body(document);
    }

    @PostMapping("/index/refresh")
    public ResponseEntity<RefreshReport> reIndexing(){
        return ResponseEntity.ok(searchEngine.reindexAll());
    }

    private static String stripLeadingSlash(String id) {
        return id.startsWith("/") ? id.substring(1) : id;
    }
}
