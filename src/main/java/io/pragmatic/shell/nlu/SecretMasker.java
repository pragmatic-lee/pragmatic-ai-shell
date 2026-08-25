package io.pragmatic.shell.nlu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息打码（FR-CTX-06-01）：疑似密钥（sk- 前缀、token=/password= 等键值对）
 * 在写入会话上下文前打码，避免敏感值被回传 LLM 与 /context 展示。
 * 已确认策略：仅打码密钥模式，不扩大到内网 IP/用户名等。
 */
public final class SecretMasker {

    /** sk- 前缀密钥 或 key=value 形式的凭据键值对。 */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(sk-[A-Za-z0-9_-]{6,}|\\b(token|password|passwd|secret|apikey|api_key|access_key)"
                    + "\\b\\s*=\\s*[^\\s,;\"']+)");

    private SecretMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher m = SECRET_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = m.group();
            String replacement = value.toLowerCase().startsWith("sk-")
                    ? "sk-****"
                    : value.substring(0, value.indexOf('=') + 1) + "****";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
