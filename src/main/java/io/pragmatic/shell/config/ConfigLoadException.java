package io.pragmatic.shell.config;

/**
 * 配置文件加载失败（文件缺失 / 不可读 / 解析失败 / 为空）。
 * 由启动入口捕获并输出 [配置错误] 信息后以非 0 退出码结束。
 */
public class ConfigLoadException extends RuntimeException {

    public ConfigLoadException(String message) {
        super(message);
    }
}
