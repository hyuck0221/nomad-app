package com.nomad.travel.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs ML Kit text recognition across multiple script recognizers and returns
 * the longest result (most likely the correct script for the menu).
 */
class OcrService {

    private val recognizers: List<TextRecognizer> = listOf(
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    )

    suspend fun recognize(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val results = recognizers.map { runOne(it, image) }
        return results.maxByOrNull { it.length }.orEmpty()
    }

    private suspend fun runOne(
        recognizer: TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
