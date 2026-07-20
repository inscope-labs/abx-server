package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

object SafUtils {
    private const val TAG = "SafUtils"

    fun Context.readTextFromUri(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            return inputStream.bufferedReader().readText()
        }
        throw SecurityException("Cannot read URI: $uri")
    }

    fun Context.writeTextToUri(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().write(text)
            return
        }
        throw SecurityException("Cannot write to URI: $uri")
    }

    fun Context.getDocumentFile(uri: Uri): DocumentFile? {
        return if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(this, uri)
        } else {
            DocumentFile.fromSingleUri(this, uri)
        }
    }

    fun Context.listFiles(uri: Uri): List<DocumentFile> {
        val doc = getDocumentFile(uri) ?: return emptyList()
        return doc.listFiles().toList()
    }

    fun Context.getFileName(uri: Uri): String {
        val doc = getDocumentFile(uri)
        return doc?.name ?: uri.lastPathSegment ?: "unknown"
    }

    fun Context.getFileSize(uri: Uri): Long {
        val doc = getDocumentFile(uri)
        if (doc != null && doc.exists()) {
            return doc.length()
        }
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
            } else 0L
        } ?: 0L
    }

    fun Context.getMimeType(uri: Uri): String? {
        val doc = getDocumentFile(uri)
        if (doc != null && doc.exists()) {
            return doc.type
        }
        return contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            uri.lastPathSegment?.substringAfterLast('.') ?: ""
        )
    }

    fun Context.isDirectory(uri: Uri): Boolean {
        val doc = getDocumentFile(uri)
        return doc?.isDirectory ?: false
    }

    fun Context.exists(uri: Uri): Boolean {
        val doc = getDocumentFile(uri)
        return doc?.exists() ?: false
    }

    // Normal methods to support SafUtils.createFileInTree(context, ...)
    fun createFileInTree(context: Context, treeUri: Uri, mimeType: String, fileName: String): Uri? {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val newFile = doc.createFile(mimeType, fileName) ?: return null
        return newFile.uri
    }

    fun createDirectoryInTree(context: Context, treeUri: Uri, dirName: String): Uri? {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val newDir = doc.createDirectory(dirName) ?: return null
        return newDir.uri
    }

    fun flattenSelection(
        context: Context,
        selection: ContextSelection,
        maxDirFiles: Int,
        forceAll: Boolean,
        auditLogger: AuditLogger? = null
    ): List<Triple<Uri, String, Long>> {
        val result = mutableListOf<Triple<Uri, String, Long>>()

        fun traverse(doc: DocumentFile, currentPath: String) {
            if (doc.isDirectory) {
                val children = doc.listFiles()
                if (!forceAll && children.size > maxDirFiles) {
                    auditLogger?.audit("dir-skipped", mapOf(
                        "dir" to currentPath,
                        "fileCount" to children.size,
                        "limit" to maxDirFiles
                    ))
                    return
                }
                for (child in children) {
                    val childPath = if (currentPath.isEmpty()) (child.name ?: "unknown") else "$currentPath/${child.name ?: "unknown"}"
                    traverse(child, childPath)
                }
            } else {
                result.add(Triple(doc.uri, currentPath, doc.length()))
            }
        }

        for (item in selection.items) {
            val path = item.displayName
            val doc = with(context) { context.getDocumentFile(item.uri) } ?: continue
            traverse(doc, path)
        }
        return result
    }
}
