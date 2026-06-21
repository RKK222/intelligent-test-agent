package com.icbc.testagent.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppModuleBoundaryTest {

    @Test
    void appModuleDoesNotContainBusinessPackages() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/icbc/testagent/app");

        List<String> businessPackages;
        try (var paths = Files.list(sourceRoot)) {
            businessPackages = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> List.of("workspace", "session", "run", "runtime", "terminal", "web").contains(name))
                    .sorted()
                    .toList();
        }

        assertThat(businessPackages).isEmpty();
    }
}
