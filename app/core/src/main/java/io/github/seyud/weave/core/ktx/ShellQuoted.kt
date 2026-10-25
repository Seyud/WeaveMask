package io.github.seyud.weave.core.ktx

/** Quote a string as a single shell word, so it is never interpreted by the shell. */
fun String.shellQuoted() = "'" + replace("'", "'\\''") + "'"
