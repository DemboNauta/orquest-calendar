package com.misturnos.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reconocimiento de texto on-device (ML Kit) sobre una captura de Orquest. */
class OcrService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Devuelve las líneas reconocidas ordenadas de arriba a abajo y de izquierda a derecha,
     * que es justo lo que espera [com.misturnos.parser.ShiftParser].
     */
    suspend fun recognizeLines(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        // Aplanamos a líneas y las ordenamos por posición vertical (luego horizontal) para
        // reconstruir el orden de lectura de las tarjetas.
        return result.textBlocks
            .flatMap { it.lines }
            .sortedWith(
                compareBy(
                    { it.boundingBox?.top ?: 0 },
                    { it.boundingBox?.left ?: 0 },
                ),
            )
            .map { it.text }
    }
}
