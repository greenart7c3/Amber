package com.greenart7c3.nostrsigner.tools.unused

import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/** A Kotlin source to analyze. Declarations are only collected from [declares] sources (not tests). */
class Source(val path: String, val text: String, val declares: Boolean)

class Finding(val path: String, val line: Int, val message: String) {
    override fun toString() = "$path:$line: $message"
}

/**
 * Name-based dead-code finder over syntax trees: a non-private declaration is
 * unused when its name is referenced nowhere, neither in any Kotlin source
 * (tests included) nor in [otherTokens] (identifiers from XML, ProGuard rules,
 * Gradle scripts and the Rust JNI side). Matching by name never reports code
 * that is used, at the cost of missing an unused declaration whose name is
 * also used for something else.
 *
 * Entry points the build reaches by convention are never reported: overrides,
 * operators, `main`, companion objects, constructor properties, previews,
 * tests and annotation-processor targets, nor the entries of an enum that
 * the sources look up by name or list (`valueOf`, `entries`, `values()`). `@Suppress("unused")` on a
 * declaration, an enclosing one, or the file opts out on purpose.
 */
// The parser needs a compiler environment, whose API Kotlin plans to rework.
@OptIn(CoreEnvironmentDeprecation::class, CompilerConfiguration.Internals::class, ExperimentalCompilerApi::class)
class UnusedCodeAnalyzer {
    private val disposable = Disposer.newDisposable()
    private val factory = KtPsiFactory(
        KotlinCoreEnvironment.createForProduction(
            disposable,
            CompilerConfiguration().apply { extensionsStorage = CompilerPluginRegistrar.ExtensionStorage() },
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        ).project,
        markGenerated = false,
    )

    fun analyze(sources: List<Source>, otherTokens: Set<String>): List<Finding> {
        val files = sources.map { it to factory.createFile(it.path.substringAfterLast('/'), it.text.replace("\r\n", "\n")) }
        val references = HashSet(otherTokens)
        files.forEach { (_, file) -> references += referencedNames(file) }
        val listedEnums = files.flatMapTo(HashSet()) { (_, file) -> listedEnums(file) }

        val findings = mutableListOf<Finding>()
        for ((source, file) in files) {
            if (source.declares) {
                declarations(file)
                    .filter { it.name !in references }
                    .filter { it !is KtEnumEntry || it.getStrictParentOfType<KtClass>()?.name !in listedEnums }
                    .forEach { findings += Finding(source.path, lineOf(file, it), "${kindOf(it)} '${it.name}' is never used") }
            }
            unusedCatchParameters(file).forEach { findings += Finding(source.path, lineOf(file, it), "catch parameter '${it.name}' is never used; name it _") }
        }
        Disposer.dispose(disposable)
        return findings.sortedWith(compareBy({ it.path }, { it.line }))
    }

