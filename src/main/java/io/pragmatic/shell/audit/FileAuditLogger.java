package io.pragmatic.shell.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 将审计条目以单行 JSON 追加写入 audit.log。
 */
public final class FileAuditLogger implements AuditLogger {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path auditPath;
    private final boolean enabled;

    public FileAuditLogger(String auditPath, boolean enabled) {
        this.auditPath = Path.of(auditPath);
        this.enabled = enabled;
    }

    @Override
    public void log(AuditEntry entry) {
        if (!enabled) {
            return;
        }
        try {
            Path parent = auditPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = mapper.writeValueAsString(entry);
            Files.writeString(auditPath, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 审计写入失败不应阻断主流程
        }
    }
}
