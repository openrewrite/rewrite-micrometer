package org.openrewrite.micrometer;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UpgradeMicrometer13Test implements RewriteTest {

	@Override
	public void defaults( RecipeSpec spec ) {

		spec.recipe( Environment.builder()
		  .scanRuntimeClasspath( "org.openrewrite.micrometer" )
		  .build()
		  .activateRecipes( "org.openrewrite.micrometer.UpgradeMicrometer13" ) );
	}

	@Test
	@DocumentExample
	void shouldChangePackage() {
		// language=java
		rewriteRun(
		  java(
			"""
			package example;

			import io.micrometer.prometheus.PrometheusConfig;
			import io.micrometer.prometheus.PrometheusMeterRegistry;

			class MicrometerConfig {
				PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
			}
			""",
			"""
			package example;

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
	@DocumentExample
	void shouldMigrateToPrometheusMeterRegistry() {
		// language=java
		rewriteRun(
		  java(
			"""
			package example;

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
			package example;

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
