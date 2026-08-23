package io.pragmatic.shell.interaction;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基于定时重绘的 spinner 进度指示器。
 * - enabled=true 时每 ~200ms 重绘旋转字符 + 阶段性文案（独立行，不与输入行混叠）。
 * - enabled=false（非 ANSI / 关闭动画 / 管道）时退化为纯文本日志，不重绘。
 * - 阶段性文案按已等待时长切换，提示用户可取消。
 */
public final class SpinnerProgressIndicator implements ProgressIndicator {

    private static final String[] SPINNER = {"|", "/", "–", "\\"};
    private static final int TICK_MS = 200;

    private final PrintWriter out;
    private final boolean enabled;
    private final Duration timeout;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "progress-spinner");
                t.setDaemon(true);
                return t;
            });

    private volatile String stage = "正在理解意图…";
    private volatile long startedAt = 0;
    private volatile boolean running = false;

    public SpinnerProgressIndicator(PrintWriter out, boolean enabled, Duration timeout) {
        this.out = out;
        this.enabled = enabled;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        if (!enabled) {
            out.println(stage);
            out.flush();
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(this::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!running) {
            return;
        }
        long waited = System.currentTimeMillis() - startedAt;
        if (waited > timeout.toMillis() && !stage.contains("较慢")) {
            stage = "响应较慢，可按 Esc 取消";
        } else if (waited > 3000 && !stage.contains("思考") && !stage.contains("较慢")) {
            stage = "仍在思考…";
        }
        int idx = (int) (waited / TICK_MS) % SPINNER.length;
        // 独立行重绘：\r 回到行首覆盖，避免刷屏
        out.print("\r" + SPINNER[idx] + " " + stage);
        out.flush();
    }

    @Override
    public void setStage(String stage) {
        this.stage = stage;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        scheduler.shutdownNow();
        // 清除行内渲染：覆盖空格 + 换行，回到干净输入态
        out.print("\r" + " ".repeat(60) + "\r");
        out.flush();
    }
}
