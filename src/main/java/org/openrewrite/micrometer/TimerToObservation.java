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
