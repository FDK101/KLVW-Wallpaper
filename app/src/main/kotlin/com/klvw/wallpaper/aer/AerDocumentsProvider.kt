package com.klvw.wallpaper.aer

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Binder
import android.os.CancellationSignal
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.klvw.wallpaper.R
import java.io.File
import java.io.FileNotFoundException

class AerDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.klvw.wallpaper.aer"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        private const val OBSERVER_MASK =
            FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
    }

    // Cursor subclass that watches its directory with a FileObserver and notifies
    // DocumentsUI when the filesystem changes — same pattern as anemo-aer DirectoryCursor.
    private inner class DirectoryCursor(
        columnNames: Array<out String>,
        docId: String,
        dir: File
    ) : MatrixCursor(columnNames) {

        private val notifyUri = DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId)

        private val observer = object : FileObserver(dir, OBSERVER_MASK) {
            override fun onEvent(event: Int, path: String?) {
                context?.contentResolver?.notifyChange(notifyUri, null)
            }
        }

        init {
            setNotificationUri(context!!.contentResolver, notifyUri)
            observer.startWatching()
        }

        override fun close() {
            observer.stopWatching()
            super.close()
        }
    }

    private val onLockChanged: (Boolean) -> Unit = { _ ->
        context?.contentResolver?.notifyChange(
            DocumentsContract.buildRootsUri(AUTHORITY), null
        )
    }

    override fun onCreate(): Boolean {
        AerLockStore.init(context!!)
        AerLockStore.addListener(onLockChanged)
        return true
    }

    override fun shutdown() {
        AerLockStore.removeListener(onLockChanged)
    }

    private val aerRoot: File
        get() = File(context!!.filesDir, "aer").also { it.mkdirs() }.canonicalFile

    private fun isCallerInternal(): Boolean = Binder.getCallingUid() == Process.myUid()

    private fun toFile(docId: String): File? {
        val root = aerRoot
        if (docId == "root") return root
        val resolved = File(root, docId).canonicalFile
        return if (resolved.path == root.path || resolved.path.startsWith(root.path + "/")) resolved else null
    }

    private fun toDocId(file: File): String {
        val root = aerRoot
        val canon = file.canonicalFile
        return if (canon.path == root.path) "root"
        else canon.relativeTo(root).path.replace('\\', '/')
    }

    private fun mimeOf(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "mkv" -> "video/x-matroska"
                "flv" -> "video/x-flv"
                "ts", "mts", "m2ts" -> "video/mp2t"
                "heic", "heif" -> "image/heic"
                else -> "application/octet-stream"
            }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_ROOT_PROJECTION
        if (!isCallerInternal() && AerLockStore.isLocked) {
            return MatrixCursor(cols).also {
                it.setNotificationUri(
                    context!!.contentResolver,
                    DocumentsContract.buildRootsUri(AUTHORITY)
                )
            }
        }
        return MatrixCursor(cols).also { result ->
            result.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, "aer")
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root")
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, "image/*\nvideo/*")
                add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                    DocumentsContract.Root.FLAG_SUPPORTS_EJECT)
                add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(DocumentsContract.Root.COLUMN_TITLE, "KLVW Aer")
                add(DocumentsContract.Root.COLUMN_SUMMARY, "Private media storage")
            }
            result.setNotificationUri(
                context!!.contentResolver,
                DocumentsContract.buildRootsUri(AUTHORITY)
            )
        }
    }

    override fun ejectRoot(rootId: String) {
        if (rootId == "aer") AerLockStore.lock(context!!)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = toFile(parentDocumentId) ?: return false
        val child = toFile(documentId) ?: return false
        return child.canonicalPath.startsWith(parent.canonicalPath + "/")
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        if (!isCallerInternal() && AerLockStore.isLocked) return MatrixCursor(cols)
        val result = MatrixCursor(cols)
        val docId = documentId ?: return result
        val file = toFile(docId) ?: return result
        if (file.isDirectory) addDirRow(result, file, docId)
        else if (file.exists()) addFileRow(result, file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val parentDocId = parentDocumentId ?: return MatrixCursor(cols)
        if (!isCallerInternal() && AerLockStore.isLocked) {
            return MatrixCursor(cols).also {
                it.setNotificationUri(
                    context!!.contentResolver,
                    DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocId)
                )
            }
        }
        val parentFile = toFile(parentDocId) ?: return MatrixCursor(cols)
        return DirectoryCursor(cols, parentDocId, parentFile).also { result ->
            parentFile.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?.forEach { child ->
                    if (child.isDirectory) addDirRow(result, child)
                    else if (child.isFile) addFileRow(result, child)
                }
        }
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (!isCallerInternal() && AerLockStore.isLocked) {
            val intent = Intent(context, AerMountActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            throw AuthenticationRequiredException(
                Throwable("Aer storage is locked"),
                PendingIntent.getActivity(context!!, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        }
        val docId = documentId ?: throw IllegalArgumentException("null documentId")
        val file = toFile(docId) ?: throw FileNotFoundException("Unknown document: $docId")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openDocumentThumbnail(
        documentId: String?,
        sizeHint: android.graphics.Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor {
        val docId = documentId ?: throw IllegalArgumentException("null documentId")
        if (!isCallerInternal() && AerLockStore.isLocked) {
            throw FileNotFoundException("Aer vault is locked: $docId")
        }
        val file = toFile(docId) ?: throw FileNotFoundException("Unknown document: $docId")

        if (mimeOf(file).startsWith("video/")) {
            val bitmap = try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            } catch (_: Exception) { null }

            if (bitmap != null) {
                val pipe = ParcelFileDescriptor.createPipe()
                Thread {
                    try {
                        java.io.FileOutputStream(pipe[1].fileDescriptor).buffered().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    } catch (_: Exception) {
                        // Reader closed the pipe early (e.g. thumbnail cache already had it) — not an error
                    } finally {
                        try { pipe[1].close() } catch (_: Exception) {}
                    }
                }.start()
                return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }
        }

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = toFile(documentId) ?: throw FileNotFoundException("Unknown document: $documentId")
        val sanitized = displayName.trim().replace("/", "_").replace(" ", "")
        if (sanitized.isBlank()) throw IllegalArgumentException("Invalid name")
        val target = File(file.parentFile!!, sanitized)
        if (!file.renameTo(target)) throw FileNotFoundException("Rename failed for: $documentId")
        val parentDocId = if (target.parentFile?.canonicalPath == aerRoot.canonicalPath) "root"
                          else toDocId(target.parentFile!!)
        context?.contentResolver?.notifyChange(
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocId), null
        )
        return toDocId(target)
    }

    private fun addDirRow(cursor: MatrixCursor, file: File, docId: String? = null) {
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId ?: toDocId(file))
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_RENAME)
            add(DocumentsContract.Document.COLUMN_SIZE, null)
        }
    }

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        val mime = mimeOf(file)
        val flags = DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
            (if (mime.startsWith("image/") || mime.startsWith("video/"))
                DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL else 0)
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, toDocId(file))
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mime)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        }
    }
}
