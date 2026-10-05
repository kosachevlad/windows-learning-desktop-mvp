package ua.school.windowsdesktop

import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.widget.EditText
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Editable text surface that exposes selection and spans to the Compose toolbar. */
@Composable
fun RichTextEditor(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onEditorReady: (EditText) -> Unit = {},
    fontFamily: String = "Calibri",
    fontSize: Int = 11,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            EditText(context).apply {
                setTextColor(AndroidColor.BLACK)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setPadding(0, 0, 0, 0)
                setSingleLine(false)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onValueChange(s?.toString().orEmpty()) }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                onEditorReady(this)
            }
        },
        update = { editor ->
            if (editor.text.toString() != value) {
                val selection = editor.selectionStart.coerceIn(0, value.length)
                editor.setText(value)
                editor.setSelection(selection)
            }
            editor.typeface = Typeface.create(when (fontFamily) { "Times New Roman" -> Typeface.SERIF; "Courier New" -> Typeface.MONOSPACE; else -> Typeface.SANS_SERIF }, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
            editor.textSize = fontSize.toFloat()
            editor.paint.isUnderlineText = underline
            onEditorReady(editor)
        },
    )
}

fun applySpanToSelection(editor: EditText, span: Any) {
    val start = editor.selectionStart.coerceAtLeast(0)
    val end = editor.selectionEnd.coerceAtLeast(start)
    if (start == end) return
    editor.text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}
