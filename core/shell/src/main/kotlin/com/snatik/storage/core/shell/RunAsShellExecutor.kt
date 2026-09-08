package com.snatik.storage.core.shell

/**
 * Runs commands as a debuggable package through `run-as`, which the shell uid is allowed to do.
 * This is how the app reaches the private data of apps you are developing without root.
 */
class RunAsShellExecutor(private val base: ShellExecutor, val packageName: String) : ShellExecutor {
    override val tier: PrivilegeTier get() = base.tier

    override suspend fun start(command: String): ShellProcess =
        base.start("run-as ${packageName.shellQuote()} sh -c ${command.shellQuote()}")
}
