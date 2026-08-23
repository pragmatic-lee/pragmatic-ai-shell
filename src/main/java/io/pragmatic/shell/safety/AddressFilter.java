package io.pragmatic.shell.safety;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感网络地址过滤（FR-04-02），仅针对 curl / wget。
 * 拦截元数据服务（169.254.x.x）、0.0.0.0 等。
 */
public final class AddressFilter implements SafetyFilter {

    private final boolean enabled;

    public AddressFilter(boolean enabled) {
        this.enabled = enabled;
    }

    private static final List<Pattern> SENSITIVE_ADDR = List.of(
            Pattern.compile("169\\.254(\\.\\d{1,3}){2}"),
            Pattern.compile("(^|\\s|[\"'])0\\.0\\.0\\.0(/|[\"']|\\s|$)"),
            Pattern.compile("metadata\\.(google|aws|azure|aliyun|tencent)"),
            Pattern.compile("100\\.100\\.100\\.200")   // 阿里云元数据
    );

    @Override
    public FilterVerdict evaluate(String command, CommandRisk risk) {
        if (!enabled) {
            return FilterVerdict.pass();
        }
        boolean isNetwork = command.trim().startsWith("curl") || command.trim().startsWith("wget");
        if (!isNetwork) {
            return FilterVerdict.pass();
        }
        for (Pattern p : SENSITIVE_ADDR) {
            if (p.matcher(command).find()) {
                return FilterVerdict.reject("命令指向敏感网络地址，已拒绝执行: " + command);
            }
        }
        return FilterVerdict.pass();
    }
}
