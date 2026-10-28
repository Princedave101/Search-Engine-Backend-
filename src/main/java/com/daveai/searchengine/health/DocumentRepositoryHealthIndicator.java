package com.daveai.searchengine.health;

import com.daveai.searchengine.config.SearchProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Health of the document repository folder, shown as {@code documentRepository} in /actuator/health.
 * <ul>
 *   <li><b>DOWN</b> if the folder is missing, is not a folder, or can't be read: nothing can be indexed.</li>
 *   <li><b>UP</b> otherwise. {@code writable: false} means uploads will fail, but searching still works.</li>
 * </ul>
 * The path is shown as configured (e.g. {@code ./documents}), not as a full server path.
 */
@Component("documentRepository")
public class DocumentRepositoryHealthIndicator implements HealthIndicator {

    private final SearchProperties properties;

    public DocumentRepositoryHealthIndicator(SearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        Path folder = properties.repositoryPath();
        String shownPath = folder.toString().replace('\\', '/');

        if (!Files.exists(folder)) {
            return Health.down().withDetail("path", shownPath).withDetail("error", "folder does not exist").build();
        }
        if (!Files.isDirectory(folder)) {
            return Health.down().withDetail("path", shownPath).withDetail("error", "not a folder").build();
        }
        if (!Files.isReadable(folder)) {
            return Health.down().withDetail("path", shownPath).withDetail("error", "folder can't be read").build();
        }
        return Health.up()
                .withDetail("path", shownPath)
                .withDetail("writable", Files.isWritable(folder))
                .build();
    }
}
