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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

public class NoExplicitEmptyLabelList extends Recipe {

    @Getter
    final String displayName = "Don't use an explicit empty label list";

    @Getter
    final String description = "`listOf()` is the default argument for the `labels` parameter.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final List<JavaTemplate> hasEmptyLabel = Arrays.asList(
                    JavaTemplate.builder("#{m:any(misk.metrics.v2.Metrics)}.counter(#{s:any(java.lang.String)}, #{s1:any(java.lang.String)}, listOf())")
                            .staticImports("kotlin.collections.CollectionsKt.listOf")
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build(),
                    JavaTemplate.builder("#{m:any(misk.metrics.v2.Metrics)}.gauge(#{s:any(java.lang.String)}, #{s1:any(java.lang.String)}, listOf())")
                            .staticImports("kotlin.collections.CollectionsKt.listOf")
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build(),
                    JavaTemplate.builder("#{m:any(misk.metrics.v2.Metrics)}.peakGauge(#{s:any(java.lang.String)}, #{s1:any(java.lang.String)}, listOf())")
                            .staticImports("kotlin.collections.CollectionsKt.listOf")
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build());

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getSelect() != null &&
                    TypeUtils.isOfClassType(method.getSelect().getType(), "misk.metrics.v2.Metrics") &&
                    hasEmptyLabel.stream().anyMatch(tmpl -> tmpl.matches(getCursor()))) {
                    JavaType.Method methodType = method.getMethodType();
                    if (methodType != null) {
                        methodType = methodType.withParameterNames(emptyList()).withParameterTypes(emptyList());
                    }
                    return method
                            .withArguments(ListUtils.map(method.getArguments(), (i, a) -> i == 2 ? null : a))
                            .withMethodType(methodType)
                            .withName(method.getName().withType(methodType));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
