package cn.family.learning.recognition

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses the bundled recognizer only. No repository, upload or cloud fallback. */
class LocalTextRecognizer {
    suspend fun recognize(files: List<File>, textbook: Boolean, onProgress: (Int, Int) -> Unit): LocalWordResult {
        require(files.isNotEmpty() && files.size <= 5) { "请添加1—5张学习照片" }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val pages = files.mapIndexed { index, file ->
                onProgress(index + 1, files.size)
                val bitmap = withContext(Dispatchers.IO) {
                    require(file.isFile) { "学习照片已不存在，请重新选图" }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取第${index + 1}张照片，请重新拍摄" }
                    var sample = 1
                    while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: error("无法读取第${index + 1}张照片，请重新拍摄")
                }
                // ML Kit still owns bitmap while its Task is running, even if the coroutine is cancelled.
                suspendCancellableCoroutine<List<OcrLine>> { continuation ->
                    try {
                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { result ->
                                if (continuation.isActive) continuation.resume(result.textBlocks.flatMap { block ->
                                    block.lines.mapNotNull { line -> line.boundingBox?.let { box ->
                                        OcrLine(line.text, box.left, box.top, box.right, box.bottom)
                                    } }
                                })
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resumeWithException(
                                    IllegalStateException("第${index + 1}张照片本地识别失败，请裁剪、重拍后重试", it))
                            }
                            .addOnCompleteListener { bitmap.recycle() }
                    } catch (e: Exception) {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
            val result = withContext(Dispatchers.Default) { LocalWordParser.parse(pages, textbook) }
            require(result.words.isNotEmpty()) { "未识别到英文单词，请裁剪到清晰的单词区域后重试，或手动建立词表" }
            return result
        } finally {
            recognizer.close()
        }
    }
}
