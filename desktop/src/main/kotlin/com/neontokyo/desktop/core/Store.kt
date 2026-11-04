package com.neontokyo.desktop.core

import java.io.File

/** Small files in the user's AppData folder, in the core's JSON format (the same as Apple's). */
object Store {
    private val dir: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, "NeonTokyo").apply { mkdirs() }
    }

    fun read(name: String): String? = File(dir, "$name.json").takeIf { it.exists() }?.readText()

    fun write(name: String, text: String) {
        val tmp = File(dir, "$name.json.tmp")
        tmp.writeText(text)
        val target = File(dir, "$name.json")
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun lines(name: String): List<String>? = read(name)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
    fun writeLines(name: String, lines: List<String>) = write(name, lines.joinToString("\n"))
}
