package com.greenart7c3.nostrsigner.tools.detekt

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Which comments may stay: a license header above the package, a tool directive
 * (`noinspection`, `ktlint-disable`, `@formatter:`, `language=`), a link to an issue,
 * an RFC or a NIP, and KDoc on public or protected declarations of public classes.
 */
object CommentPolicy {
    private val toolDirectives = listOf("noinspection", "ktlint-disable", "ktlint-enable", "@formatter:", "language=")

    private val constraintLinks = Regex(
        "https?://(" +
            "github\\.com/[^/\\s]+/[^/\\s]+/(issues|pull|discussions)/\\d+" +
            "|github\\.com/nostr-protocol/nips" +
            "|issuetracker\\.google\\.com/" +
            "|youtrack\\.jetbrains\\.com/issue/" +
            "|(www\\.)?rfc-editor\\.org/" +
            "|datatracker\\.ietf\\.org/doc/" +
            ")",
    )

    private val licenseWords = Regex("(?i)\\b(copyright|license|spdx)\\b")

    fun isAllowed(comment: PsiComment): Boolean {
        val content = content(comment.text)
        return toolDirectives.any { content.startsWith(it) } ||
            constraintLinks.containsMatchIn(comment.text) ||
            isLicenseHeader(comment) ||
            isPublicApiDoc(comment)
    }

    /** The comment's first line of text, without its markers and with its whitespace collapsed. */
    fun firstLine(text: String): String = text.lines()
        .map { content(it) }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .take(MAX_FIRST_LINE)

    private fun content(text: String): String = text.trim()
        .removePrefix("/**")
        .removePrefix("/*")
        .removePrefix("//")
        .removeSuffix("*/")
        .trim()
        .removePrefix("*")
        .trim()

    private fun isLicenseHeader(comment: PsiComment): Boolean {
        val file = comment.containingFile as? KtFile ?: return false
        val packageStart = file.packageDirective?.takeIf { it.textLength > 0 }?.textOffset ?: file.declarations.firstOrNull()?.textOffset ?: Int.MAX_VALUE
        return comment.textOffset < packageStart && licenseWords.containsMatchIn(comment.text)
    }

    private fun isPublicApiDoc(comment: PsiComment): Boolean {
        if (comment !is KDoc) return false
        val owner = comment.getParentOfType<KtDeclaration>(strict = true)?.takeIf { it.docComment == comment } ?: return false
        return isVisibleOutside(owner) && owner.parents.filterIsInstance<KtClassOrObject>().all { isVisibleOutside(it) }
    }

    private fun isVisibleOutside(declaration: KtDeclaration): Boolean = !KtPsiUtil.isLocal(declaration) &&
        !declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
        !declaration.hasModifier(KtTokens.INTERNAL_KEYWORD)

    private const val MAX_FIRST_LINE = 100
}
