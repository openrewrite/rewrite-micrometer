/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.micrometer.dropwizard;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.micrometer.table.DropwizardMetricsInUse;

public class FindDropwizardMetrics extends Recipe {
    final transient DropwizardMetricsInUse metrics = new DropwizardMetricsInUse(this);

    @Override
    public String getDisplayName() {
        return "Find Dropwizard metrics";
    }

    @Override
    public String getDescription() {
        return "Find uses of Dropwizard metrics that could be converted to a more modern metrics instrumentation library.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher counter = new MethodMatcher("com.codahale.metrics.MetricRegistry counter(..)");
        MethodMatcher gauge = new MethodMatcher("com.codahale.metrics.MetricRegistry gauge(..)");

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (counter.matches(method) || gauge.matches(method)) {
                    String metricType = counter.matches(method) ? "Counter" : "Gauge";
                    metrics.insertRow(ctx, new DropwizardMetricsInUse.Row(
                            getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath().toString(),
                            metricType,
                            method.printTrimmed(getCursor())
                    ));
                    return SearchResult.found(method, metricType);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
