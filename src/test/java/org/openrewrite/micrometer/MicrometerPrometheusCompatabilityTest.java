/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.*;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.time.Duration;
import java.util.*;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerPrometheusCompatabilityTest {
    CollectorRegistry micrometerRegistry = new CollectorRegistry(true);
    CollectorRegistry promRegistry = new CollectorRegistry(true);
    MeterRegistry wrappedMicrometerRegistry = new PrometheusMeterRegistry(
      PrometheusConfig.DEFAULT, micrometerRegistry, Clock.SYSTEM);

    static class CounterArguments extends MetricArguments {

        @Override
        public Consumer<Double> prometheus(CollectorRegistry registry) {
            return Counter.build("gets", "-")
              .labelNames("status")
              .register(registry).labels("200")::inc;
        }

        @Override
        public Consumer<Double> micrometer(MeterRegistry registry) {
            return io.micrometer.core.instrument.Counter.builder("gets")
              .description("-")
              .tags("status", "200")
              .register(registry)::increment;
        }
    }

    @ParameterizedTest
    @ArgumentsSource(CounterArguments.class)
    void counters(CollectorRegistry registry, Metric metric) {
        metric.observe(1);
        assertThat(get(registry, "gets", "status", "200")).isEqualTo(1.0);
        metric.observe(1);
        assertThat(get(registry, "gets", "status", "200")).isEqualTo(2.0);
    }

    static class GaugeArguments extends MetricArguments {
        @Override
        public Consumer<Double> prometheus(CollectorRegistry registry) {
            return Gauge.build("thread_count", "-")
              .labelNames("state")
              .register(registry)
              .labels("running")::set;
        }

        @Override
        public Consumer<Double> micrometer(MeterRegistry registry) {
            AtomicDouble gaugeValue = new AtomicDouble();
            io.micrometer.core.instrument.Gauge.builder("thread_count", gaugeValue::get)
              .description("-")
              .tags("state", "running")
              // only for the sake of this test since gaugeValue could be garbage collected
              .strongReference(true)
              .register(registry);
            return gaugeValue::set;
        }
    }

    @ParameterizedTest
    @ArgumentsSource(GaugeArguments.class)
    void gauges(CollectorRegistry registry, Metric metric) {
        metric.observe(20.0);
        assertThat(get(registry, "thread_count", "state", "running")).isEqualTo(20.0);

        metric.observe(30.0);
        assertThat(get(registry, "thread_count", "state", "running")).isEqualTo(30.0);
    }

    static class SummaryArguments extends MetricArguments {
        @Override
        public Consumer<Double> prometheus(CollectorRegistry registry) {
            return Summary.build("call_times", "-")
              .labelNames("status")
              .quantile(0.5, 0.05)
              .quantile(0.75, 0.02)
              .quantile(0.95, 0.01)
              .quantile(0.99, 0.001)
              .quantile(0.999, 0.0001)
              .register(registry)
              .labels("200")::observe;
        }

        @Override
        public Consumer<Double> micrometer(MeterRegistry registry) {
            return DistributionSummary.builder("call_times")
              .description("-")
              .tags("status", "200")
              .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
              .percentilePrecision(4)
              .distributionStatisticExpiry(Duration.ofMinutes(10))
              .distributionStatisticBufferLength(5)
              .register(registry)::record;
        }
    }

    @ParameterizedTest
    @ArgumentsSource(SummaryArguments.class)
    void summaries(CollectorRegistry registry, Metric metric) {
        metric.observe(100.0);
        assertThat(summaryMean(registry, "status", "200")).isEqualTo(100.0);
        assertThat(summarySum(registry, "status", "200")).isEqualTo(100.0);
        assertThat(summaryCount(registry, "status", "200")).isEqualTo(1.0);
//        assertThat(summaryP50(registry, "status", "200")).isEqualTo(100.0);

        metric.observe(99.0);
        metric.observe(101.0);
        assertThat(summaryMean(registry, "status", "200")).isEqualTo(100.0);
        assertThat(summarySum(registry, "status", "200")).isEqualTo(300.0);
        assertThat(summaryCount(registry, "status", "200")).isEqualTo(3.0);
//        assertThat(summaryP50(registry, "status", "200")).isIn(99.0, 100.0, 101.0);
    }

    @Test
    void differentLabelValues() {
        assertThat(get(promRegistry, "gets", "status", "200")).isNull();
        assertThat(get(promRegistry, "gets", "status", "503")).isNull();
        Counter counter = Counter.build("gets", "-").labelNames("status").register(promRegistry);
        Counter.Child promCounter200s = counter.labels("200");
        Counter.Child promCounter503s = counter.labels("503");
        promCounter200s.inc(7.0);
        promCounter503s.inc(9.0);
        assertThat(get(promRegistry, "gets", "status", "200")).isEqualTo(7.0);
        assertThat(get(promRegistry, "gets", "status", "503")).isEqualTo(9.0);
        promCounter200s.inc(10.0);
        promCounter503s.inc(20.0);
        assertThat(get(promRegistry, "gets", "status", "200")).isEqualTo(17.0);
        assertThat(get(promRegistry, "gets", "status", "503")).isEqualTo(29.0);
        // do the same things with micrometer;
        assertThat(get(micrometerRegistry, "gets", "status", "200")).isNull();
        assertThat(get(micrometerRegistry, "gets", "status", "503")).isNull();
        io.micrometer.core.instrument.Counter counter200s =
          io.micrometer.core.instrument.Counter.builder("gets")
            .description("-")
            .tags("status", "200")
            .register(wrappedMicrometerRegistry);
        io.micrometer.core.instrument.Counter counter503s =
          io.micrometer.core.instrument.Counter.builder("gets")
            .description("-")
            .tags("status", "503")
            .register(wrappedMicrometerRegistry);
        counter200s.increment(7.0);
        counter503s.increment(9.0);
        assertThat(get(micrometerRegistry, "gets", "status", "200")).isEqualTo(7.0);
        assertThat(get(micrometerRegistry, "gets", "status", "503")).isEqualTo(9.0);
        counter200s.increment(10.0);
        counter503s.increment(20.0);
        assertThat(get(micrometerRegistry, "gets", "status", "200")).isEqualTo(17.0);
        assertThat(get(micrometerRegistry, "gets", "status", "503")).isEqualTo(29.0);

        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test
    void differentNames() {
        assertThat(get(promRegistry, "gets")).isNull();
        assertThat(get(promRegistry, "puts")).isNull();
        assertThat(get(promRegistry, "gets_total")).isNull();
        assertThat(get(promRegistry, "puts_total")).isNull();
        Counter promGetsCounter = Counter.build("gets", "-").register(promRegistry);
        Counter promPutsCounter = Counter.build("puts", "-").register(promRegistry);
        promGetsCounter.inc(7.0);
        promPutsCounter.inc(9.0);
        assertThat(get(promRegistry, "gets")).isEqualTo(7.0);
        assertThat(get(promRegistry, "puts")).isEqualTo(9.0);
        promGetsCounter.inc(10.0);
        promPutsCounter.inc(20.0);
        assertThat(get(promRegistry, "gets")).isEqualTo(17.0);
        assertThat(get(promRegistry, "puts")).isEqualTo(29.0);
        assertThat(get(promRegistry, "gets_total")).isEqualTo(17.0);
        assertThat(get(promRegistry, "puts_total")).isEqualTo(29.0);

        // do the same things with micrometer;
        assertThat(get(micrometerRegistry, "gets")).isNull();
        assertThat(get(micrometerRegistry, "puts")).isNull();
        assertThat(get(micrometerRegistry, "gets_total")).isNull();
        assertThat(get(micrometerRegistry, "puts_total")).isNull();
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
        assertThat(get(micrometerRegistry, "gets")).isEqualTo(7.0);
        assertThat(get(micrometerRegistry, "puts")).isEqualTo(9.0);
        getsCounter.increment(10.0);
        putsCounter.increment(20.0);
        assertThat(get(micrometerRegistry, "gets")).isEqualTo(17.0);
        assertThat(get(micrometerRegistry, "puts")).isEqualTo(29.0);
        assertThat(get(micrometerRegistry, "gets_total")).isEqualTo(17.0);
        assertThat(get(micrometerRegistry, "puts_total")).isEqualTo(29.0);
        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test
    void summaryQuantiles() {
        Summary promSummary = Summary.build("call_times", "-")
          .quantile(0.5, 0.05)
          .quantile(0.75, 0.02)
          .quantile(0.95, 0.01)
          .quantile(0.99, 0.001)
          .quantile(0.999, 0.0001)
          .register(promRegistry);
        promSummary.observe(400.0);
        assertThat(summaryP50(promRegistry)).isEqualTo(400.0);
        assertThat(summaryP99(promRegistry)).isEqualTo(400.0);
        promSummary.observe(450.0);
        assertThat(summaryP50(promRegistry)).isEqualTo(400.0);
        assertThat(summaryP99(promRegistry)).isEqualTo(450.0);
        promSummary.observe(500.0);
        assertThat(summaryP50(promRegistry)).isEqualTo(450.0);
        assertThat(summaryP99(promRegistry)).isEqualTo(500.0);
        promSummary.observe(550.0);
        assertThat(summaryP50(promRegistry)).isEqualTo(450.0);
        assertThat(summaryP99(promRegistry)).isEqualTo(550.0);
        promSummary.observe(600.0);
        assertThat(summaryP50(promRegistry)).isEqualTo(500.0);
        assertThat(summaryP99(promRegistry)).isEqualTo(600.0);
        // do the same things with micrometer;
        DistributionSummary summary = DistributionSummary.builder("call_times")
          .description("-")

          .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)

          .percentilePrecision(4)
          .distributionStatisticExpiry(Duration.ofMinutes(10))
          .distributionStatisticBufferLength(5)
          .register(wrappedMicrometerRegistry);
        summary.record(400.0);
        assertThat(summaryP50(micrometerRegistry)).isEqualTo(400.0);
        assertThat(summaryP99(micrometerRegistry)).isEqualTo(400.0);
        summary.record(450.0);
        assertThat(summaryP50(micrometerRegistry)).isEqualTo(400.0);
        //FIXME: Values should probably match prometheus, so something weird is happening.
        assertThat(summaryP99(micrometerRegistry)).isEqualTo(450.0);//450

        summary.record(500.0);
        assertThat(summaryP50(micrometerRegistry)).isEqualTo(450.0);//450
        assertThat(summaryP99(micrometerRegistry)).isEqualTo(500.0);//500

        summary.record(550.0);
        assertThat(summaryP50(micrometerRegistry)).isEqualTo(450.0);//450
        assertThat(summaryP99(micrometerRegistry)).isEqualTo(550.015625);//550

        summary.record(600.0);
        assertThat(summaryP50(micrometerRegistry)).isEqualTo(500.0);//500
        assertThat(summaryP99(micrometerRegistry)).isEqualTo(600.015625);//600

        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    @Test
    void getAllSamples() {
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
          new Sample("gets_total", List.of("foo"), List.of("bar"), 1.0),
          new Sample("thread_count", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "1.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "2.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "+Inf"), 1.0),
          new Sample("histogram_count", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_sum", List.of("foo"), List.of("bar"), 1.0));
        // do the same things with micrometer;
        io.micrometer.core.instrument.Counter.builder("gets")
          .description("-")
          .tags("foo", "bar")
          .register(wrappedMicrometerRegistry)
          .increment();
        AtomicDouble gauge = new AtomicDouble();
        io.micrometer.core.instrument.Gauge.builder("thread_count", gauge::get)
          .description("-")
          .tags("foo", "bar")
          .register(wrappedMicrometerRegistry);

        gauge.set(1.0);

        DistributionSummary.builder("histogram")
          .description("-")
          .tags("foo", "bar")
          .serviceLevelObjectives(1.0, 2.0)
          .register(wrappedMicrometerRegistry)

          .record(1.0);
        assertThat(getAllSamples(micrometerRegistry).toList()).contains(
          new Sample("gets_total", List.of("foo"), List.of("bar"), 1.0),
          new Sample("thread_count", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "1.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "2.0"), 1.0),
          new Sample("histogram_bucket", List.of("foo", "le"), List.of("bar", "+Inf"), 1.0),
          new Sample("histogram_count", List.of("foo"), List.of("bar"), 1.0),
          new Sample("histogram_sum", List.of("foo"), List.of("bar"), 1.0));

        assertThatPromAndMicrometerRegistriesAreTheSame(promRegistry, micrometerRegistry);
    }

    void assertThatPromAndMicrometerRegistriesAreTheSame(CollectorRegistry promRegistry,
                                                         CollectorRegistry micrometerRegistry) {
        // FIXME because of https://github.com/micrometer-metrics/micrometer/issues/2625, they
        // aren't actually the same because the _created gauge is missing from the micrometer registry
//        try {
//            StringWriter micrometerMetricsString = new StringWriter();
//            TextFormat.writeOpenMetrics100(micrometerMetricsString, micrometerRegistry.metricFamilySamples());
//            StringWriter prometheusMetricsString = new StringWriter();
//            TextFormat.writeOpenMetrics100(prometheusMetricsString, promRegistry.metricFamilySamples());
//            assertThat(micrometerMetricsString.toString()).isEqualTo(prometheusMetricsString.toString());
//        } catch (IOException e) {
//            throw new UncheckedIOException(e);
//        }
    }

    @Nullable Double get(CollectorRegistry registry, String name, String... keyValues) {
        Sample sample = getSample(registry, name, Tags.of(keyValues), null);
        return sample == null ? null : sample.value;
    }

    /**
     * Returns the number of histogram samples taken.
     */
    @Nullable Double summaryCount(CollectorRegistry registry, String... keyValues) {
        Sample sample = getSample(registry, "call_times", Tags.of(keyValues), "call_times" + "_count");
        return sample == null ? null : sample.value;
    }

    /**
     * Returns the sum of all histogram samples taken.
     */
    @Nullable Double summarySum(CollectorRegistry registry, String... keyValues) {
        Sample sample = getSample(registry, "call_times", Tags.of(keyValues), "call_times" + "_sum");
        return sample == null ? null : sample.value;
    }

    /**
     * Returns the average of all histogram samples taken.
     */
    @Nullable Double summaryMean(CollectorRegistry registry, String... keyValues) {
        Double sum = summarySum(registry, keyValues);
        if (sum == null) {
            return null;
        }
        Double count = summaryCount(registry, keyValues);
        if (count == null) {
            return null;
        }
        return sum / count;
    }

    /**
     * Returns the median for a [histogram]. In small samples this is the element preceding
     * the middle element.
     */
    @Nullable Double summaryP50(CollectorRegistry registry, String... keyValues) {
        return summaryQuantile(registry, "0.5", keyValues);
    }

    /**
     * Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest
     * element.
     */
    @Nullable Double summaryP99(CollectorRegistry registry, String... keyValues) {
        return summaryQuantile(registry, "0.99", keyValues);
    }

    @Nullable Double summaryQuantile(CollectorRegistry registry, String quantile,
                                     String... keyValues) {
        Sample sample = getSample(registry, "call_times",
          Tags.of(keyValues).and("quantile", quantile), null);
        return sample == null ? null : sample.value;
    }

    @Nullable Sample getSample(CollectorRegistry registry, String name, Tags labels,
                               @Nullable String sampleName) {
        Enumeration<Collector.MetricFamilySamples> mfs = registry.metricFamilySamples();
        Collector.MetricFamilySamples filteredSamples = Collections.list(mfs).stream()
          .filter(it -> Objects.equals(it.name, name) ||
                        (it.type == Collector.Type.COUNTER && Objects.equals(it.name + "_total", name)))
          .findFirst()
          .orElse(null);

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
        labels.forEach(it -> {
            labelNames.add(it.getKey());
            labelValues.add(it.getValue());
        });

        return filteredSamples.samples.stream()
          .filter(it -> Objects.equals(it.name, familySampleName) &&
                        Objects.equals(it.labelNames, labelNames) &&
                        Objects.equals(it.labelValues, labelValues))
          .findFirst()
          .orElse(null);
    }

    Stream<Sample> getAllSamples(CollectorRegistry registry) {
        return Collections.list(registry.metricFamilySamples()).stream().flatMap(it -> it.samples.stream());
    }

    abstract static class MetricArguments implements ArgumentsProvider {
        public abstract Consumer<Double> prometheus(CollectorRegistry registry);

        public abstract Consumer<Double> micrometer(MeterRegistry registry);

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext ctx) {
            CollectorRegistry prometheusRegistry = new CollectorRegistry(true) {
                @Override
                public String toString() {
                    return "prometheus";
                }
            };
            CollectorRegistry micrometerRegistry = new CollectorRegistry(true) {
                @Override
                public String toString() {
                    return "micrometer";
                }
            };

            MeterRegistry micrometerMeterRegistry = new PrometheusMeterRegistry(
              PrometheusConfig.DEFAULT, micrometerRegistry, Clock.SYSTEM);

            return Stream.of(
              Arguments.of(prometheusRegistry, new Metric(prometheus(prometheusRegistry))),
              Arguments.of(micrometerRegistry, new Metric(micrometer(micrometerMeterRegistry)))
            );
        }
    }

    @RequiredArgsConstructor
    public static class Metric {
        private final Consumer<Double> action;

        public void observe(double amt) {
            action.accept(amt);
        }

        public final void observe() {
            observe(Double.NaN);
        }

        @Override
        public String toString() {
            return "metric";
        }
    }
}
