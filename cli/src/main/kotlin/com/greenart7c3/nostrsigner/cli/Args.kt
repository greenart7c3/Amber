package com.greenart7c3.nostrsigner.cli

/**
 * One parsed command line: the command, its positional arguments and the flags
 * that appeared anywhere on the line (`auth sign-event --json` and
 * `auth --json sign-event` mean the same).
 */
class Args internal constructor(
    val command: String?,
    val positionals: List<String>,
    private val flags: Map<String, List<String>>,
) {
    fun value(name: String): String? = flags[name]?.lastOrNull()

    fun values(name: String): List<String> = flags[name].orEmpty()

    fun has(name: String): Boolean = name in flags

    companion object {
        private val VALUE_FLAGS = setOf("profile", "timeout", "relay", "name", "perms")

        private val BOOLEAN_FLAGS = setOf("json", "qr", "open", "force", "all", "help", "version", "verbose")

        fun parse(arguments: List<String>): Args {
            val positionals = mutableListOf<String>()
            val flags = mutableMapOf<String, MutableList<String>>()
            fun flag(name: String, value: String = "") {
                flags.getOrPut(name) { mutableListOf() }.add(value)
            }

            var index = 0
            while (index < arguments.size) {
                val argument = arguments[index]
                when {
                    argument == "--" -> {
                        positionals += arguments.drop(index + 1)
                        break
                    }

                    argument == "-h" -> flag("help")
                    argument == "-V" -> flag("version")
                    argument.startsWith("--") -> {
                        val name = argument.substring(2).substringBefore('=')
                        val inline = if ('=' in argument) argument.substringAfter('=') else null
                        val next = arguments.getOrNull(index + 1)
                        optionError(name, inline, next)?.let { throw CliError(it, Exit.USAGE) }
                        if (name in VALUE_FLAGS) {
                            if (inline == null) index++
                            flag(name, inline ?: next.orEmpty())
                        } else {
                            flag(name)
                        }
                    }

                    else -> positionals += argument
                }
                index++
            }
            return Args(positionals.firstOrNull(), positionals.drop(1), flags)
        }

        private fun optionError(
            name: String,
            inlineValue: String?,
            nextArgument: String?,
        ): String? = when (name) {
            in VALUE_FLAGS -> if (inlineValue == null && nextArgument == null) "--$name needs a value" else null
            in BOOLEAN_FLAGS -> if (inlineValue != null) "--$name takes no value" else null
            else -> "unknown option --$name (run `amber help`)"
        }
    }
}
