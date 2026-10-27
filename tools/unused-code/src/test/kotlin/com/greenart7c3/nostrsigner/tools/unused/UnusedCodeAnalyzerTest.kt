package com.greenart7c3.nostrsigner.tools.unused

import org.junit.Assert.assertEquals
import org.junit.Test

class UnusedCodeAnalyzerTest {
    private fun findings(vararg sources: Source, tokens: Set<String> = emptySet()): List<String> = UnusedCodeAnalyzer().analyze(sources.toList(), tokens).map { it.message }

    private fun main(text: String) = Source("app/src/main/A.kt", text, declares = true)

    @Test
    fun reportsDeclarationsNothingReferences() {
        val result = findings(
            main(
                """
                class Used
                class Unused
                fun used() = Used()
                fun unused() {}
                val unusedProperty = used()
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("class 'Unused' is never used", "function 'unused' is never used", "property 'unusedProperty' is never used"), result)
    }

    @Test
    fun aReferenceFromATestOrAnotherFileIsAUse() {
        val result = findings(
            main("fun helper() {}\nfun other() {}"),
            Source("app/src/main/B.kt", "fun caller() = other()", declares = true),
            Source("app/src/test/T.kt", "fun t() { helper(); caller() }", declares = false),
        )
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun anIdentifierFromXmlOrNativeCodeIsAUse() {
        assertEquals(emptyList<String>(), findings(main("class MainActivity"), tokens = setOf("MainActivity")))
    }

    @Test
    fun entryPointsAndOptOutsAreNotReported() {
        val result = findings(
            Source("app/src/main/Kept.kt", "@file:Suppress(\"unused\")\n\nfun keptByFileSuppress() {}", declares = true),
            main(
                """
                interface Base { fun call() }
                class Impl : Base { override fun call() {} }
                private fun privateHelper() {}
                operator fun Impl.invoke() {}
                fun main() {}
                enum class Kind { ONE }
                class Holder { companion object }
                @ThemePreviews @Composable fun ScreenPreview() {}
                @Suppress("unused") fun keptForTheNdk() {}
                @Entity data class Row(val id: Int) { val extra: Int = 0 }
                fun use(b: Base) { b.call(); Impl(); Kind.ONE; Holder(); Row(0) }
                """.trimIndent(),
            ),
            Source("app/src/main/C.kt", "fun c() = use(Impl())", declares = false),
        )
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun reportsEnumEntriesUnlessTheEnumIsLookedUpByName() {
        val result = findings(
            main(
                """
                enum class Key { USED, UNUSED }
                enum class Parsed { ONE, TWO }
                enum class Listed { A, B; companion object { fun all() = entries } }
                fun use() { Key.USED; Parsed.valueOf("ONE"); Listed.all() }
                """.trimIndent(),
            ),
            Source("app/src/main/D.kt", "fun d() = use()", declares = false),
        )
        assertEquals(listOf("enum entry 'UNUSED' is never used"), result)
    }

    @Test
    fun reportsCatchParametersTheBlockNeverReads() {
        val result = findings(
            main(
                """
                fun f() {
                    try { f() } catch (e: Exception) { println("failed") }
                    try { f() } catch (e: Exception) { println(e) }
                    try { f() } catch (_: Exception) { }
                }
                fun g() = f()
                """.trimIndent(),
            ).let { Source(it.path, it.text, declares = false) },
        )
        assertEquals(listOf("catch parameter 'e' is never used; name it _"), result)
    }
}
