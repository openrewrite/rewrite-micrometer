package org.openrewrite.micrometer.misk;

import io.prometheus.client.SimpleCollector;
import misk.metrics.v2.Metrics;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static kotlin.collections.CollectionsKt.listOf;
import static org.openrewrite.java.JavaTemplate.compile;

public class NoExplicitEmptyLabelList extends Recipe {

    @Override
    public String getDisplayName() {
        return "Don't use an explicit empty label list";
    }

    @Override
    public String getDescription() {
        return "`listOf()` is the default argument for the `labels` parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {


        return new JavaIsoVisitor<>() {
            final List<JavaTemplate> hasEmptyLabel = Arrays.asList(
                    compile(this, "emptyLabelCounter", (JavaTemplate.F3<? extends SimpleCollector<?>, Metrics, String, String>)
                            (m, s, s1) -> m.counter(s, s1, listOf())).build(),
                    compile(this, "emptyLabelGauge", (JavaTemplate.F3<? extends SimpleCollector<?>, Metrics, String, String>)
                            (m, s, s1) -> m.gauge(s, s1, listOf())).build(),
                    compile(this, "emptyLabelPeakGauge", (JavaTemplate.F3<? extends SimpleCollector<?>, Metrics, String, String>)
                            (m, s, s1) -> m.peakGauge(s, s1, listOf())).build()
            );

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                AtomicInteger i = new AtomicInteger();
                Stream.<JavaTemplate.F3<? extends SimpleCollector<?>, Metrics, String, String>>of(
                        (m, s, s1) -> m.counter(s, s1, listOf()),
                        (m, s, s1) -> m.gauge(s, s1, listOf()),
                        (m, s, s1) -> m.peakGauge(s, s1, listOf())
                ).map(f -> compile(this, "emptyLabel" + i.incrementAndGet(), f).build()).collect(toList());

                if (method.getSelect() != null &&
                    TypeUtils.isOfClassType(method.getSelect().getType(), "misk.metrics.v2.Metrics") &&
                    hasEmptyLabel.stream().anyMatch(tmpl -> tmpl.matches(getCursor()))) {
                    return method.withArguments(ListUtils.map(method.getArguments(), (i, a) -> i == 2 ? null : a));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
