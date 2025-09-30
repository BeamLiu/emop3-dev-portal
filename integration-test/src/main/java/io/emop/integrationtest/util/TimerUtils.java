package io.emop.integrationtest.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

@Slf4j
public class TimerUtils {
    public static long measureExecutionTime(String taskName, Runnable runnable) {
        long startTime = System.nanoTime();
        runnable.run();
        long endTime = System.nanoTime();
        long duration = endTime - startTime;

        log.info("{} 执行时间: {} 毫秒", taskName, duration / 1_000_000);
        return endTime - startTime;
    }

    @SneakyThrows
    public static <R> R measureExecutionTime(String taskName, Callable<R> callable) {
        long startTime = System.nanoTime();
        R result = callable.call();
        long endTime = System.nanoTime();
        long duration = endTime - startTime;

        log.info("{} 执行时间: {} 毫秒", taskName, duration / 1_000_000);
        return result;
    }
}
