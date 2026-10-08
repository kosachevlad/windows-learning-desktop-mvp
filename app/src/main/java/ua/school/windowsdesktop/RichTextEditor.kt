package ua.school.windowsdesktop

import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.widget.EditText
import android.graphics.Typeface
import android.view.Gravity
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.text.style.UnderlineSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.text.style.StyleSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

data class SelectionStyle(val size: Int = 11, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

private class SelectionEditText(context: android.content.Context) : EditText(context) {
    var onSelectionChangedCallback: (() -> Unit)? = null
    override fun onSelectionChanged(selStart: Int, selEnd: Int) { super.onSelectionChanged(selStart, selEnd); onSelectionChangedCallback?.invoke() }
}

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
    onSelectionStyleChanged: (SelectionStyle) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SelectionEditText(context).apply {
                setTextColor(AndroidColor.BLACK)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setPadding(0, 0, 0, 0)
                setSingleLine(false)
                gravity = Gravity.TOP or Gravity.START
                setLineSpacing(0f, 1f)
                includeFontPadding = true
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onValueChange(s?.toString().orEmpty()) }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                onEditorReady(this)
                onSelectionChangedCallback = { onSelectionStyleChanged(selectionStyle(this)) }
            }
        },
        update = { editor ->
            if (editor.text.toString() != value) {
                val selection = editor.selectionStart.coerceIn(0, value.length)
                editor.setText(value)
                editor.setSelection(selection)
            }
            // Do not reset typeface or textSize here: those are selection spans.
            // Updating them on every recomposition would restyle the whole document.
            onEditorReady(editor)
            editor.onSelectionChangedCallback = { onSelectionStyleChanged(selectionStyle(editor)) }
        },
    )
}

private fun selectionStyle(editor: EditText): SelectionStyle {
    val start = editor.selectionStart.coerceAtLeast(0); val end = editor.selectionEnd.coerceAtLeast(start)
    val styles = editor.text.getSpans(start, end, StyleSpan::class.java)
    return SelectionStyle(
        size = editor.text.getSpans(start, end, AbsoluteSizeSpan::class.java).firstOrNull()?.size ?: 11,
        bold = styles.any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC },
        italic = styles.any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC },
        underline = editor.text.getSpans(start, end, UnderlineSpan::class.java).isNotEmpty(),
    )
}

fun applySpanToSelection(editor: EditText, span: Any) {
    val start = editor.selectionStart.coerceAtLeast(0)
    val end = editor.selectionEnd.coerceAtLeast(start)
    if (start == end) return
    editor.text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    editor.requestLayout()
    editor.invalidate()
}

fun applyFontToSelection(editor: EditText, family: String) = applySpanToSelection(editor, TypefaceSpan(when (family) {
    "Times New Roman" -> "serif"
    "Courier New" -> "monospace"
    else -> "sans-serif"
}))
fun applySizeToSelection(editor: EditText, sizeSp: Int) {
    val start = editor.selectionStart.coerceAtLeast(0); val end = editor.selectionEnd.coerceAtLeast(start)
    if (start == end) return
    editor.text.getSpans(start, end, AbsoluteSizeSpan::class.java).forEach { editor.text.removeSpan(it) }
    editor.text.getSpans(start, end, RelativeSizeSpan::class.java).forEach { editor.text.removeSpan(it) }
    editor.text.setSpan(AbsoluteSizeSpan(sizeSp, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    editor.requestLayout(); editor.invalidate()
}
fun applyColorToSelection(editor: EditText, color: Int) = applySpanToSelection(editor, ForegroundColorSpan(color))

fun toggleUnderlineSelection(editor: EditText) {
    val start = editor.selectionStart.coerceAtLeast(0); val end = editor.selectionEnd.coerceAtLeast(start)
    if (start == end) return
    val spans = editor.text.getSpans(start, end, UnderlineSpan::class.java)
    if (spans.isNotEmpty()) spans.forEach { editor.text.removeSpan(it) }
    else editor.text.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    editor.invalidate()
}
