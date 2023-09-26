package org.openrewrite.micrometer;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.openrewrite.internal.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class MicrometerPrometheusCompatabilityTest {

    CollectorRegistry micrometerRegistry = new CollectorRegistry(true);
    CollectorRegistry promRegistry = new CollectorRegistry(true);
    MeterRegistry wrappedMicrometerRegistry =
      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, micrometerRegistry, Clock.SYSTEM);

    @Test void countHappyPath() throws IOException {
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "200")))).isNull();
        Counter.Child promCounter =
          Counter.build("gets", "-").labelNames("status").register(promRegistry).labels("200");
        promCounter.inc();
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(1.0);
        promCounter.inc();
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(2.0);

        // do the same things with micrometer
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "200")))).isNull();
        io.micrometer.core.instrument.Counter counter =
          io.micrometer.core.instrument.Counter.builder("gets")
            .description("-")
            .tags(List.of(Tag.of("status", "200")))
            .register(wrappedMicrometerRegistry);
        counter.increment();
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(
          1.0);
        counter.increment();
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(
          2.0);

        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test void gaugeHappyPath() throws IOException {
        // do everything with Prometheus;
        assertThat(get(promRegistry, "thread_count", List.of(Tag.of("state", "running")))).isNull();
        Gauge.Child promGauge = Gauge.build("thread_count", "-")
          .labelNames("state")
          .register(promRegistry)
          .labels("running");
        promGauge.set(20.0);
        assertThat(
          get(promRegistry, "thread_count", List.of(Tag.of("state", "running")))).isEqualTo(20.0);
        promGauge.set(30.0);
        assertThat(
          get(promRegistry, "thread_count", List.of(Tag.of("state", "running")))).isEqualTo(30.0);
        // do the same things with micrometer;
        assertThat(
          get(micrometerRegistry, "thread_count", List.of(Tag.of("state", "running")))).isNull();
        AtomicDouble gaugeValue = new AtomicDouble();
        io.micrometer.core.instrument.Gauge.builder("thread_count", gaugeValue::get)
          .description("-")
          .tags(List.of(Tag.of("state", "running")))
          .register(wrappedMicrometerRegistry);
        gaugeValue.set(20.0);
        assertThat(
          get(micrometerRegistry, "thread_count", List.of(Tag.of("state", "running")))).isEqualTo(
          20.0);
        gaugeValue.set(30.0);
        assertThat(
          get(micrometerRegistry, "thread_count", List.of(Tag.of("state", "running")))).isEqualTo(
          30.0);
        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test void summaryHappyPath() throws IOException {
        assertThat(get(promRegistry, "call_times", List.of(Tag.of("status", "200")))).isNull();
        Summary promSummary = Summary.build("call_times", "-")
          .labelNames("status")
          .quantile(0.5, 0.05)
          .quantile(0.75, 0.02)
          .quantile(0.95, 0.01)
          .quantile(0.99, 0.001)
          .quantile(0.999, 0.0001)
          .register(promRegistry);

        promSummary.labels("200").observe(100.0);
        assertThat(
          summaryMean(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          100.0);
        assertThat(
          summarySum(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          100.0);
        assertThat(
          summaryCount(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          1.0);
        assertThat(
          summaryP50(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          100.0);
        promSummary.labels("200").observe(99.0);
        promSummary.labels("200").observe(101.0);
        assertThat(
          summaryMean(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          100.0);
        assertThat(
          summarySum(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          300.0);
        assertThat(
          summaryCount(promRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          3.0);
        assertThat(summaryP50(promRegistry, List.of(Tag.of("status", "200")))).isIn(
          99.0, 100.0, 101.0);
        // do the same things with micrometer;
        assertThat(
          get(micrometerRegistry, "call_times", List.of(Tag.of("status", "200")))).isNull();
        DistributionSummary summary = DistributionSummary.builder("call_times")
          .description("-")
          .tags(List.of(Tag.of("status", "200")))
          .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
          .percentilePrecision(4)
          .distributionStatisticExpiry(Duration.ofMinutes(10))
          .distributionStatisticBufferLength(5)
          .register(wrappedMicrometerRegistry);
        summary.record(100.0);
        assertThat(summaryMean(micrometerRegistry,
          List.of(Tag.of("status", "200")))).isEqualTo(100.0);
        assertThat(
          summarySum(micrometerRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          100.0);
        assertThat(summaryCount(micrometerRegistry,
          List.of(Tag.of("status", "200")))).isEqualTo(1.0);
        assertThat(
          summaryP50(micrometerRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          100.0);
        summary.record(99.0);
        summary.record(101.0);
        assertThat(summaryMean(micrometerRegistry,
          List.of(Tag.of("status", "200")))).isEqualTo(100.0);
        assertThat(
          summarySum(micrometerRegistry, List.of(Tag.of("status", "200")))).isEqualTo(
          300.0);
        assertThat(summaryCount(micrometerRegistry,
          List.of(Tag.of("status", "200")))).isEqualTo(3.0);
        assertThat(
          summaryP50(micrometerRegistry, List.of(Tag.of("status", "200")))).isIn(99.0,
          100.0, 101.0);
        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test void differentLabelValues() throws IOException {
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "200")))).isNull();
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "503")))).isNull();
        Counter counter = Counter.build("gets", "-").labelNames("status").register(promRegistry);
        Counter.Child promCounter200s = counter.labels("200");
        Counter.Child promCounter503s = counter.labels("503");
        promCounter200s.inc(7.0);
        promCounter503s.inc(9.0);
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(7.0);
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "503")))).isEqualTo(9.0);
        promCounter200s.inc(10.0);
        promCounter503s.inc(20.0);
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(17.0);
        assertThat(get(promRegistry, "gets", List.of(Tag.of("status", "503")))).isEqualTo(29.0);
        // do the same things with micrometer;
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "200")))).isNull();
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "503")))).isNull();
        io.micrometer.core.instrument.Counter counter200s =
          io.micrometer.core.instrument.Counter.builder("gets")
            .description("-")
            .tags(List.of(Tag.of("status", "200")))
            .register(wrappedMicrometerRegistry);
        io.micrometer.core.instrument.Counter counter503s =
          io.micrometer.core.instrument.Counter.builder("gets")
            .description("-")
            .tags(List.of(Tag.of("status", "503")))
            .register(wrappedMicrometerRegistry);
        counter200s.increment(7.0);
        counter503s.increment(9.0);
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(
          7.0);
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "503")))).isEqualTo(
          9.0);
        counter200s.increment(10.0);
        counter503s.increment(20.0);
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "200")))).isEqualTo(
          17.0);
        assertThat(get(micrometerRegistry, "gets", List.of(Tag.of("status", "503")))).isEqualTo(
          29.0);
        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test void differentNames() throws IOException {
        assertThat(get(promRegistry, "gets", List.of())).isNull();
        assertThat(get(promRegistry, "puts", List.of())).isNull();
        assertThat(get(promRegistry, "gets_total", List.of())).isNull();
        assertThat(get(promRegistry, "puts_total", List.of())).isNull();
        Counter promGetsCounter = Counter.build("gets", "-").register(promRegistry);
        Counter promPutsCounter = Counter.build("puts", "-").register(promRegistry);

        promGetsCounter.inc(7.0);
        promPutsCounter.inc(9.0);
        assertThat(get(promRegistry, "gets", List.of())).isEqualTo(7.0);
        assertThat(get(promRegistry, "puts", List.of())).isEqualTo(9.0);
        promGetsCounter.inc(10.0);
        promPutsCounter.inc(20.0);
        assertThat(get(promRegistry, "gets", List.of())).isEqualTo(17.0);
        assertThat(get(promRegistry, "puts", List.of())).isEqualTo(29.0);
        assertThat(get(promRegistry, "gets_total", List.of())).isEqualTo(17.0);
        assertThat(get(promRegistry, "puts_total", List.of())).isEqualTo(29.0);
        // do the same things with micrometer;
        assertThat(get(micrometerRegistry, "gets", List.of())).isNull();
        assertThat(get(micrometerRegistry, "puts", List.of())).isNull();
        assertThat(get(micrometerRegistry, "gets_total", List.of())).isNull();
        assertThat(get(micrometerRegistry, "puts_total", List.of())).isNull();
        io.micrometer.core.instrument.Counter getsCounter =
          io.micrometer.core.instrument.Counter.builder("gets")
            .description("-")
            .register(wrappedMicrometerRegistry);
        io.micrometer.core.instrument.Counter putsCounter =
          io.micrometer.core.instrument.Counter.builder("puts")
            .description("-")
            .register(wrappedMicrometerRegistry);
        getsCounter.increment(7.0);
        putsCounter.increment(9.0);
        assertThat(get(micrometerRegistry, "gets", List.of())).isEqualTo(7.0);
        assertThat(get(micrometerRegistry, "puts", List.of())).isEqualTo(9.0);
        getsCounter.increment(10.0);
        putsCounter.increment(20.0);
        assertThat(get(micrometerRegistry, "gets", List.of())).isEqualTo(17.0);
        assertThat(get(micrometerRegistry, "puts", List.of())).isEqualTo(29.0);
        assertThat(get(micrometerRegistry, "gets_total", List.of())).isEqualTo(17.0);
        assertThat(get(micrometerRegistry, "puts_total", List.of())).isEqualTo(29.0);
        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test void summaryQuantiles() throws IOException {
        Summary promSummary = Summary.build("call_times", "-")
          .quantile(0.5, 0.05)
          .quantile(0.75, 0.02)
          .quantile(0.95, 0.01)
          .quantile(0.99, 0.001)
          .quantile(0.999, 0.0001)
          .register(promRegistry);
        promSummary.observe(400.0);
        assertThat(summaryP50(promRegistry, List.of())).isEqualTo(400.0);
        assertThat(summaryP99(promRegistry, List.of())).isEqualTo(400.0);
        promSummary.observe(450.0);
        assertThat(summaryP50(promRegistry, List.of())).isEqualTo(400.0);
        assertThat(summaryP99(promRegistry, List.of())).isEqualTo(450.0);
        promSummary.observe(500.0);
        assertThat(summaryP50(promRegistry, List.of())).isEqualTo(450.0);
        assertThat(summaryP99(promRegistry, List.of())).isEqualTo(500.0);
        promSummary.observe(550.0);
        assertThat(summaryP50(promRegistry, List.of())).isEqualTo(450.0);
        assertThat(summaryP99(promRegistry, List.of())).isEqualTo(550.0);
        promSummary.observe(600.0);
        assertThat(summaryP50(promRegistry, List.of())).isEqualTo(500.0);
        assertThat(summaryP99(promRegistry, List.of())).isEqualTo(600.0);
        // do the same things with micrometer;
        DistributionSummary summary = DistributionSummary.builder("call_times")
          .description("-")

          .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)

          .percentilePrecision(4)
          .distributionStatisticExpiry(Duration.ofMinutes(10))
          .distributionStatisticBufferLength(5)
          .register(wrappedMicrometerRegistry);
        summary.record(400.0);
        assertThat(summaryP50(micrometerRegistry, List.of())).isEqualTo(400.0);
        assertThat(summaryP99(micrometerRegistry, List.of())).isEqualTo(400.0);
        summary.record(450.0);
        assertThat(summaryP50(micrometerRegistry, List.of())).isEqualTo(400.0);
        //FIXME: Values should probably match prometheus, so something weird is happening.
        assertThat(summaryP99(micrometerRegistry, List.of())).isEqualTo(450.0);//450

        summary.record(500.0);
        assertThat(summaryP50(micrometerRegistry, List.of())).isEqualTo(450.0);//450
        assertThat(summaryP99(micrometerRegistry, List.of())).isEqualTo(500.0);//500

        summary.record(550.0);
        assertThat(summaryP50(micrometerRegistry, List.of())).isEqualTo(450.0);//450
        assertThat(summaryP99(micrometerRegistry, List.of())).isEqualTo(550.015625);//550

        summary.record(600.0);
        assertThat(summaryP50(micrometerRegistry, List.of())).isEqualTo(500.0);//500
        assertThat(summaryP99(micrometerRegistry, List.of())).isEqualTo(600.015625);//600

        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test void getAllSamples() throws IOException {
        Counter.build("gets", "-").labelNames("foo").register(promRegistry).labels("bar").inc();
        Gauge.build("thread_count", "-")
          .labelNames("foo")
          .register(promRegistry)
          .labels("bar")
          .inc();
        Histogram.build("histogram", "-")
          .labelNames("foo")
          .buckets(1.0, 2.0)
          .register(promRegistry)
          .labels("bar")
          .observe(1.0);
        assertThat(getAllSamples(promRegistry).toList()).contains(
          new Sample("counter_total", List.of("foo"), List.of("bar"), 1.0),
          new Sample("gauge", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "1.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "2.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "+Inf"), 1.0),
          new Sample("histogram_count", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_sum", List.of("foo"), List.of("bar"), 1.0));
        // do the same things with micrometer;
        io.micrometer.core.instrument.Counter.builder("gets")
          .description("-")
          .tags(List.of(Tag.of("foo", "bar")))
          .register(wrappedMicrometerRegistry)
          .increment();
        AtomicDouble gauge = new AtomicDouble();
        io.micrometer.core.instrument.Gauge.builder("thread_count", gauge::get)
          .description("-")
          .tags(List.of(Tag.of("foo", "bar")))
          .register(wrappedMicrometerRegistry);

        gauge.set(1.0);

        DistributionSummary.builder("histogram")
          .description("-")
          .tags(List.of(Tag.of("foo", "bar")))
          .serviceLevelObjectives(1.0, 2.0)
          .register(wrappedMicrometerRegistry)

          .record(1.0);
        assertThat(getAllSamples(micrometerRegistry).toList()).contains(
          new Sample("counter_total", List.of("foo"), List.of("bar"), 1.0),
          new Sample("gauge", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "1.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "2.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "+Inf"), 1.0),
          new Sample("histogram_count", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_sum", List.of("foo"), List.of("bar"), 1.0));
        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    void assertThatPromAndMicrometerRegistriesAreTheSame(CollectorRegistry promRegistry,
      CollectorRegistry micrometerRegistry) throws IOException {
        StringWriter micrometerMetricsString = new StringWriter();
        TextFormat.writeOpenMetrics100(micrometerMetricsString,
          micrometerRegistry.metricFamilySamples());
        StringWriter prometheusMetricsString = new StringWriter();
        TextFormat.writeOpenMetrics100(prometheusMetricsString, promRegistry.metricFamilySamples());

        assertThat(micrometerMetricsString.toString()).isEqualTo(
          prometheusMetricsString.toString());
    }

    @Nullable Double get(CollectorRegistry registry, String name, List<Tag> labels) {
        Sample sample = getSample(registry, name, labels, null);
        return sample == null ? null : sample.value;
    }

    /**
     * Returns the number of histogram samples taken.
     */
    @Nullable Double summaryCount(CollectorRegistry registry, List<Tag> labels) {

        Sample sample = getSample(registry, "call_times", labels, "call_times" + "_count");
        return sample == null ? null : sample.value;
    }

    /**
     * Returns the sum of all histogram samples taken.
     */
    @Nullable Double summarySum(CollectorRegistry registry, List<Tag> labels) {

        Sample sample = getSample(registry, "call_times", labels, "call_times" + "_sum");
        return sample == null ? null : sample.value;
    }

    /**
     * Returns the average of all histogram samples taken.
     */
    @Nullable Double summaryMean(CollectorRegistry registry, List<Tag> labels) {

        Double sum = summarySum(registry, labels);
        if (sum == null) return null;
        Double count = summaryCount(registry, labels);
        if (count == null) return null;
        return sum / count;
    }

    /**
     * Returns the median for a [histogram]. In small samples this is the element preceding
     * the middle element.
     */

    @Nullable Double summaryP50(CollectorRegistry registry, List<Tag> labels) {
        return summaryQuantile(registry, "0.5", labels);
    }

    /**
     * Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest
     * element.
     */

    @Nullable Double summaryP99(CollectorRegistry registry, List<Tag> labels) {
        return summaryQuantile(registry, "0.99", labels);
    }

    @Nullable Double summaryQuantile(CollectorRegistry registry, String quantile,
      List<Tag> labels) {
        List<Tag> extraLabels = new ArrayList<>(labels);
        extraLabels.add(Tag.of("quantile", quantile));
        Sample sample = getSample(registry, "call_times", extraLabels, null);
        return sample == null ? null : sample.value;
    }

    @Nullable Sample getSample(CollectorRegistry registry, String name, List<Tag> labels,
      @Nullable String sampleName) {

        Collector.MetricFamilySamples filteredSamples =
          streamOf(registry.metricFamilySamples()).filter(
            it -> Objects.equals(it.name, name) || (it.type == Collector.Type.COUNTER
              && Objects.equals(it.name + "_total", name))).findFirst().orElse(null);

        if (filteredSamples == null) {
            return null;
        }

        String familySampleName;
        if (sampleName != null) {
            familySampleName = sampleName;
        } else if (filteredSamples.type == Collector.Type.COUNTER && !name.endsWith("_total")) {
            familySampleName = name + "_total";
        } else {
            familySampleName = name;
        }

        List<String> labelNames = new ArrayList<>();

        List<String> labelValues = new ArrayList<>();
        //TODO use pairs
        labels.forEach(it -> {
            labelNames.add(it.getKey());
            labelValues.add(it.getValue());
        });

        return filteredSamples.samples.stream()
          .filter(it -> Objects.equals(it.name, familySampleName) && Objects.equals(it.labelNames,
            labelNames) && Objects.equals(it.labelValues, labelValues))
          .findFirst()
          .orElse(null);
    }

    private <T> Stream<T> streamOf(Enumeration<T> enumeration) {
        EnumerationSpliterator<T> spliterator =
          new EnumerationSpliterator<T>(Long.MAX_VALUE, Spliterator.ORDERED, enumeration);
        return StreamSupport.stream(spliterator, false);
    }

    Stream<Sample> getAllSamples(CollectorRegistry registry) {
        return streamOf(registry.metricFamilySamples()).flatMap(it -> it.samples.stream());
    }

    static class EnumerationSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

        private final Enumeration<T> enumeration;

        public EnumerationSpliterator(long est, int additionalCharacteristics,
          Enumeration<T> enumeration) {
            super(est, additionalCharacteristics);
            this.enumeration = enumeration;
        }

        @Override public void forEachRemaining(Consumer<? super T> action) {
            while (enumeration.hasMoreElements()) {
                action.accept(enumeration.nextElement());
            }
        }

        @Override public boolean tryAdvance(Consumer<? super T> action) {
            if (enumeration.hasMoreElements()) {
                action.accept(enumeration.nextElement());
                return true;
            }
            return false;
        }
    }
}
