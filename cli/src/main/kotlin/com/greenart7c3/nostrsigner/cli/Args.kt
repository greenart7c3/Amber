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
        /** Flags that take a value, either `--flag value` or `--flag=value`. */
        private val VALUE_FLAGS = setOf("profile", "timeout", "relay", "name", "perms")

        /** Flags that stand alone. */
        private val BOOLEAN_FLAGS = setOf("json", "qr", "open", "force", "help", "version", "verbose")

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
                        when {
                            name in VALUE_FLAGS -> {
                                val value = inline
                                    ?: arguments.getOrNull(index + 1)
                                    ?: throw CliError("--$name needs a value", Exit.USAGE)
                                if (inline == null) index++
                                flag(name, value)
                            }

                            name in BOOLEAN_FLAGS -> {
                                if (inline != null) throw CliError("--$name takes no value", Exit.USAGE)
                                flag(name)
                            }

                            else -> throw CliError("unknown option --$name (run `amber help`)", Exit.USAGE)
                        }
                    }

                    else -> positionals += argument
                }
                index++
            }
            return Args(positionals.firstOrNull(), positionals.drop(1), flags)
        }
    }
}
