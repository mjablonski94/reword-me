package dev.mjablonski.rewordme.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * "Launch at login" via the per-user Run key - no elevation, and the user can
 * see and remove it from Task Manager's Startup tab.
 */
object StartupRegistration {
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "RewordMe"

    /**
     * False during development, where the running command is the JDK launcher:
     * registering that would put a bare java.exe in the user's startup list.
     */
    val isSupported: Boolean
        get() = isWindows && launchCommand() != null

    var isEnabled: Boolean
        get() = isWindows && runCatching {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
        }.getOrDefault(false)
        set(enabled) {
            val command = launchCommand() ?: return
            runCatching {
                if (enabled) {
                    Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME, "\"$command\""
                    )
                } else if (isEnabled) {
                    Advapi32Util.registryDeleteValue(
                        WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME
                    )
                }
            }
        }

    private fun launchCommand(): String? = ProcessHandle.current().info().command()
        .orElse(null)
        ?.takeUnless { it.endsWith("java.exe") || it.endsWith("javaw.exe") }
}
