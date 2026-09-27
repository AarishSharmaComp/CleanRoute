package com.cleanroute.observation;

import com.cleanroute.observation.config.ObservationProperties;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.service.ObservationProviderCallExecutor;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationProviderCallExecutorTest {
    @Test void hangingProviderTimesOutAndDoesNotHoldTheCallingThread() {
        ObservationProperties properties = new ObservationProperties();
        properties.setProviderTimeoutMs(100);
        ObservationProviderCallExecutor executor = new ObservationProviderCallExecutor(properties);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        long start = System.nanoTime();
        try {
            assertThatThrownBy(() -> executor.call("test-provider", () -> {
                while (releaseWorker.getCount() != 0) {
                    try { releaseWorker.await(20, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException ignored) { /* simulate an adapter that ignores cancellation */ }
                }
                return "done";
            })).isInstanceOf(ProviderFailureException.class)
                    .extracting(e -> ((ProviderFailureException) e).getType())
                    .isEqualTo(ProviderFailureException.Type.TIMEOUT);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(1_000);
        } finally {
            releaseWorker.countDown();
            executor.close();
        }
    }
}
