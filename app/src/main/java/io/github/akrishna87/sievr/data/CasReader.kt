package io.github.akrishna87.sievr.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.akrishna87.sievr.core.mf.CasParser
import io.github.akrishna87.sievr.core.mf.MfScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens a CAS PDF with its password and reads the holdings, all on the phone. */
object CasReader {
    sealed interface Outcome {
        data class Read(val result: CasParser.Result) : Outcome
        data object WrongPassword : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun read(context: Context, uri: Uri, password: String, schemes: List<MfScheme>): Outcome = withContext(Dispatchers.Default) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, password).use { doc ->
                    PDFTextStripper().apply { setSortByPosition(true) }.getText(doc)
                }
            } ?: return@withContext Outcome.Failed("Couldn't open that file.")
            Outcome.Read(CasParser.parse(text, schemes))
        } catch (_: InvalidPasswordException) {
            Outcome.WrongPassword
        } catch (e: Exception) {
            Outcome.Failed("Couldn't read that PDF (${e.javaClass.simpleName}). Is it a CAS from CAMS, KFintech, MF Central, NSDL or CDSL?")
        } catch (e: OutOfMemoryError) {
            Outcome.Failed("That PDF is too large to read on this phone. Request a Summary statement instead of a Detailed one.")
        }
    }
}
