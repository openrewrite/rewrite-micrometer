/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class TimerToObservationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TimerToObservation())
          .parser(JavaParser.fromJavaVersion().classpath("micrometer-core"));
    }

    @DocumentExample
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
    void timerVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import io.micrometer.core.instrument.MeterRegistry;
              import io.micrometer.core.instrument.Timer;
              
              class Test {
                  private MeterRegistry registry;
              
                  void test(Runnable arg) {
                      Timer t = Timer.builder("my.timer")
                              .register(registry);
                      t.record(arg);
                  }
              }
              """,
            """
              import io.micrometer.observation.Observation;
              import io.micrometer.observation.ObservationRegistry;
              
              class Test {
                  private ObservationRegistry registry;
                  
                  void test(Runnable arg) {
                      Observation t = Observation.createNotStarted("my.timer", registry);
                      t.observe(arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepComments() {
        rewriteRun(
          //language=java
          java(
            """
              import io.micrometer.core.instrument.MeterRegistry;
              import io.micrometer.core.instrument.Timer;
              
              class Test {
                  private MeterRegistry registry;
              
                  void test(Runnable arg) {
                      // Comments on Timer
                      Timer.builder("my.timer")
                              // Comments on register
                              .register(registry)
                              // Comments on record
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
                      // Comments on Timer
                      Observation.createNotStarted("my.timer", registry)
                              // Comments on record
                              .observe(arg);
                  }
              }
              """
          )
        );
    }

    @Nested
    class Tags {
        @Test
        void tag() {
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
                              .tag("key", "value")
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
                              .highCardinalityKeyValue("key", "value")
                              .observe(arg);
                  }
              }
              """
              )
            );
        }

        @Test
        void tagsVarArgs() {
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
                                  .tags("key1", "value1", "key2", "value2")
                                  .register(registry)
                                  .record(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.common.KeyValues;
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
                  
                  class Test {
                      private ObservationRegistry registry;
                      
                      void test(Runnable arg) {
                          Observation.createNotStarted("my.timer", registry)
                                  .highCardinalityKeyValues(KeyValues.of("key1", "value1", "key2", "value2"))
                                  .observe(arg);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tagsArray() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Timer;
                  
                  class Test {
                      private MeterRegistry registry;
                  
                      void test(Runnable arg) {
                          String[] tags = new String[]{"key1", "value1", "key2", "value2"};
                          Timer.builder("my.timer")
                                  .tags(tags)
                                  .register(registry)
                                  .record(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.common.KeyValues;
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
    
                  class Test {
                      private ObservationRegistry registry;
                      
                      void test(Runnable arg) {
                          String[] tags = new String[]{"key1", "value1", "key2", "value2"};
                          Observation.createNotStarted("my.timer", registry)
                                  .highCardinalityKeyValues(KeyValues.of(tags))
                                  .observe(arg);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tagsCollection() {
            rewriteRun(
              // MethodInvocation->MethodInvocation->MethodInvocation->Block->MethodDeclaration->Block->ClassDeclaration->CompilationUnit
              ///*~~(MethodInvocation type is missing or malformed)~~>*/KeyValues.of(tags, Tag::getKey, Tag::getValue)
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  import io.micrometer.core.instrument.MeterRegistry;
                  import io.micrometer.core.instrument.Tag;
                  import io.micrometer.core.instrument.Timer;
                  
                  import java.util.List;

                  class Test {
                      private MeterRegistry registry;
                  
                      void test(Runnable arg) {
                          List<Tag> tags = List.of(
                                  Tag.of("key1", "value1"),
                                  Tag.of("key2", "value2")
                          );
                          Timer.builder("my.timer")
                                  .tags(tags)
                                  .register(registry)
                                  .record(arg);
                      }
                  }
                  """,
                """
                  import io.micrometer.common.KeyValues;
                  import io.micrometer.core.instrument.Tag;
                  import io.micrometer.observation.Observation;
                  import io.micrometer.observation.ObservationRegistry;
                  
                  import java.util.List;
                  
                  class Test {
                      private ObservationRegistry registry;
                      
                      void test(Runnable arg) {
                          List<Tag> tags = List.of(
                                  Tag.of("key1", "value1"),
                                  Tag.of("key2", "value2")
                          );
                          Observation.createNotStarted("my.timer", registry)
                                  .highCardinalityKeyValues(KeyValues.of(tags, Tag::getKey, Tag::getValue))
                                  .observe(arg);
                      }
                  }
                  """
              )
            );
        }
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
