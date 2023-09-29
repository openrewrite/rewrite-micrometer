package org.openrewrite.micrometer.misk;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

public class NoExplicitEmptyLabelListTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoExplicitEmptyLabelList())
          .parser(JavaParser.fromJavaVersion()
            .classpath("misk-metrics", "kotlin-reflect", "kotlin-stdlib"));
    }

    @Test
    void emptyLabel() {
        //language=java
        rewriteRun(
          java(
            """
              import misk.metrics.v2.Metrics;
              import static kotlin.collections.CollectionsKt.listOf;
                            
              class Test {
                void test(Metrics metrics) {
                    metrics.counter("counter", "description", listOf());
                    metrics.gauge("gauge", "description", listOf());
                    metrics.peakGauge("peakGauge", "description", listOf());
                }
              }
              """,
            """
              import misk.metrics.v2.Metrics;
              import static kotlin.collections.CollectionsKt.listOf;
                            
              class Test {
                void test(Metrics metrics) {
                    metrics.counter("counter", "description");
                    metrics.gauge("gauge", "description");
                    metrics.peakGauge("peakGauge", "description");
                }
              }
              """
          )
        );
    }

    @Disabled
    @Test
    void emptyLabelKt() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import misk.metrics.v2.Metrics
              fun test(metrics: Metrics) {
                  metrics.counter("counter", "desc", listOf())
              }
              """,
            """
              import misk.metrics.v2.Metrics
              fun test(metrics: Metrics) {
                  metrics.counter("counter", "desc")
              }
              """
          )
        );
    }
}
