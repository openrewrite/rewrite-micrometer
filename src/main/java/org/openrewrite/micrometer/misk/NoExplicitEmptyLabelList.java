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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NoExplicitEmptyLabelList extends Recipe {

    @Getter
    final String displayName = "Don't use an explicit empty label list";

    @Getter
    final String description = "`listOf()` is the default argument for the `labels` parameter.";

    private static final MethodMatcher METRIC = new MethodMatcher("misk.metrics.v2.Metrics *(..)");
    private static final Set<String> WITH_LABELS_THIRD = new HashSet<>(Arrays.asList("counter", "gauge", "peakGauge"));
    private static final MethodMatcher LIST_OF = new MethodMatcher("kotlin.collections.CollectionsKt listOf()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(METRIC), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // Only Kotlin has the default arguments that let a call leave out its labels
                String path = sourceFile.getSourcePath().toString();
                return (path.endsWith(".kt") || path.endsWith(".kts")) && super.isAcceptable(sourceFile, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (METRIC.matches(mi) && WITH_LABELS_THIRD.contains(mi.getSimpleName()) &&
                    mi.getArguments().size() == 3 && LIST_OF.matches(mi.getArguments().get(2))) {
                    return mi.withArguments(ListUtils.map(mi.getArguments(), (i, a) -> i == 2 ? null : a));
                }
                return mi;
            }
        });
    }
}
