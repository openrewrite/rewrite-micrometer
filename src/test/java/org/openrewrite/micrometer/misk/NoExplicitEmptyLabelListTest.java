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
package org.openrewrite.micrometer.misk;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class NoExplicitEmptyLabelListTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoExplicitEmptyLabelList())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "misk-metrics", "kotlin-stdlib-1"));
    }

    @DocumentExample
    @Test
    void emptyLabel() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "misk-metrics", "kotlin-stdlib-1")),
          //language=kotlin
          kotlin(
            """
              import misk.metrics.v2.Metrics
              fun test(metrics: Metrics) {
                  metrics.counter("counter", "description", listOf())
                  metrics.gauge("gauge", "description", listOf())
                  metrics.peakGauge("peakGauge", "description", listOf())
              }
              """,
            """
              import misk.metrics.v2.Metrics
              fun test(metrics: Metrics) {
                  metrics.counter("counter", "description")
                  metrics.gauge("gauge", "description")
                  metrics.peakGauge("peakGauge", "description")
              }
              """
          )
        );
    }

    @Test
    void labelsKept() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "misk-metrics", "kotlin-stdlib-1")),
          //language=kotlin
          kotlin(
            """
              import misk.metrics.v2.Metrics
              fun test(metrics: Metrics) {
                  metrics.counter("counter", "description", listOf("label"))
              }
              """
          )
        );
    }

    @Test
    void javaCallKeepsItsLabels() {
        rewriteRun(
          //language=java
          java(
            """
              import misk.metrics.v2.Metrics;
              import static kotlin.collections.CollectionsKt.listOf;

              class Test {
                void test(Metrics metrics) {
                    metrics.counter("counter", "description", listOf());
                }
              }
              """
          )
        );
    }
}
