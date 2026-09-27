package com.cleanroute.observation.service;

import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.config.ObservationProperties;
import org.springframework.stereotype.Component;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounds provider wait time and worker growth; timed-out calls are interrupted and removed from the queue. */
@Component
public class ObservationProviderCallExecutor implements AutoCloseable {
    private final ConcurrentMap<String, ThreadPoolExecutor> providerExecutors = new ConcurrentHashMap<>();
    private final ObservationProperties properties;

    public ObservationProviderCallExecutor(ObservationProperties properties) { this.properties = properties; }

    public <T> T call(String providerId, Callable<T> providerCall) {
        ThreadPoolExecutor executor = providerExecutors.computeIfAbsent(providerId, this::newProviderExecutor);
        final Future<T> future;
        try { future = executor.submit(providerCall); }
        catch (RuntimeException rejected) { throw ProviderFailureException.temporary(); }
        try {
            return future.get(properties.getProviderTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            cancel(executor, future);
            throw ProviderFailureException.timeout();
        } catch (InterruptedException interrupted) {
            cancel(executor, future);
            Thread.currentThread().interrupt();
            throw ProviderFailureException.temporary();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof ProviderFailureException providerFailure) throw providerFailure;
            throw ProviderFailureException.temporary();
        }
    }

    private void cancel(ThreadPoolExecutor executor, Future<?> future) {
        if (future instanceof Runnable runnable) executor.remove(runnable);
        future.cancel(true);
    }

    private ThreadPoolExecutor newProviderExecutor(String providerId) {
        AtomicInteger threadId = new AtomicInteger();
        String safeName = providerId.replaceAll("[^A-Za-z0-9_-]", "_");
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(3), task -> {
                    Thread thread = new Thread(task, "observation-provider-" + safeName + "-" + threadId.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public void close() { providerExecutors.values().forEach(ThreadPoolExecutor::shutdownNow); }
}
