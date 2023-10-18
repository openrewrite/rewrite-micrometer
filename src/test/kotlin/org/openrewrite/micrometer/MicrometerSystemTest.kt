package org.openrewrite.micrometer

import com.google.common.util.concurrent.AtomicDouble
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import java.time.Duration

class MicrometerSystemTest {
  class Metrics(val registry: MeterRegistry) {
    fun counterWithoutLabels(): Counter {
      return Counter.builder("counterWithoutLabels")
        .description("This is a Counter(No Labels) description")
        .register(registry)
    }

    fun counterWithLabels(counterLabel1: String, counterLabel2: String): Counter {
      return Counter.builder("counterWithoutLabels")
        .description("This is a Counter(No Labels) description")
        .tags("counterLabel1", counterLabel1, "counterLabel2", counterLabel2)

        .register(registry)
    }

    fun histogramWithLabels(histogramLabel1: String, histogramLabel2: String): DistributionSummary {
      return DistributionSummary.builder("histogramWithLabels")
        .description("This is a Histogram description")
        .tags("histogramLabel1", histogramLabel1, "histogramLabel2", histogramLabel2)
        .serviceLevelObjectives(1.0, 2.0)
        .register(registry)
    }

    fun summaryWithLabels(summaryLabel1: String, summaryLabel2: String): DistributionSummary {
      return DistributionSummary.builder("summaryWithLabels")
        .description("This is a Summary description")
        .tags("summaryLabel1", summaryLabel1, "summaryLabel2", summaryLabel2)
        .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
        .percentilePrecision(4)
        // The default for this in prometheus was 10 minutes (above), default in micrometer is 2 minutes
        .distributionStatisticExpiry(Duration.ofMinutes(10))
        // this is the default in prometheus. The default in micrometer is 3
        .distributionStatisticBufferLength(5)
        .register(registry)
    }

    fun gaugeWithoutLabels(supplier: AtomicDouble): AtomicDouble {
      Gauge.builder("gaugeWithoutLabels", supplier::get)
        .description("This is a Gauge(No Labels) description")
        .register(registry)
      return supplier
    }

    fun gaugeWithLabels(supplier: AtomicDouble, gaugeLabel1: String, gaugeLabel2: String): AtomicDouble {
      Gauge.builder("gaugeWithLabels", supplier::get)
        .description("This is a Gauge(Labels) description")
        .tags("gaugeLabel1", gaugeLabel1, "gaugeLabel2", gaugeLabel2)
        .register(registry)
      return supplier
    }
  }

  @Test
  fun counterSimpleTest() {
    val metrics = Metrics(SimpleMeterRegistry())
    metrics.counterWithoutLabels().increment()
    metrics.counterWithoutLabels().increment(5.0)
    Assertions.assertThat(metrics.counterWithoutLabels().count()).isEqualTo(6.0)
  }

  @Test fun clearACounter() {
    val metrics = Metrics(SimpleMeterRegistry())
    metrics.counterWithoutLabels().increment()
    metrics.registry.remove(metrics.counterWithoutLabels())
    metrics.counterWithoutLabels().increment(5.0)
    Assertions.assertThat(metrics.counterWithoutLabels().count()).isEqualTo(5.0)
  }

  @Test
  fun counterWithLabels() {
    val metrics = Metrics(SimpleMeterRegistry())
    metrics.counterWithLabels("value1", "value2").increment()
    Assertions.assertThat(metrics.counterWithLabels("value1", "value2").count())
      .isEqualTo(1.0)
    val labelValues = arrayOf("value3", "value4")
    metrics.counterWithLabels(labelValues[0], labelValues[1]).increment()
    Assertions.assertThat(metrics.counterWithLabels(labelValues[0], labelValues[1]).count())
      .isEqualTo(1.0)
    //TODO() do something useful with the result of collect()
    //Four samples because there are created at 'samples' included
    //TODO figure out how to convert this to micrometer
    // Assertions.assertThat(metrics.counterWithLabels().collect()[0].samples).hasSize(4)
    // metrics.counterWithLabels().clear()
    // Assertions.assertThat(metrics.counterWithLabels().collect()[0].samples).hasSize(0)
  }

  @Test
  fun gaugeWithoutLabelsTest() {
    val metrics = Metrics(SimpleMeterRegistry())
    // java atomics have an adder and an accumulator or a reference for doubles. Using googles for convenience because the adder doesn't have a set() and won't chain a reset
    val gauge= AtomicDouble()
    metrics.gaugeWithoutLabels(gauge).set(5.0)
    metrics.gaugeWithoutLabels(gauge).addAndGet(1.0)
    metrics.gaugeWithoutLabels(gauge).addAndGet(3.0)
    Assertions.assertThat(metrics.gaugeWithoutLabels(gauge).get()).isEqualTo(9.0)
    metrics.gaugeWithoutLabels(gauge).addAndGet(-5.0)
    metrics.gaugeWithoutLabels(gauge).addAndGet(-1.0)
    Assertions.assertThat(metrics.gaugeWithoutLabels(gauge).get()).isEqualTo(3.0)
    metrics.gaugeWithoutLabels(gauge).set(0.0)
    Assertions.assertThat(metrics.gaugeWithoutLabels(gauge).get()).isEqualTo(0.0)
  }

  @Test
  fun gaugeWithLabelsTest() {
    val metrics = Metrics(SimpleMeterRegistry())
    val gauge= AtomicDouble()
    metrics.gaugeWithLabels(gauge,"value1", "value2").set(5.0)
    metrics.gaugeWithLabels(gauge,"value1", "value2").addAndGet(1.0)
    metrics.gaugeWithLabels(gauge,"value1", "value2").addAndGet(3.0)
    Assertions.assertThat(metrics.gaugeWithLabels(gauge,"value1", "value2").get()).isEqualTo(9.0)
    metrics.gaugeWithLabels(gauge,"value1", "value2").addAndGet(-5.0)
    metrics.gaugeWithLabels(gauge,"value1", "value2").addAndGet(-1.0)
    Assertions.assertThat(metrics.gaugeWithLabels(gauge,"value1", "value2").get()).isEqualTo(3.0)
    metrics.gaugeWithLabels(gauge,"value1", "value2").set(0.0)
    Assertions.assertThat(metrics.gaugeWithLabels(gauge,"value1", "value2").get()).isEqualTo(0.0)
  }

  @Test
  fun histogramTest() {
    val metrics = Metrics(SimpleMeterRegistry())
    metrics.histogramWithLabels("value1", "value2").record(1.0)

    //TODO() This is probably a different thing entirely
    // metrics.histogramWithLabels("value1", "value2").startTimer().observeDuration()
    //TODO() replace with a fake clock
    // metrics.histogramWithLabels("value1", "value2").time { Thread.sleep(1) }
    Assertions.assertThat(metrics.histogramWithLabels("value1", "value2").totalAmount()).isCloseTo(
      1.001,
      Percentage.withPercentage(1.0)
    )
  }

  @Test
  fun summaryTest() {
    val metrics = Metrics(SimpleMeterRegistry())
    metrics.summaryWithLabels("value1", "value2").record(1.0)
    //TODO() replace with a fake clock
    //TODO() This is probably a different thing entirely

    // metrics.summaryWithLabels("value1", "value2").time { Thread.sleep(1) }
    Assertions.assertThat(metrics.summaryWithLabels("value1", "value2").totalAmount()).isCloseTo(
      1.001,
      Percentage.withPercentage(1.0)
    )
  }
}