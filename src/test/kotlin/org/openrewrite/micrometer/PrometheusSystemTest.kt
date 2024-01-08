package org.openrewrite.micrometer

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test

class PrometheusSystemTest {
  class Metrics(registry: CollectorRegistry) {
    val counterWithoutLabels = Counter
      .build("counterWithoutLabels", "This is a Counter(No Labels) description")
      .register(registry)

    val counterWithLabels = Counter
      .build("counterWithLabels", "This is a Counter(Labels) description")
      .labelNames("counterLabel1", "counterLabel2")
      .register(registry)

    val histogramWithLabels = Histogram
      .build("histogramWithLabels", "This is a Histogram description")
      .labelNames("histogramLabel1", "histogramLabel2")
      .buckets(1.0, 2.0)
      .register(registry)

    val summaryWithLabels = Summary
      .build("summaryWithLabels", "This is a Summary description")
      .labelNames("summaryLabel1", "summaryLabel2")
      .quantile(0.5, 0.05)
      .quantile(0.75, 0.02)
      .quantile(0.95, 0.01)
      .quantile(0.99, 0.001)
      .quantile(0.999, 0.0001)
      .maxAgeSeconds(3)

      .register(registry)

    val gaugeWithoutLabels = Gauge
      .build("gaugeWithoutLabels", "This is a Gauge(No Labels) description")
      .register(registry)

    val gaugeWithLabels = Gauge
      .build("gaugeWithLabels", "This is a Gauge(Labels) description")
      .labelNames("gaugeLabel1", "gaugeLabel2")
      .register(registry)
  }

  @Test
  fun counterSimpleTest() {
    val metrics = Metrics(CollectorRegistry())
    metrics.counterWithoutLabels.inc()
    metrics.counterWithoutLabels.inc(5.0)
    assertThat(metrics.counterWithoutLabels.get()).isEqualTo(6.0)
  }

  @Test fun clearACounter() {
    val metrics = Metrics(CollectorRegistry())
    metrics.counterWithoutLabels.inc()
    metrics.counterWithoutLabels.clear()
    metrics.counterWithoutLabels.inc(5.0)
    assertThat(metrics.counterWithoutLabels.get()).isEqualTo(5.0)
  }

  @Test
  fun counterWithLabels() {
    val metrics = Metrics(CollectorRegistry())
    metrics.counterWithLabels.labels("value1", "value2").inc()
    assertThat(metrics.counterWithLabels.labels("value1", "value2").get())
      .isEqualTo(1.0)
    val labelValues = arrayOf("value3", "value4")
    metrics.counterWithLabels.labels(*labelValues).inc()
    assertThat(metrics.counterWithLabels.labels(*labelValues).get()).isEqualTo(1.0)
    //TODO() do something useful with the result of collect()
    //Four samples because there are created at 'samples' included
    assertThat(metrics.counterWithLabels.collect()[0].samples).hasSize(4)
    metrics.counterWithLabels.clear()
    assertThat(metrics.counterWithLabels.collect()[0].samples).hasSize(0)
  }

  @Test
  fun gaugeWithoutLabelsTest() {
    val metrics = Metrics(CollectorRegistry())
    metrics.gaugeWithoutLabels.set(5.0)
    metrics.gaugeWithoutLabels.inc()
    metrics.gaugeWithoutLabels.inc(3.0)
    assertThat(metrics.gaugeWithoutLabels.get()).isEqualTo(9.0)
    metrics.gaugeWithoutLabels.dec(5.0)
    metrics.gaugeWithoutLabels.dec()
    assertThat(metrics.gaugeWithoutLabels.get()).isEqualTo(3.0)
    metrics.gaugeWithoutLabels.clear()
    assertThat(metrics.gaugeWithoutLabels.get()).isEqualTo(0.0)
  }

  @Test
  fun gaugeWithLabelsTest() {
    val metrics = Metrics(CollectorRegistry())
    metrics.gaugeWithLabels.labels("value1", "value2").set(5.0)
    metrics.gaugeWithLabels.labels("value1", "value2").inc()
    metrics.gaugeWithLabels.labels("value1", "value2").inc(3.0)
    assertThat(metrics.gaugeWithLabels.labels("value1", "value2").get()).isEqualTo(9.0)
    metrics.gaugeWithLabels.labels("value1", "value2").dec(5.0)
    metrics.gaugeWithLabels.labels("value1", "value2").dec()
    assertThat(metrics.gaugeWithLabels.labels("value1", "value2").get()).isEqualTo(3.0)
    metrics.gaugeWithLabels.clear()
    assertThat(metrics.gaugeWithLabels.labels("value1", "value2").get()).isEqualTo(0.0)
  }

  @Test
  fun histogramTest() {
    val metrics = Metrics(CollectorRegistry())
    metrics.histogramWithLabels.labels("value1", "value2").observe(1.0)
    metrics.histogramWithLabels.labels("value1", "value2").startTimer().observeDuration()
    //TODO() replace with a fake clock
    metrics.histogramWithLabels.labels("value1", "value2").time { Thread.sleep(1) }
    assertThat(metrics.histogramWithLabels.labels("value1", "value2").get().sum).isCloseTo(
      1.001,
      Percentage.withPercentage(1.0)
    )
  }

  @Test
  fun summaryTest() {
    val metrics = Metrics(CollectorRegistry())
    metrics.summaryWithLabels.labels("value1", "value2").observe(1.0)
    //TODO() replace with a fake clock
    metrics.summaryWithLabels.labels("value1", "value2").time { Thread.sleep(1) }
    assertThat(metrics.summaryWithLabels.labels("value1", "value2").get().sum).isCloseTo(
      1.001,
      Percentage.withPercentage(1.0)
    )
  }
}