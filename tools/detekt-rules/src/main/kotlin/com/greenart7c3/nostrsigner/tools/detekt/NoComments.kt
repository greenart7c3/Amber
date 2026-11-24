package com.greenart7c3.nostrsigner.tools.detekt

import com.intellij.psi.PsiComment
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Location
import dev.detekt.api.Rule
import java.net.URI
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Reports comments the code could say instead, through names, types or structure;
 * [CommentPolicy] lists the ones that may stay. A comment about a constraint outside
 * this code goes under `@Suppress("NoComments")` on its declaration.
 */
class NoComments(config: Config) :
    Rule(
        config,
        "Say it through names, types or structure instead of a comment.",
        URI("https://github.com/greenart7c3/Amber/blob/master/AGENTS.md#build-and-validation-commands"),
    ) {
    override fun visit(root: KtFile) {
        super.visit(root)
        root.collectDescendantsOfType<PsiComment>()
            .filterNot { CommentPolicy.isAllowed(it) }
            .forEach { report(Finding(entity(root, it), MESSAGE)) }
    }

    private fun entity(file: KtFile, comment: PsiComment) = Entity(
        CommentPolicy.firstLine(comment.text),
        Location.from(comment),
        comment.getParentOfType<KtElement>(strict = true) ?: file,
    )

    private companion object {
        const val MESSAGE = "Comment: say it through names, types or structure, or delete it. " +
            "Allowed: public API KDoc, license headers, tool directives and issue, RFC or NIP links; " +
            "@Suppress(\"NoComments\") marks a constraint outside this code."
    }
}
