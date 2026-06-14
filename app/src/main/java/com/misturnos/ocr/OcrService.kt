package com.misturnos.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.misturnos.parser.OcrLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reconocimiento de texto on-device (ML Kit) sobre una captura de Orquest. */
class OcrService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Devuelve cada línea reconocida con su centro vertical (cy). El [com.misturnos.parser.ShiftParser]
     * usa esa posición para agrupar las líneas por día, sin depender del orden de lectura.
     */
    suspend fun recognizeLines(context: Context, uri: Uri): List<OcrLine> {
        val image = InputImage.fromFilePath(context, uri)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        return result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(text = line.text, cy = box.exactCenterY())
            }
    }
}
