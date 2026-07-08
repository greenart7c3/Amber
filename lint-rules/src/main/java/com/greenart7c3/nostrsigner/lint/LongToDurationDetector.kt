package com.greenart7c3.nostrsigner.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.UCallExpression

/**
 * Build-time port of Android Studio's "Long overload to Duration conversion" IDE
 * inspection (ConvertLongToDurationInspection), which only runs inside the IDE.
 * Flags calls that resolve to the legacy Long-millisecond overloads of the
 * kotlinx.coroutines timing functions so they get migrated to the
 * kotlin.time.Duration overloads.
 */
class LongToDurationDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf(
        "delay",
        "withTimeout",
        "withTimeoutOrNull",
        "debounce",
        "sample",
        "throttle",
        "timeout",
        "onTimeout",
        "advanceTimeBy",
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val packageName = context.evaluator.getPackage(method)?.qualifiedName ?: return
        if (!packageName.startsWith("kotlinx.coroutines")) return
        // The legacy overloads take the time value as a Long. Duration is a value
        // class backed by a JVM long, so the Duration overloads erase to the same
        // JVM signature and PSI resolution cannot tell them apart — classify by the
        // Kotlin type of the argument at the call site instead.
        if (method.parameterList.parameters.none { it.type == PsiTypes.longType() }) return
        for (argument in node.valueArguments) {
            val ktExpression = argument.sourcePsi as? KtExpression ?: continue
            val argumentClassFqName = try {
                analyze(ktExpression) {
                    (ktExpression.expressionType as? KaClassType)?.classId?.asFqNameString()
                }
            } catch (_: Throwable) {
                // Analysis session unavailable — cannot classify the overload
                return
            }
            when (argumentClassFqName) {
                // Already migrated to the Duration overload
                "kotlin.time.Duration" -> return
                "kotlin.Long", "kotlin.Int" -> {
                    context.report(
                        ISSUE,
                        node,
                        context.getCallLocation(node, includeReceiver = false, includeArguments = true),
                        "Legacy `Long` overload can be converted to `Duration`",
                    )
                    return
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "LongToDuration",
            briefDescription = "Legacy Long overload can be converted to Duration",
            explanation = """
                kotlinx.coroutines timing functions (`delay`, `withTimeout`, `debounce`, …) \
                have `kotlin.time.Duration` overloads that are preferred over the legacy \
                Long-millisecond overloads. Replace e.g. `delay(3000)` with \
                `delay(3.seconds)` using `kotlin.time.Duration.Companion` extensions.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(LongToDurationDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
}