    private fun referencedNames(file: KtFile): Set<String> {
        val names = HashSet<String>()
        file.accept(
            object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    if (expression.parents.none { it is KtImportDirective || it is KtPackageDirective }) {
                        names += expression.getReferencedName()
                    }
                    super.visitSimpleNameExpression(expression)
                }
            },
        )
        return names
    }

    /**
     * Enums whose entries are reached without their names: `X.valueOf(…)`,
     * `X.entries`, `X.values()`, `enumValueOf<X>()`…, or an unqualified
     * `entries`/`values()`/`valueOf(…)` inside the enum itself.
     */
    private fun listedEnums(file: KtFile): Set<String> {
        val names = HashSet<String>()
        file.collectDescendantsOfType<KtSimpleNameExpression>().forEach { expression ->
            val name = expression.getReferencedName()
            if (name in ENUM_LISTINGS) {
                val receiver = (expression.parent as? KtCallExpression ?: expression).let { it.parent as? KtDotQualifiedExpression }
                    ?.takeIf { it.selectorExpression == (expression.parent as? KtCallExpression ?: expression) }
                    ?.receiverExpression as? KtSimpleNameExpression
                names += receiver?.getReferencedName() ?: expression.getStrictParentOfType<KtClass>()?.takeIf { it.isEnum() }?.name ?: return@forEach
            }
            if (name in ENUM_LISTING_FUNCTIONS) {
                (expression.parent as? KtCallExpression)?.typeArguments?.forEach { argument -> argument.text?.substringAfterLast('.')?.let(names::add) }
            }
        }
        return names
    }

    private fun declarations(file: KtFile): List<KtNamedDeclaration> {
        val result = mutableListOf<KtNamedDeclaration>()
        file.accept(
            object : KtTreeVisitorVoid() {
                override fun visitNamedFunction(function: KtNamedFunction) {
                    if (!function.isLocal && function.name != "main") consider(function)
                    super.visitNamedFunction(function)
                }

                override fun visitProperty(property: KtProperty) {
                    if (!property.isLocal) consider(property)
                    super.visitProperty(property)
                }

                override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                    val companion = classOrObject is KtObjectDeclaration && classOrObject.isCompanion()
                    if (!companion && !classOrObject.isLocal) consider(classOrObject)
                    super.visitClassOrObject(classOrObject)
                }

                override fun visitTypeAlias(typeAlias: KtTypeAlias) {
                    consider(typeAlias)
                    super.visitTypeAlias(typeAlias)
                }

                private fun consider(declaration: KtNamedDeclaration) {
                    if (declaration.name != null && isReportable(declaration)) result += declaration
                }
            },
        )
        return result
    }

    private fun isReportable(declaration: KtNamedDeclaration): Boolean {
        // Private code is detekt's (UnusedPrivate*), which also sees what this cannot.
        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) return false
        if (ENTRY_POINT_MODIFIERS.any { declaration.hasModifier(it) }) return false
        if (declaration.annotationNames().any { it in ENTRY_POINT_ANNOTATIONS || "Preview" in it }) return false
        // A serializer writes and reads these entries by name.
        if (declaration is KtEnumEntry && declaration.getStrictParentOfType<KtClass>()?.annotationNames()?.any { it in GENERATED_ACCESS_ANNOTATIONS } == true) return false
        if (declaration is KtProperty && declaration.name == "serialVersionUID") return false
        // Room and the serializers read these classes' properties themselves.
        if (declaration is KtProperty) {
            val owner = declaration.getStrictParentOfType<KtClassOrObject>()
            if (owner != null && owner.annotationNames().any { it in GENERATED_ACCESS_ANNOTATIONS }) return false
        }
        return !isSuppressed(declaration)
    }

    private fun isSuppressed(declaration: KtDeclaration): Boolean {
        val scopes = sequenceOf<PsiElement>(declaration) + declaration.parents.filter { it is KtDeclaration }
        if (scopes.any { it is KtAnnotated && it.suppressesUnused() }) return true
        return declaration.containingKtFile.fileAnnotationList?.annotationEntries.orEmpty().any { it.shortName?.asString() == "Suppress" && it.suppressesUnusedArgs() }
    }

    private fun KtAnnotated.suppressesUnused(): Boolean = annotationEntries.any { it.shortName?.asString() == "Suppress" && it.suppressesUnusedArgs() }

    private fun KtAnnotationEntry.suppressesUnusedArgs(): Boolean = collectDescendantsOfType<KtStringTemplateExpression>().any { arg ->
        val text = arg.entries.joinToString("") { it.text }
        text.equals("unused", ignoreCase = true) || text.startsWith("Unused")
    }

    private fun KtAnnotated.annotationNames(): List<String> = annotationEntries.mapNotNull { it.shortName?.asString() }

    private fun unusedCatchParameters(file: KtFile): List<KtNamedDeclaration> = file.collectDescendantsOfType<KtCatchClause>().mapNotNull { clause ->
        val parameter = clause.catchParameter ?: return@mapNotNull null
        val name = parameter.name ?: return@mapNotNull null
        if (name == "_") return@mapNotNull null
        val used = clause.catchBody?.collectDescendantsOfType<KtSimpleNameExpression>()?.any { it.getReferencedName() == name } == true
        if (used || parameter.annotationNames().contains("Suppress")) null else parameter
    }

    private fun kindOf(declaration: KtNamedDeclaration): String = when (declaration) {
        is KtEnumEntry -> "enum entry"
        is KtNamedFunction -> "function"
        is KtProperty -> "property"
        is KtObjectDeclaration -> "object"
        is KtClass -> if (declaration.isInterface()) "interface" else "class"
        is KtTypeAlias -> "typealias"
        else -> "declaration"
    }

    private fun lineOf(file: KtFile, element: PsiElement): Int {
        val offset = (element as? KtNamedDeclaration)?.nameIdentifier?.textOffset ?: element.textOffset
        return file.text.substring(0, offset).count { it == '\n' } + 1
    }

    private companion object {
        val ENTRY_POINT_MODIFIERS = listOf(KtTokens.OVERRIDE_KEYWORD, KtTokens.OPERATOR_KEYWORD, KtTokens.ACTUAL_KEYWORD, KtTokens.EXPECT_KEYWORD)

        // Called by a framework or a test runner, never by name.
        val ENTRY_POINT_ANNOTATIONS = setOf("Test", "Before", "After", "BeforeClass", "AfterClass", "TypeConverter", "JsonCreator", "JsonValue", "JsonProperty", "SerialName", "Keep")

        val GENERATED_ACCESS_ANNOTATIONS = setOf("Entity", "Serializable", "Fts4")

        val ENUM_LISTINGS = setOf("valueOf", "entries", "values")

        val ENUM_LISTING_FUNCTIONS = setOf("enumValueOf", "enumValues", "enumEntries")
    }
}
