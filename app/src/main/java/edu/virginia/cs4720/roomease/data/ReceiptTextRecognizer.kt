package edu.virginia.cs4720.roomease.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import edu.virginia.cs4720.roomease.domain.ReceiptParser
import edu.virginia.cs4720.roomease.domain.ReceiptSuggestion
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * On-device text recognition (ML Kit, bundled model): the photo is read on the phone and never
 * uploaded anywhere (Lecture 17 "process on the phone"). Holds only the Application context.
 */
class ReceiptTextRecognizer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun suggest(photo: File): ReceiptSuggestion {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromFilePath(appContext, Uri.fromFile(photo))
            ReceiptParser.parse(recognizer.process(image).await().text)
        } finally {
            recognizer.close()
        }
    }
}
