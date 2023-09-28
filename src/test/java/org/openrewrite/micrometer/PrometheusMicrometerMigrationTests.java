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

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class PrometheusMicrometerMigrationTests implements RewriteTest {

    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new TimerToObservation())
          .parser(JavaParser.fromJavaVersion().classpath("micrometer-core", "simpleclient"));
    }

    @Test
    void counterSimpleTest() {
        rewriteRun(
          //language=java
          java(
            """
              import io.prometheus.client.CollectorRegistry;
              import io.prometheus.client.Counter;
                            
              class Test {
                  private CollectorRegistry registry;
                            
                  void test() {
                       Counter counter = Counter.build("gets", "-")
                         .register(registry);
                         
                       counter.inc();
                       counter.inc(5);
                       counter.get();
                  }
              }
              """,
            """
              import io.micrometer.core.instrument.Counter;
              import io.micrometer.core.instrument.MeterRegistry;
                            
              class Test {
                  private MeterRegistry registry;
                  
                  void test() {
                      Counter counter= Counter.builder("gets")
                      .description("-")
                      .register(registry);
                      
                      counter.increment();
                      counter.increment(5);
                      counter.count();
                  }
              }
              """
          )
        );
    }

    @Test
    void summaryTest() {
        rewriteRun(
          //language=java
          java(
            """
              import io.prometheus.client.CollectorRegistry;
              import io.prometheus.client.Summary;
                            
              class Test {
                  private CollectorRegistry registry;
                            
                  void test() {
                       Summary summary = Summary.build("call_times", "-")
                                                      .quantile(0.5, 0.05)
                                                      .quantile(0.75, 0.02)
                                                      .quantile(0.95, 0.01)
                                                      .quantile(0.99, 0.001)
                                                      .quantile(0.999, 0.0001)
                                                      .register(registry);
                         
                       summary.observe(100.0);
                       summary.get();
                  }
              }
              """,
            """
              import io.micrometer.core.instrument.DistributionSummary;
              import io.micrometer.core.instrument.MeterRegistry;
                            
              class Test {
                  private MeterRegistry registry;
                  
                  void test() {
                      DistributionSummary summary = DistributionSummary.builder("call_times")
                                                    .description("-")
                                                    .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
                                                    .percentilePrecision(4)
                                                    .distributionStatisticExpiry(Duration.ofMinutes(10))
                                                    .distributionStatisticBufferLength(5)
                                                    .register(registry);
                      
                      summary.record(100.0);
                      summary.takeSnapshot();
                  }
              }
              """
          )
        );
    }

    @Test
    void histogramTest() {
        rewriteRun(
          //language=java
          java(
            """
              import io.prometheus.client.CollectorRegistry;
              import io.prometheus.client.Histogram;
                            
              class Test {
                  private CollectorRegistry registry;
                            
                  void test() {
                       Histogram histogram = Histogram.build("histogram", "-")
                                                   .buckets(1.0, 2.0)
                                                   .register(registry);
                       
                       histogram.observe(1.0);
                  }
              }
              """,
            """
              import io.micrometer.core.instrument.DistributionSummary;
              import io.micrometer.core.instrument.MeterRegistry;
                            
              class Test {
                  private MeterRegistry registry;
                  
                  void test() {
                      DistributionSummary histogram = DistributionSummary.builder("histogram")
                                                    .description("-")
                                                    .serviceLevelObjectives(1.0, 2.0)
                                                    .register(registry);
                                          
                      histogram.record(1.0);
                  }
              }
              """
          )
        );
    }

}
