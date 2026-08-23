package io.pragmatic.shell.interaction;

import io.pragmatic.shell.nlu.NluResult;
import io.pragmatic.shell.nlu.NluService;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 将一次 NLU 语义理解调用封装为可取消、可调超时的异步任务。
 * - 底层 {@link NluService#understand(String)} 在独立线程执行。
 * - {@link #await()} 内部按 timeout 阻塞获取；超时抛出 {@link TimeoutException} 并取消底层调用。
 * - {@link #cancel()} 中断任务线程（进而中断 LangChain4j 底层 HTTP，若其支持）。
 */
public final class CancelableNluCall {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nlu-call");
                t.setDaemon(true);
                return t;
            });

    private final Future<NluResult> future;
    private final long timeoutSeconds;

    public CancelableNluCall(NluService nlu, String input) {
        this(nlu, input, 30);
    }

    public CancelableNluCall(NluService nlu, String input, long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.future = executor.submit(() -> nlu.understand(input));
    }

    /** 阻塞等待结果，受 timeoutSeconds 约束。 */
    public NluResult await() throws TimeoutException, CancellationException {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new CancellationException("等待被中断");
        } catch (CancellationException e) {
            throw e;
        } catch (ExecutionException e) {
            // 将底层异常包装为 RuntimeException，由调用方区分超时/降级文案
            throw new RuntimeException(e.getCause() == null ? e : e.getCause());
        }
    }

    /** 取消本次请求（中断底层线程）。 */
    public void cancel() {
        future.cancel(true);
    }

    public boolean isDone() {
        return future.isDone();
    }

    /** 释放线程池。 */
    public void shutdown() {
        executor.shutdownNow();
    }
}
