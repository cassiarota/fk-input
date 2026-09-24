package uk.cassiangroup.fkinput

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import uk.cassiangroup.fkinput.data.SensitiveWordFile
import uk.cassiangroup.fkinput.data.selectedSensitiveWord

/** Adds text selected in another app through Android's standard selection menu. */
class AddSensitiveWordActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selection = runCatching {
            require(intent.action == Intent.ACTION_PROCESS_TEXT)
            selectedSensitiveWord(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty())
        }.getOrElse {
            notice(it.message ?: getString(R.string.invalid_sensitive_word))
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_sensitive_word)
            .setMessage(selection)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add_sensitive_word) { _, _ ->
                runCatching { SensitiveWordFile(this).add(selection) }
                    .onSuccess { notice(getString(if (it) R.string.sensitive_word_added else R.string.sensitive_word_exists)) }
                    .onFailure { notice(it.message ?: getString(R.string.sensitive_word_failed)) }
            }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
