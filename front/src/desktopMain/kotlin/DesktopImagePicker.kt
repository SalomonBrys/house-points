import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Dev-only Compose Hot-Reload harness picker (`front/ARCHITECTURE.md §2`,
 * not a shipped target) — a plain blocking `JFileChooser`, run off the main
 * thread since it blocks until the user responds.
 */
actual suspend fun pickImageFile(): PickedImage? = withContext(Dispatchers.IO) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("Images (PNG, JPG, WebP)", "png", "jpg", "jpeg", "webp")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null

    val file: File = chooser.selectedFile
    PickedImage(file.readBytes(), file.name, mimeTypeFor(file.extension))
}

private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> "application/octet-stream"
}
