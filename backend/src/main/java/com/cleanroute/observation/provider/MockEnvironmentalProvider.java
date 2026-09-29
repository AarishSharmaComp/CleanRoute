package com.cleanroute.observation.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic offline provider used by default, tests, and demos. */
@Component
@ConditionalOnProperty(name = "app.environmental.provider", havingValue = "mock", matchIfMissing = true)
public class MockEnvironmentalProvider extends MockAQIProvider implements EnvironmentalDataProvider {
}
