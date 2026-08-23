package io.pragmatic.shell.interaction;

/**
 * 语义模式等待态指示器。用于大模型调用期间的进度反馈（spinner + 阶段性文案）。
 * 所有退出路径（成功/异常/超时/取消）都必须调用 {@link #stop()} 清理终端渲染。
 */
public interface ProgressIndicator {

    /** 启动指示器（若 disabled 则退化为纯文本日志，不重绘）。 */
    void start();

    /** 切换当前阶段文案（如「正在理解意图…」）。 */
    void setStage(String stage);

    /** 停止并清理渲染状态，回到干净输入行。 */
    void stop();
}
