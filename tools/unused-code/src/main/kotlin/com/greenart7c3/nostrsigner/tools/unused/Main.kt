package com.greenart7c3.nostrsigner.tools.unused

import java.io.File
import kotlin.system.exitProcess

/**
 * `Main <root> <module>... [-- <token dir>...]`: analyzes every module's
 * `src/` tree, and reads identifiers from the [TOKEN_EXTENSIONS] files in the
 * modules and in the token directories (sources the JNI side calls by name).
 */
fun main(args: Array<String>) {
    val root = File(args.first())
    val split = args.indexOf("--").takeIf { it >= 0 } ?: args.size
    val modules = args.toList().subList(1, split).map { File(root, it) }
    val tokenDirs = args.toList().drop(split + 1).map { File(root, it) }

    val sources = modules.flatMap { module ->
        kotlinFiles(File(module, "src")).map { file ->
            val sourceSet = file.relativeTo(File(module, "src")).invariantSeparatorsPath.substringBefore('/')
            Source(file.relativeTo(root).invariantSeparatorsPath, file.readText(), declares = !isTestSourceSet(sourceSet))
        }
    }
    val tokens = (modules + tokenDirs).flatMap(::tokenFiles).flatMapTo(HashSet()) { IDENTIFIER.findAll(it.readText()).map { match -> match.value } }

    val findings = UnusedCodeAnalyzer().analyze(sources, tokens)
    findings.forEach(::println)
    if (findings.isNotEmpty()) {
        System.err.println("${findings.size} unused declarations or catch parameters. Remove them, name an unused catch parameter _, or add @Suppress(\"unused\") where something outside the sources uses it.")
        exitProcess(1)
    }
}

private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

private val TOKEN_EXTENSIONS = setOf("xml", "pro", "kts", "rs")

private fun isTestSourceSet(name: String) = name.startsWith("test") || name.startsWith("androidTest")

private fun walk(dir: File): Sequence<File> = dir.walkTopDown().onEnter { it.name != "build" && !it.name.startsWith(".") }.filter { it.isFile }

private fun kotlinFiles(dir: File): List<File> = walk(dir).filter { it.extension == "kt" }.sortedBy { it.path }.toList()

private fun tokenFiles(dir: File): List<File> = walk(dir).filter { it.extension in TOKEN_EXTENSIONS }.toList()
