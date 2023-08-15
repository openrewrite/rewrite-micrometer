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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Markup;

public class TimerToObservation extends Recipe {
    private static final String TIMER = "io.micrometer.core.instrument.Timer";

    @Override
    public String getDisplayName() {
        return "Convert Micrometer Timer to Observations";
    }

    @Override
    public String getDescription() {
        return "Convert Micrometer Timer to Observations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        Preconditions.or(
                                new UsesMethod<>(TIMER + " record*(..)", false),
                                new UsesMethod<>(TIMER + " wrap(..)", false)
                        ),
                        Preconditions.and(
                                Preconditions.not(new UsesMethod<>(TIMER + " record(java.time.Duration)", false)),
                                Preconditions.not(new UsesMethod<>(TIMER + " record(long, java.util.concurrent.TimeUnit)", false))
                        )
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                        return Markup.info(cd, "TODO: Convert Timer to Observations");
                    }
                });
    }
}
