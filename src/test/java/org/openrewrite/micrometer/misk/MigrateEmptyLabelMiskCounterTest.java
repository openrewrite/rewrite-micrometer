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
package org.openrewrite.micrometer.misk;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateEmptyLabelMiskCounterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateEmptyLabelMiskCounter())
          .parser(JavaParser.fromJavaVersion()
            .classpath("misk-metrics", "kotlin-reflect", "kotlin-stdlib"));
    }

    @Test
    void migrateEmptyLabel() {
        //language=java
        rewriteRun(
          java(
            """
              import misk.metrics.v2.Metrics;
              import static kotlin.collections.CollectionsKt.listOf;
                            
              class Test {
                void test(Metrics metrics) {
                    metrics.counter("counter", "description", listOf());
                }
              }
              """,
            """
              import io.micrometer.core.instrument.Counter;
              import misk.metrics.v2.Metrics;
              import static kotlin.collections.CollectionsKt.listOf;
                            
              class Test {
                void test(Metrics metrics) {
                    Counter.builder("counter").description("description").register(Metrics.globalRegistry);
                }
              }
              """
          )
        );
    }
}
