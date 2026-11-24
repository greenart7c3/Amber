package com.greenart7c3.nostrsigner.tools.detekt

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(K1Deprecation::class, CompilerConfiguration.Internals::class, ExperimentalCompilerApi::class)
class CommentPolicyTest {
    private fun reported(source: String): List<String> = factory.createFile("Probe.kt", source.trimIndent())
        .collectDescendantsOfType<PsiComment>()
        .filterNot { CommentPolicy.isAllowed(it) }
        .map { CommentPolicy.firstLine(it.text) }

    @Test
    fun reportsLineAndBlockComments() {
        val source = """
            package probe

            fun f(): Int {
                // the answer
                /* still the answer */
                return 42
            }
        """
        assertEquals(listOf("the answer", "still the answer"), reported(source))
    }

    @Test
    fun keepsKDocOnPublicApiOnly() {
        val source = """
            package probe

            /** Public. */
            class Api {
                /** A public member. */
                fun member() = Unit

                /** A protected member. */
                protected fun forSubclasses() = Unit

                /** A private member. */
                private fun hidden() = Unit
            }

            /** Internal. */
            internal fun moduleOnly() = Unit

            private class Hidden {
                /** Public, in a private class. */
                fun member() = Unit
            }

            fun f() {
                /** A local function. */
                fun local() = Unit
            }
        """
        assertEquals(
            listOf("A private member.", "Internal.", "Public, in a private class.", "A local function."),
            reported(source),
        )
    }

    @Test
    fun keepsLicenseHeadersToolDirectivesAndConstraintLinks() {
        val source = """
            // Copyright 2026 Amber contributors, MIT license
            package probe

            //noinspection SpellCheckingInspection
            // ktlint-disable max-line-length
            // language=json
            // https://github.com/nostr-protocol/nips/blob/master/46.md says so
            // Works around https://issuetracker.google.com/issues/123456
            // Fixed upstream in https://github.com/vitorpamplona/quartz/issues/42
            // Copyright is not a header down here
            fun f() = Unit
        """
        assertEquals(listOf("Copyright is not a header down here"), reported(source))
    }

    @Test
    fun firstLineSkipsTheMarkersAndCollapsesWhitespace() {
        assertEquals("Says what it does.", CommentPolicy.firstLine("/**\n * Says   what\tit does.\n */"))
        assertEquals("a note", CommentPolicy.firstLine("//   a note"))
    }

    private companion object {
        val disposable = Disposer.newDisposable()
        val factory = KtPsiFactory(
            KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration().apply { extensionsStorage = CompilerPluginRegistrar.ExtensionStorage() },
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            ).project,
            markGenerated = false,
        )

        @JvmStatic
        @AfterClass
        fun dispose() = Disposer.dispose(disposable)
    }
}
