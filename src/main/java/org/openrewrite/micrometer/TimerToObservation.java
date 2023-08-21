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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
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
                    MethodMatcher recordMatcher = new MethodMatcher(TIMER + " record*(..)");
                    MethodMatcher wrapMatcher = new MethodMatcher(TIMER + " wrap(..)");
                    MethodMatcher registerMatcher = new MethodMatcher(TIMER + ".Builder register(io.micrometer.observation.ObservationRegistry)");
                    MethodMatcher builderMatcher = new MethodMatcher(TIMER + " builder(String)");
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext executionContext) {
                        ChangeType changeType = new ChangeType("io.micrometer.core.instrument.MeterRegistry", "io.micrometer.observation.ObservationRegistry", null);
                        J.CompilationUnit cu = (J.CompilationUnit) changeType.getVisitor().visit(compilationUnit, executionContext);
                        if (cu == null) {
                            cu  = compilationUnit;
                        }
                        return super.visitCompilationUnit(cu, executionContext);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                        if (recordMatcher.matches(mi) || wrapMatcher.matches(mi)) {
                            String methodName = mi.getSimpleName().equals("wrap")? "wrap" : "observe";
                            Expression observable = mi.getArguments().get(0);
                            if (mi.getSelect() instanceof J.MethodInvocation) {
                                if (registerMatcher.matches(mi.getSelect())) {
                                    J.MethodInvocation register = (J.MethodInvocation) mi.getSelect();
                                    Expression registry = register.getArguments().get(0);
                                    Expression maybeBuilder = register.getSelect();
                                    while (maybeBuilder != null && !builderMatcher.matches(maybeBuilder)) {
                                        if (maybeBuilder instanceof J.MethodInvocation) {
                                            maybeBuilder = ((J.MethodInvocation) maybeBuilder).getSelect();
                                        } else {
                                            maybeBuilder = null;
                                        }
                                    }
                                    if (maybeBuilder != null) {
                                        J.MethodInvocation builder = (J.MethodInvocation) maybeBuilder;
                                        Expression timerName = builder.getArguments().get(0);

                                        JavaTemplate template = JavaTemplate
                                                .builder("Observation.createNotStarted(#{any(java.lang.String)}, #{any()})\n.#{}(#{any()})")
                                                .contextSensitive()
                                                .javaParser(JavaParser.fromJavaVersion()
                                                        .classpathFromResources(executionContext, "micrometer-observation"))
                                                .imports("io.micrometer.observation.Observation")
                                                .build();
                                        J.MethodInvocation observation = template.apply(getCursor(), mi.getCoordinates().replace(), timerName, registry, methodName, observable);

                                        maybeAddImport("io.micrometer.observation.Observation");
                                        maybeRemoveImport("io.micrometer.core.instrument.Timer");
                                        mi = autoFormat(observation, executionContext);
                                    }
                                }
                            }
                        }
                        return mi;
                    }
                });
    }
}
