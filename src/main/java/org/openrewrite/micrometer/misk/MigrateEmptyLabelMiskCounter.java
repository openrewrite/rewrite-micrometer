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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static org.openrewrite.java.template.Semantics.expression;

public class MigrateEmptyLabelMiskCounter extends Recipe {

    @Getter
    final String displayName = "Migrate Misk counter to Micrometer";

    @Getter
    final String description = "Convert a Misk (Prometheus) counter to a Micrometer counter.";

    @Override
    public List<Recipe> getRecipeList() {
        return super.getRecipeList();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("misk.metrics.v2.Metrics", true), new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher miskCounter = new MethodMatcher("misk.metrics.v2.Metrics counter(..)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (miskCounter.matches(method)) {
                    boolean emptyLabel = method.getArguments().size() == 2;
                    if (method.getArguments().size() == 3 && method.getArguments().get(2) instanceof J.MethodInvocation) {
                        JavaType.Method arg2 = ((J.MethodInvocation) method.getArguments().get(2)).getMethodType();
                        emptyLabel = arg2 != null &&
                                     TypeUtils.isOfClassType(arg2.getDeclaringType(), "kotlin.collections.CollectionsKt") &&
                                     "listOf".equals(arg2.getName());
                    }
                    if (!emptyLabel) {
                        return m;
                    }
                    m = expression(
                            this, "micrometerCounter",
                            (String name, String help) -> Counter.builder(name).description(help).register(Metrics.globalRegistry)
                    ).build().apply(getCursor(),
                            method.getCoordinates().replace(),
                            method.getArguments().get(0),
                            method.getArguments().get(1));

                    maybeRemoveImport("misk.metrics.v2.Metrics");
                    maybeAddImport("io.micrometer.core.instrument.Counter");
                }
                return m;
            }
        });
    }
}
