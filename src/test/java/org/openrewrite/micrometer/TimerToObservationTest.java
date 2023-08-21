/*
 * Copyright 2021 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TimerToObservationTest implements RewriteTest {

    public void defaults(RecipeSpec spec) {
        spec.recipe(new TimerToObservation())
          .parser(JavaParser.fromJavaVersion().classpath("micrometer-core"));
    }

    @Test
    void recordRunnable() {
        rewriteRun(
          //language=java
          java(
            """
              import io.micrometer.core.instrument.MeterRegistry;
              import io.micrometer.core.instrument.Timer;
              
              class Test {
                  private MeterRegistry registry;
              
                  void test(Runnable arg) {
                      Timer.builder("my.timer")
                          .register(registry)
                          .record(arg);
                  }
              }
              """,
            """
              import io.micrometer.observation.Observation;
              import io.micrometer.observation.ObservationRegistry;
              
              class Test {
                  private ObservationRegistry registry;
                  
                  void test(Runnable arg) {
                      Observation.createNotStarted("my.timer", registry)
                              .observe(arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void recordSupplier() {
        rewriteRun(
          //language=java
          java(
            """
              import io.micrometer.core.instrument.MeterRegistry;
              import io.micrometer.core.instrument.Timer;
              
              import java.util.function.Supplier;
              
              class Test {
                  private MeterRegistry registry;
              
                  void test(Supplier<String> arg) {
                      String result = Timer.builder("my.timer")
                          .register(registry)
                          .record(arg);
                  }
              }
              """,
            """
              import io.micrometer.observation.Observation;
              import io.micrometer.observation.ObservationRegistry;
              
              import java.util.function.Supplier;
              
              class Test {
                  private ObservationRegistry registry;
                  
                  void test(Supplier<String> arg) {
                      String result = Observation.createNotStarted("my.timer", registry)
                              .observe(arg);
                  }
              }
              """
          )
        );
    }

    @Nested
    class Callable {
        @Test
        void recordCallable() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  import java.util.concurrent.Callable;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test(Callable<String> arg) {
                          String result = Timer.builder("my.timer")
                              .register(registry)
                              .recordCallable(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
                  
                  import java.util.concurrent.Callable;
                  
                  class Test {
                      private ObservationRegistry registry;
                      
                      void test(Callable<String> arg) {
                          String result = Observation.createNotStarted("my.timer", registry)
                                  .observe(arg);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Wrap {

        @Test
        void wrapRunnable() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test(Runnable arg) {
                          Runnable result = Timer.builder("my.timer")
                                  .register(registry)
                                  .wrap(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
                  
                  class Test {
                      private ObservationRegistry registry;
                  
                      void test(Runnable arg) {
                          Runnable result = Observation.createNotStarted("my.timer", registry)
                                  .wrap(arg);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void wrapSupplier() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  import java.util.function.Supplier;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test(Supplier<String> arg) {
                          Supplier<String> result = Timer.builder("my.timer")
                              .register(registry)
                              .wrap(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
                  
                  import java.util.function.Supplier;
                  
                  class Test {
                      private ObservationRegistry registry;
                  
                      void test(Supplier<String> arg) {
                          Supplier<String> result = Observation.createNotStarted("my.timer", registry)
                                  .wrap(arg);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void wrapCallable() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  import java.util.concurrent.Callable;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test(Callable<String> arg) {
                          Callable<String> result = Timer.builder("my.timer")
                              .register(registry)
                              .wrap(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
                  
                  import java.util.concurrent.Callable;
                  
                  class Test {
                      private ObservationRegistry registry;
                  
                      void test(Callable<String> arg) {
                          Callable<String> result = Observation.createNotStarted("my.timer", registry)
                                  .wrap(arg);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Ignore {
        @Test
        void noTimer() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.Counter;
                  import io.micrometer.core.instrument.MeterRegistry;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test() {
                          Counter.builder("my.counter")
                              .register(registry)
                              .increment();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void recordDuration() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  import java.time.Duration;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test() {
                          Timer.builder("my.timer")
                              .register(registry)
                              .record(Duration.ofMillis(100));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void recordTimeUnit() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  import java.util.concurrent.TimeUnit;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test() {
                          Timer.builder("my.timer")
                              .register(registry)
                              .record(100, TimeUnit.MILLISECONDS);
                      }
                  }
                  """
              )
            );
        }
    }

}