@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import kotlinx.browser.document
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.coroutines.resume

private const val ACCEPTED_IMAGE_TYPES = "image/png,image/jpeg,image/webp"

/**
 * A hidden, throwaway `<input type="file">` triggered programmatically —
 * Compose for Web has no built-in file-picker API, so this is the standard
 * way to reach the browser's native picker from Kotlin/Wasm & Kotlin/JS
 * (shared here since both compile `webMain`, per `front/ARCHITECTURE.md §3`).
 */
actual suspend fun pickImageFile(): PickedImage? = suspendCancellableCoroutine { continuation ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ACCEPTED_IMAGE_TYPES

    // `once = true`: the DOM guarantees this fires at most once, so the
    // continuation can never be resumed twice even if "change" somehow
    // dispatched again for the same input.
    input.addEventListener(
        "change",
        { _: Event ->
            val file = input.files?.get(0)
            if (file == null) {
                continuation.resume(null)
            } else {
                readFileBytes(file, continuation)
            }
        },
        AddEventListenerOptions(once = true),
    )

    // There's no reliable "cancel" event for <input type=file> across
    // browsers, so a dismissed picker just never resumes the continuation —
    // fine here since the caller (AdminScreen) has no pending state to unwind
    // while it's open.
    input.click()
}

private fun readFileBytes(file: File, continuation: CancellableContinuation<PickedImage?>) {
    val reader = FileReader()
    reader.onload = { _: Event ->
        val bytes = (reader.result as ArrayBuffer).toByteArray()
        continuation.resume(PickedImage(bytes, file.name, file.type))
    }
    reader.onerror = { _: Event -> continuation.resume(null) }
    reader.readAsArrayBuffer(file)
}

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
