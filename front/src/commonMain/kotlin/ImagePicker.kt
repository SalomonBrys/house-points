/**
 * Bytes read from a user-picked image file, plus enough metadata for
 * [TeamsRepository.uploadImage] to submit it as a multipart upload.
 */
class PickedImage(val bytes: ByteArray, val fileName: String, val mimeType: String)

/**
 * Opens the platform's native file picker restricted to images (PNG/JPG/WebP,
 * `SPECS.md`), returning the picked file's bytes, or null if the user
 * cancelled. Used by [AdminScreen] to upload/replace a team's image.
 */
expect suspend fun pickImageFile(): PickedImage?
