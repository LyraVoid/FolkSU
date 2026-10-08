package me.weishu.kernelsu.wallpaper

import java.io.File
import java.io.IOException

/** Stage in the destination directory, then atomically replace the file without losing the old one. */
internal fun replaceSurfaceImage(
    directory: File,
    stem: String,
    extension: String,
    copy: (File) -> Boolean,
): File {
    val staged = File.createTempFile("${stem}_", ".tmp", directory)
    try {
        if (!copy(staged) || staged.length() == 0L) throw IOException("Empty surface image")
        val target = File(directory, "$stem$extension")
        if (!staged.renameTo(target)) throw IOException("Cannot replace surface image")
        return target
    } finally {
        staged.delete()
    }
}
