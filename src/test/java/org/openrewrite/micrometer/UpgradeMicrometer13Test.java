/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.micrometer;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeMicrometer13Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
          "/META-INF/rewrite/micrometer-13.yml",
          "org.openrewrite.micrometer.UpgradeMicrometer13");
    }

    @Test
    @DocumentExample
    void shouldChangePackage() {
        // language=java
        rewriteRun(
          java(
            """
              import io.micrometer.prometheus.PrometheusConfig;
              import io.micrometer.prometheus.PrometheusMeterRegistry;

              class MicrometerConfig {
                  PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
              }
              """,
            """
              import io.micrometer.prometheusmetrics.PrometheusConfig;
              import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

              class MicrometerConfig {
                  PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
              }
              """
          )
        );
    }

    @Test
    void shouldMigrateToPrometheusMeterRegistry() {
        // language=java
        rewriteRun(
          java(
            """
              import io.micrometer.core.instrument.Clock;
              import io.micrometer.prometheus.PrometheusConfig;
              import io.micrometer.prometheus.PrometheusMeterRegistry;
              import io.prometheus.client.CollectorRegistry;

              class MicrometerConfig {
                  PrometheusMeterRegistry prometheusMeterRegistry =
                      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM);
              }
              """,
            """
              import io.micrometer.core.instrument.Clock;
              import io.micrometer.prometheusmetrics.PrometheusConfig;
              import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
              import io.prometheus.metrics.model.registry.PrometheusRegistry;

              class MicrometerConfig {
                  PrometheusMeterRegistry prometheusMeterRegistry =
                      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM);
              }
              """
          )
        );
    }
}
