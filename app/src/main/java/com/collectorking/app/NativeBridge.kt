package com.collectorking.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import android.util.Base64

/**
 * JavaScript bridge interface for communication between WebView and native Android.
 * Provides native capabilities like camera, album, file sharing, and app info.
 */
class NativeBridge(private val activity: Activity) {

    /**
     * Show a native toast message.
     * Called from JS: NativeBridge.showToast("message", "short"|"long")
     */
    @JavascriptInterface
    fun showToast(message: String, duration: String) {
        val len = if (duration == "long") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        activity.runOnUiThread {
            Toast.makeText(activity, message, len).show()
        }
    }

    /**
     * Get app version info as JSON string.
     * Returns: {"versionName":"1.1.0","versionCode":2,"appName":"收集王","sdkVersion":36}
     */
    @JavascriptInterface
    fun getAppInfo(): String {
        return try {
            val pm = activity.packageManager
            val info = pm.getPackageInfo(activity.packageName, 0)
            JSONObject().apply {
                put("versionName", info.versionName ?: "1.1.0")
                put("versionCode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") info.versionCode
                })
                put("appName", "收集王")
                put("packageName", activity.packageName)
                put("sdkVersion", Build.VERSION.SDK_INT)
                put("deviceModel", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("error", e.message) }.toString()
        }
    }

    /**
     * Open camera to take a photo for a specific document type and photo index.
     * Called from JS: NativeBridge.openCamera("身份证", 0)
     * Result callback: window.onCameraResult(docType, photoIndex, base64Data)
     */
    @JavascriptInterface
    fun openCamera(docType: String, photoIndex: Int) {
        activity.runOnUiThread {
            (activity as? MainActivity)?.launchCamera(docType, photoIndex)
        }
    }

    /**
     * Open album to pick a single photo for a specific document type and photo index.
     * Called from JS: NativeBridge.openAlbum("身份证", 0)
     * Result callback: window.onAlbumResult(docType, photoIndex, base64Data)
     */
    @JavascriptInterface
    fun openAlbum(docType: String, photoIndex: Int) {
        activity.runOnUiThread {
            (activity as? MainActivity)?.launchAlbum(docType, photoIndex)
        }
    }

    /**
     * Open album to pick multiple photos for a document type.
     * Called from JS: NativeBridge.openAlbumMulti("身份证")
     * Result callback: window.onAlbumMultiResult(docType, base64JsonArray)
     */
    @JavascriptInterface
    fun openAlbumMulti(docType: String) {
        activity.runOnUiThread {
            (activity as? MainActivity)?.launchAlbumMulti(docType)
        }
    }

    /**
     * Open album to pick a single image for background customization.
     * Called from JS: NativeBridge.pickImage()
     * Result callback: window.onPickImageResult(base64Data)
     */
    @JavascriptInterface
    fun pickImage() {
        activity.runOnUiThread {
            (activity as? MainActivity)?.launchPickImage()
        }
    }

    /**
     * Share a file using Android's share sheet.
     */
    @JavascriptInterface
    fun shareFile(filePath: String, mimeType: String, title: String) {
        activity.runOnUiThread {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Toast.makeText(activity, "文件不存在", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, title))
            } catch (e: Exception) {
                Toast.makeText(activity, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Create a ZIP package from photos and share it.
     * Called from JS: NativeBridge.createAndSharePackage(packageName, photosJson)
     * photosJson format: {"证件类型1":["base64_1","base64_2"], "证件类型2":["base64_3"]}
     * Returns: window.onPackageCreated(filePath, error) callback
     */
    @JavascriptInterface
    fun createAndSharePackage(packageName: String, photosJson: String, namingMode: String, packMode: String) {
        activity.runOnUiThread {
            try {
                (activity as? MainActivity)?.createAndSharePackage(packageName, photosJson, namingMode, packMode)
            } catch (e: Exception) {
                Toast.makeText(activity, "打包失败: ${e.message}", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.callJsFunction(
                    "window.onPackageCreated && window.onPackageCreated('', '${e.message?.replace("'", "\\'") ?: "unknown"}')"
                )
            }
        }
    }

    /**
     * Re-share an existing package file.
     * Called from JS: NativeBridge.reSharePackage(filePath)
     */
    @JavascriptInterface
    fun reSharePackage(filePath: String) {
        activity.runOnUiThread {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Toast.makeText(activity, "文件已不存在，无法重新分享", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "分享打包文件"))
            } catch (e: Exception) {
                Toast.makeText(activity, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Share text content using Android's share sheet.
     */
    @JavascriptInterface
    fun shareText(text: String, title: String) {
        activity.runOnUiThread {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            activity.startActivity(Intent.createChooser(shareIntent, title))
        }
    }

    /**
     * Get the app's external files directory for storing photos.
     */
    @JavascriptInterface
    fun getStoragePath(): String {
        return activity.getExternalFilesDir(null)?.absolutePath ?: ""
    }

    /**
     * Check if a file exists at the given path.
     */
    @JavascriptInterface
    fun fileExists(path: String): Boolean = File(path).exists()

    /**
     * Delete a file at the given path.
     */
    @JavascriptInterface
    fun deleteFile(path: String): Boolean = try { File(path).delete() } catch (e: Exception) { false }

    /**
     * Check if the system is currently in dark mode.
     * Called from JS: NativeBridge.isSystemDarkMode()
     * Used for reliable dark mode detection on HyperOS and other ROMs where matchMedia doesn't work.
     */
    @JavascriptInterface
    fun isSystemDarkMode(): Boolean {
        val uiMode = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Vibrate the device briefly for haptic feedback.
     */
    @JavascriptInterface
    fun hapticFeedback() {
        activity.runOnUiThread {
            activity.window.decorView.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP
            )
        }
    }

    /**
     * Open file picker to import a text file.
     * Called from JS: NativeBridge.readTextFile(callbackName)
     * Result callback: window[callbackName](fileContent)
     */
    @JavascriptInterface
    fun readTextFile(callbackName: String) {
        activity.runOnUiThread {
            (activity as? MainActivity)?.launchTextFileImport(callbackName)
        }
    }

    /**
     * Save text content to a TXT file in Documents directory and share it.
     * Called from JS: NativeBridge.saveTextFile(fileName, content, title)
     * The file is saved to app's external Documents directory and shared via Android share sheet.
     */
    @JavascriptInterface
    fun saveTextFile(fileName: String, content: String, title: String) {
        activity.runOnUiThread {
            try {
                val docsDir = File(activity.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "exports")
                if (!docsDir.exists()) docsDir.mkdirs()
                val file = File(docsDir, fileName)
                file.writeText(content, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, title))
            } catch (e: Exception) {
                Toast.makeText(activity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Set photo compression quality (10-100).
     * Called from JS: NativeBridge.setCompressQuality(75)
     * This value is used by uriToBase64 when compressing photos.
     */
    @JavascriptInterface
    fun setCompressQuality(quality: Int) {
        val q = quality.coerceIn(10, 100)
        (activity as? MainActivity)?.compressQuality = q
    }

    /**
     * Save a base64-encoded photo to external storage and return the file path.
     * Called from JS: NativeBridge.savePhotoToFile(base64Data, fileName)
     */
    @JavascriptInterface
    fun savePhotoToFile(base64Data: String, fileName: String): String {
        return try {
            val photosDir = File(activity.getExternalFilesDir(null), "photos")
            if (!photosDir.exists()) photosDir.mkdirs()
            val file = File(photosDir, fileName)
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Delete a photo file by path.
     * Called from JS: NativeBridge.deletePhotoFile(path)
     */
    @JavascriptInterface
    fun deletePhotoFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && file.absolutePath.contains(activity.packageName)) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete all photo files in external photos directory.
     * Called from JS: NativeBridge.deleteAllPhotos()
     * Returns the number of files deleted.
     */
    @JavascriptInterface
    fun deleteAllPhotos(): Int {
        val photosDir = File(activity.getExternalFilesDir(null), "photos")
        if (!photosDir.exists()) return 0
        var count = 0
        photosDir.listFiles()?.forEach { file ->
            if (file.isFile) { file.delete(); count++ }
        }
        return count
    }

    /**
     * Get total size of photos directory in KB.
     * Called from JS: NativeBridge.getPhotosSizeKB()
     */
    @JavascriptInterface
    fun getPhotosSizeKB(): Long {
        val photosDir = File(activity.getExternalFilesDir(null), "photos")
        if (!photosDir.exists()) return 0
        return photosDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
    }

    /**
     * Get the external photos directory path.
     * Called from JS: NativeBridge.getPhotosDir()
     */
    @JavascriptInterface
    fun getPhotosDir(): String {
        val photosDir = File(activity.getExternalFilesDir(null), "photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        return photosDir.absolutePath
    }

    /**
     * Get total size of history directory in KB.
     * Called from JS: NativeBridge.getHistorySizeKB()
     */
    @JavascriptInterface
    fun getHistorySizeKB(): Long {
        val historyDir = File(activity.getExternalFilesDir(null), "history")
        if (!historyDir.exists()) return 0
        return historyDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
    }

    /**
     * Delete all history files.
     * Called from JS: NativeBridge.deleteAllHistory()
     * Returns the number of files deleted.
     */
    @JavascriptInterface
    fun deleteAllHistory(): Int {
        val historyDir = File(activity.getExternalFilesDir(null), "history")
        if (!historyDir.exists()) return 0
        var count = 0
        historyDir.walkTopDown().forEach { file ->
            if (file.isFile) { file.delete(); count++ }
        }
        // Remove empty directories
        historyDir.walkTopDown().filter { it.isDirectory && it.listFiles()?.isEmpty() != false }.forEach { it.delete() }
        return count
    }

    /**
     * Get total size of packages (ZIP) directory in KB.
     * Called from JS: NativeBridge.getPackagesSizeKB()
     */
    @JavascriptInterface
    fun getPackagesSizeKB(): Long {
        val packagesDir = File(activity.getExternalFilesDir(null), "packages")
        if (!packagesDir.exists()) return 0
        return packagesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
    }

    /**
     * Delete all package (ZIP) files.
     * Called from JS: NativeBridge.deleteAllPackages()
     * Returns the number of files deleted.
     */
    @JavascriptInterface
    fun deleteAllPackages(): Int {
        val packagesDir = File(activity.getExternalFilesDir(null), "packages")
        if (!packagesDir.exists()) return 0
        var count = 0
        packagesDir.listFiles()?.forEach { file ->
            if (file.isFile) { file.delete(); count++ }
        }
        return count
    }

    /**
     * Read a photo file and return its base64 encoding (for preview).
     * Called from JS: NativeBridge.readPhotoAsBase64(path)
     */
    @JavascriptInterface
    fun readPhotoAsBase64(path: String): String {
        return try {
            val file = File(path)
            if (!file.exists()) return ""
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Copy a photo file for history storage.
     * Called from JS: NativeBridge.copyPhotoForHistory(srcPath, historyId, docType, index)
     * Returns the new file path.
     */
    @JavascriptInterface
    fun copyPhotoForHistory(srcPath: String, historyId: String, docType: String, index: Int): String {
        return try {
            val srcFile = File(srcPath)
            if (!srcFile.exists()) return ""
            val historyDir = File(activity.getExternalFilesDir(null), "history/$historyId/photos")
            if (!historyDir.exists()) historyDir.mkdirs()
            val destFile = File(historyDir, "${docType}_${index}.jpg")
            srcFile.copyTo(destFile, overwrite = true)
            destFile.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Create ZIP package from file paths (instead of base64).
     * Called from JS: NativeBridge.createPackageFromFiles(packageName, filePathsJson, namingMode, packMode, timestampMode)
     * filePathsJson format: {"证件类型1":["/path/to/photo1.jpg","/path/to/photo2.jpg"], "证件类型2":["/path/to/photo3.jpg"]}
     */
    @JavascriptInterface
    fun createPackageFromFiles(packageName: String, filePathsJson: String, namingMode: String, packMode: String, timestampMode: String) {
        activity.runOnUiThread {
            try {
                (activity as? MainActivity)?.createPackageFromFiles(packageName, filePathsJson, namingMode, packMode, timestampMode)
            } catch (e: Exception) {
                Toast.makeText(activity, "打包失败: ${e.message}", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.callJsFunction(
                    "window.onPackageCreated && window.onPackageCreated('', '${e.message?.replace("'", "\\'") ?: "unknown"}')"
                )
            }
        }
    }

    /**
     * Exit the app (move to background).
     */
    @JavascriptInterface
    fun exitApp() {
        activity.runOnUiThread { activity.moveTaskToBack(true) }
    }

    /**
     * Save a base64-encoded background image to external storage.
     * Called from JS: NativeBridge.saveBgImage(base64Data, fileName)
     * Returns the file path, or empty string on failure.
     */
    @JavascriptInterface
    fun saveBgImage(base64Data: String, fileName: String): String {
        return try {
            val bgDir = File(activity.getExternalFilesDir(null), "backgrounds")
            if (!bgDir.exists()) bgDir.mkdirs()
            val file = File(bgDir, fileName)
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Delete a background image file by path.
     * Called from JS: NativeBridge.deleteBgImage(path)
     */
    @JavascriptInterface
    fun deleteBgImage(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && file.absolutePath.contains(activity.packageName)) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete all background image files.
     * Called from JS: NativeBridge.deleteAllBgImages()
     * Returns the number of files deleted.
     */
    @JavascriptInterface
    fun deleteAllBgImages(): Int {
        val bgDir = File(activity.getExternalFilesDir(null), "backgrounds")
        if (!bgDir.exists()) return 0
        var count = 0
        bgDir.listFiles()?.forEach { file ->
            if (file.isFile) { file.delete(); count++ }
        }
        return count
    }

    /**
     * Get total size of backgrounds directory in KB.
     * Called from JS: NativeBridge.getBgSizeKB()
     */
    @JavascriptInterface
    fun getBgSizeKB(): Long {
        val bgDir = File(activity.getExternalFilesDir(null), "backgrounds")
        if (!bgDir.exists()) return 0
        return bgDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
    }
}