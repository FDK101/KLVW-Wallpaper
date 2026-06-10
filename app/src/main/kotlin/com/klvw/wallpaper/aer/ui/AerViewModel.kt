package com.klvw.wallpaper.aer.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klvw.wallpaper.aer.AerDocumentsProvider
import com.klvw.wallpaper.aer.AerLockStore
import com.klvw.wallpaper.aer.AerMountActivity
import com.klvw.wallpaper.data.repository.StaticImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface AerItem {
    val id: String
    val name: String

    data class Folder(override val name: String, val relPath: String) : AerItem {
        override val id = "folder:$relPath"
    }

    data class MediaFile(
        override val name: String,
        val relPath: String,
        val mimeType: String
    ) : AerItem {
        override val id = "file:$relPath"
        val isVideo get() = mimeType.startsWith("video/")
    }
}

data class AerUiState(
    val currentRelPath: String? = null,
    val pathComponents: List<String> = emptyList(),
    val items: List<AerItem> = emptyList(),
    val importing: Boolean = false,
    val showCreateFolder: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isMounted: Boolean = false,
    val staticAdded: Int = 0,
    val renameTarget: AerItem? = null,
)

@HiltViewModel
class AerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val staticImageRepository: StaticImageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AerUiState(isMounted = AerLockStore.isMounted))
    val uiState: StateFlow<AerUiState> = _uiState.asStateFlow()

    val aerRoot: File get() = File(context.filesDir, "aer").also { it.mkdirs() }

    fun fileOf(relPath: String) = File(aerRoot, relPath)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            migrateDotPrefixedFiles(aerRoot)
            doRefresh()
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { doRefresh() }
    }

    private fun doRefresh() {
        val dir = currentDir()
        val items = buildList {
            dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.forEach { f ->
                    when {
                        f.isDirectory -> {
                            val rel = f.relativeTo(aerRoot).path.replace('\\', '/')
                            add(AerItem.Folder(f.name, rel))
                        }
                        f.isFile -> {
                            val mime = mimeOf(f)
                            if (mime.startsWith("image/") || mime.startsWith("video/")) {
                                val rel = f.relativeTo(aerRoot).path.replace('\\', '/')
                                add(AerItem.MediaFile(f.name, rel, mime))
                            }
                        }
                    }
                }
        }
        _uiState.update { it.copy(items = items, isMounted = AerLockStore.isMounted) }
    }

    // Renames dot-prefixed user files/dirs to non-dot names so they are visible
    // to external file browsers and the Aer browser. Skips known system entries.
    private fun migrateDotPrefixedFiles(dir: File) {
        val systemNames = setOf(".nomedia", ".thumbnails", ".DS_Store", ".android_secure")
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(".")) {
                if (file.name in systemNames) return@forEach
                val cleanName = file.name.trimStart('.').ifEmpty { "hidden_${file.name.hashCode()}" }
                val target = File(file.parent!!, cleanName)
                if (!target.exists()) {
                    file.renameTo(target)
                    if (target.isDirectory) migrateDotPrefixedFiles(target)
                }
                // Target name already taken — leave as is rather than risk overwriting
            } else if (file.isDirectory) {
                migrateDotPrefixedFiles(file)
            }
        }
    }

    fun navigate(folder: AerItem.Folder) {
        _uiState.update { state ->
            state.copy(
                currentRelPath = folder.relPath,
                pathComponents = state.pathComponents + folder.name,
                selectedIds = emptySet()
            )
        }
        refresh()
    }

    fun navigateBack() {
        _uiState.update { state ->
            val newComponents = state.pathComponents.dropLast(1)
            val newRelPath = newComponents.joinToString("/").ifEmpty { null }
            state.copy(
                currentRelPath = newRelPath,
                pathComponents = newComponents,
                selectedIds = emptySet()
            )
        }
        refresh()
    }

    fun showCreateFolderDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateFolder = show) }
    }

    fun createFolder(name: String) {
        File(currentDir(), name.trim()).mkdirs()
        refresh()
    }

    fun importFiles(uris: List<Uri>, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true) }
            withContext(Dispatchers.IO) {
                val dir = currentDir()
                uris.forEach { uri ->
                    val rawName = queryDisplayName(contentResolver, uri)
                        ?: uri.lastPathSegment?.substringAfterLast('/')
                        ?: "file"
                    val name = sanitizeName(rawName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(dir, name).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            _uiState.update { it.copy(importing = false) }
            refresh()
        }
    }

    fun importFolder(treeUri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true) }
            withContext(Dispatchers.IO) {
                runCatching { copyDocumentTree(treeUri, currentDir(), contentResolver) }
            }
            _uiState.update { it.copy(importing = false) }
            refresh()
        }
    }

    private fun copyDocumentTree(treeUri: Uri, destDir: File, contentResolver: ContentResolver) {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val folderName = run {
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            contentResolver.query(docUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: treeUri.lastPathSegment ?: "Folder"
        }
        val targetDir = File(destDir, folderName).also { it.mkdirs() }
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        contentResolver.query(childrenUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(0) ?: continue
                val mime = cursor.getString(1) ?: continue
                val name = sanitizeName(cursor.getString(2) ?: childDocId)
                val fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                when {
                    mime.startsWith("image/") || mime.startsWith("video/") -> {
                        contentResolver.openInputStream(fileUri)?.use { input ->
                            File(targetDir, name).outputStream().use { input.copyTo(it) }
                        }
                    }
                    mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR -> {
                        val subTreeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
                            treeUri.authority ?: return@use, childDocId
                        )
                        val childDir = File(targetDir, name).also { it.mkdirs() }
                        copySubTree(treeUri, childDocId, childDir, contentResolver)
                    }
                }
            }
        }
    }

    private fun copySubTree(treeUri: Uri, parentDocId: String, destDir: File, contentResolver: ContentResolver) {
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        contentResolver.query(childrenUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(0) ?: continue
                val mime = cursor.getString(1) ?: continue
                val name = sanitizeName(cursor.getString(2) ?: childDocId)
                val fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                when {
                    mime.startsWith("image/") || mime.startsWith("video/") ->
                        contentResolver.openInputStream(fileUri)?.use { input ->
                            File(destDir, name).outputStream().use { input.copyTo(it) }
                        }
                    mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR ->
                        copySubTree(treeUri, childDocId, File(destDir, name).also { it.mkdirs() }, contentResolver)
                }
            }
        }
    }

    fun toggleSelection(itemId: String) {
        _uiState.update { state ->
            val updated = if (itemId in state.selectedIds) state.selectedIds - itemId
                          else state.selectedIds + itemId
            state.copy(selectedIds = updated)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun showRenameDialog(item: AerItem) {
        _uiState.update { it.copy(renameTarget = item) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(renameTarget = null) }
    }

    fun renameItem(item: AerItem, newName: String) {
        val trimmed = newName.trim().replace("/", "_")
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = when (item) {
                is AerItem.Folder    -> File(aerRoot, item.relPath)
                is AerItem.MediaFile -> File(aerRoot, item.relPath)
            }
            val target = File(file.parentFile!!, trimmed)
            file.renameTo(target)
            _uiState.update { it.copy(selectedIds = emptySet(), renameTarget = null) }
            doRefresh()
        }
    }

    fun deleteSelected() {
        val selected = _uiState.value.selectedIds
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.items.filter { it.id in selected }.forEach { item ->
                when (item) {
                    is AerItem.Folder -> File(aerRoot, item.relPath).deleteRecursively()
                    is AerItem.MediaFile -> File(aerRoot, item.relPath).delete()
                }
            }
            _uiState.update { it.copy(selectedIds = emptySet()) }
            refresh()
        }
    }

    fun addSelectedAsStaticWallpapers() {
        val selectedImages = _uiState.value.items
            .filterIsInstance<AerItem.MediaFile>()
            .filter { !it.isVideo && it.id in _uiState.value.selectedIds }
        if (selectedImages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            selectedImages.forEach { item ->
                val uri = DocumentsContract.buildDocumentUri(AerDocumentsProvider.AUTHORITY, item.relPath)
                staticImageRepository.add(uri, item.name)
            }
            _uiState.update { it.copy(selectedIds = emptySet(), staticAdded = selectedImages.size) }
        }
    }

    fun clearStaticAdded() {
        _uiState.update { it.copy(staticAdded = 0) }
    }

    fun mount(context: Context) {
        context.startActivity(
            Intent(context, AerMountActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun unmount() {
        AerLockStore.unmount(context)
        _uiState.update { it.copy(isMounted = false) }
    }

    fun checkMountState() {
        _uiState.update { it.copy(isMounted = AerLockStore.isMounted) }
    }

    private fun currentDir(): File {
        val rel = _uiState.value.currentRelPath
        return if (rel.isNullOrEmpty()) aerRoot else File(aerRoot, rel)
    }

    private fun sanitizeName(raw: String): String = raw.trimStart('.').ifEmpty { raw.replace(".", "_") }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
