package com.cyberscan.app.core.shell

sealed interface CommandEnvironment {
    data object AndroidRoot : CommandEnvironment

    data class Chroot(val root: String) : CommandEnvironment {
        init {
            require(root.startsWith('/') && root.none(Char::isWhitespace)) {
                "Invalid chroot path"
            }
        }
    }
}

internal fun CommandEnvironment.render(payload: String): String = when (this) {
    CommandEnvironment.AndroidRoot -> payload
    is CommandEnvironment.Chroot -> "chroot ${shellWord(root)} /bin/bash -lc ${shellWord(payload)}"
}

