@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import splitties.resources.color
import java.util.regex.Pattern

val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@js:|@Json:|@css:|@@|@XPath:")
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")
val wrapPattern: Pattern = Pattern.compile("\\\\n")
val operationPattern: Pattern =
    Pattern.compile(":|==|>|<|!=|>=|<=|->|=|%|-|-=|%=|\\+|\\-|\\-=|\\+=|\\^|\\&|\\|::|\\?|\\*")
val jsPattern: Pattern = Pattern.compile("var")

// 12-missed-2 #27：高亮配色改用 code_* 明暗成对令牌（关键字橙/键蓝/注释灰，两主题均 ≥4.5:1）
// 用 CodeView 自身 context 解析：appCtx 不带 AppCompatDelegate 夜间限定，
// 「系统浅色 + 应用内深色」时会错拿亮色配色压暗底（#C2410C on #1D1B19 仅 3.3:1）
fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, context.color(R.color.code_keyword))
}

fun CodeView.addJsonPattern() {
    addSyntaxPattern(jsonPattern, context.color(R.color.code_key))
}

fun CodeView.addJsPattern() {
    addSyntaxPattern(wrapPattern, context.color(R.color.code_comment))
    addSyntaxPattern(operationPattern, context.color(R.color.code_keyword))
    addSyntaxPattern(jsPattern, context.color(R.color.code_key))
}

fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}