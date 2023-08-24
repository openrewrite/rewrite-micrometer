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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markup;

import java.util.ArrayList;
import java.util.List;

public class TimerToObservation extends Recipe {
    private static final String TIMER = "io.micrometer.core.instrument.Timer";
    private static final String OBSERVATION = "io.micrometer.observation.Observation";

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
                    private final MethodMatcher builderMatcher = new MethodMatcher(OBSERVATION + " builder(String)");
                    private final MethodMatcher registerMatcher = new MethodMatcher(TIMER + ".Builder register(io.micrometer.observation.ObservationRegistry)");
                    private final MethodMatcher tagMatcher = new MethodMatcher(TIMER + ".Builder tag(String, String)");
                    private final MethodMatcher tagsMatcher = new MethodMatcher(TIMER + ".Builder tags(..)");
                    private final MethodMatcher tagsIterableMatcher = new MethodMatcher(TIMER + ".Builder tags(Iterable)");

                    private final ChangeType changeTypeRegistry = new ChangeType("io.micrometer.core.instrument.MeterRegistry", "io.micrometer.observation.ObservationRegistry", null);
                    private final ChangeType changeTypeTimer = new ChangeType("io.micrometer.core.instrument.Timer","io.micrometer.observation.Observation", null);
                    private final ChangeMethodName changeRecord = new ChangeMethodName(OBSERVATION + " record*(..)", "observe", null, null);

                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext executionContext) {
                        J.CompilationUnit cu = compilationUnit;
                        cu = (J.CompilationUnit) changeTypeRegistry.getVisitor().visit(cu, executionContext);
                        cu = (J.CompilationUnit) changeTypeTimer.getVisitor().visit(cu, executionContext);
                        cu = (J.CompilationUnit) changeRecord.getVisitor().visit(cu, executionContext);
                        assert cu != null;
                        return super.visitCompilationUnit(cu, executionContext);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext executionContext) {
                        if (registerMatcher.matches(mi)) {
                            Expression timerName = null;
                            Expression registry = mi.getArguments().get(0);

                            List<String> builder = new ArrayList<>();
                            List<Expression> parameters = new ArrayList<>();

                            Expression maybeBuilder = mi.getSelect();
                            while (maybeBuilder instanceof J.MethodInvocation) {
                                J.MethodInvocation builderMethod = (J.MethodInvocation) maybeBuilder;
                                if (builderMatcher.matches(maybeBuilder)) {
                                    timerName = builderMethod.getArguments().get(0);
                                }
                                else if (tagMatcher.matches(maybeBuilder)) {
                                    builder.add("\n.lowCardinalityKeyValue(#{any(String)}, #{any(String)})");
                                    parameters.add(builderMethod.getArguments().get(0));
                                    parameters.add(builderMethod.getArguments().get(1));
                                }
                                else if (tagsIterableMatcher.matches(maybeBuilder)) {
                                    builder.add("\n.lowCardinalityKeyValues(KeyValues.of(#{any(Iterable)}, Tag::getKey, Tag::getValue))");
                                    parameters.addAll(builderMethod.getArguments());
                                }
                                else if (tagsMatcher.matches(maybeBuilder)) {
                                    String args = StringUtils.repeat("#{any(String)},", builderMethod.getArguments().size());
                                    args = args.substring(0, args.length() - 1);
                                    builder.add("\n.lowCardinalityKeyValues(KeyValues.of(" + args + "))");
                                    parameters.addAll(builderMethod.getArguments());
                                }
                                maybeBuilder = ((J.MethodInvocation) maybeBuilder).getSelect();
                            }
                            if (timerName != null) {
                                parameters.add(0, timerName);
                                parameters.add(1, registry);

                                maybeAddImport("io.micrometer.observation.Observation");
                                maybeAddImport("io.micrometer.common.KeyValues");
                                maybeAddImport("io.micrometer.core.instrument.Tag");
                                maybeRemoveImport("io.micrometer.core.instrument.Timer");

                                JavaTemplate template = JavaTemplate
                                        .builder("Observation.createNotStarted(#{any(java.lang.String)}, #{any()})"
                                                + String.join("", builder))
                                        .contextSensitive()
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(executionContext, "micrometer-observation", "micrometer-commons", "micrometer-core"))
                                        .imports("io.micrometer.observation.Observation")
                                        .imports("io.micrometer.common.KeyValues")
                                        .imports("io.micrometer.core.instrument.Tag")
                                        .build();

                                mi = autoFormat(
                                        template.apply(updateCursor(mi), mi.getCoordinates().replace(), parameters.toArray()),
                                        executionContext
                                );
                            }
                        }
                        return super.visitMethodInvocation(mi, executionContext);
                    }

                });
    }
}
