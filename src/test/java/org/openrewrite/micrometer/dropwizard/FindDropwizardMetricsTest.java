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
package org.openrewrite.micrometer.dropwizard;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.micrometer.table.DropwizardMetricsInUse;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class FindDropwizardMetricsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDropwizardMetrics())
          .parser(JavaParser.fromJavaVersion().classpath("metrics-core"));
    }

    @DocumentExample
    @Test
    void findDropwizardMetrics() {
        rewriteRun(
          spec -> spec.dataTable(DropwizardMetricsInUse.Row.class, list ->
            assertThat(list).anySatisfy(row ->
              assertThat(row.getMetricCode()).isEqualTo("registry.counter(\"my.counter\")"))),
          //language=java
          java(
            """
              import com.codahale.metrics.MetricRegistry;
              
              class Test {
                 void instrument(MetricRegistry registry) {
                     registry.counter("my.counter");
                     registry.gauge("my.gauge");
                 }
              }
              """,
            """
              import com.codahale.metrics.MetricRegistry;
              
              class Test {
                 void instrument(MetricRegistry registry) {
                     /*~~(Counter)~~>*/registry.counter("my.counter");
                     /*~~(Gauge)~~>*/registry.gauge("my.gauge");
                 }
              }
              """
          )
        );
    }
}
