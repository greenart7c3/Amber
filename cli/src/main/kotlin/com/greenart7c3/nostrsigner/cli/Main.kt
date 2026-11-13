package com.greenart7c3.nostrsigner.cli

import kotlin.system.exitProcess

fun main(arguments: Array<String>) {
    exitProcess(AmberCli.run(arguments.toList()))
}
