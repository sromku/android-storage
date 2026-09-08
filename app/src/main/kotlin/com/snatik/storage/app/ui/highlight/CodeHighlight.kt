package com.snatik.storage.app.ui.highlight

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** Colours for each token type, taken from the theme so both light and dark read well. */
class TokenColors(
    val keyword: Color, val string: Color, val comment: Color, val number: Color,
    val punctuation: Color, val tag: Color, val attribute: Color, val annotation: Color,
    val key: Color, val literal: Color, val plain: Color,
) {
    fun of(type: TokenType): Color = when (type) {
        TokenType.KEYWORD -> keyword
        TokenType.STRING -> string
        TokenType.COMMENT -> comment
        TokenType.NUMBER -> number
        TokenType.PUNCTUATION -> punctuation
        TokenType.TAG -> tag
        TokenType.ATTRIBUTE -> attribute
        TokenType.ANNOTATION -> annotation
        TokenType.KEY -> key
        TokenType.LITERAL -> literal
        TokenType.PLAIN -> plain
    }
}

@Composable
fun rememberTokenColors(): TokenColors {
    val scheme = MaterialTheme.colorScheme
    return TokenColors(
        keyword = scheme.primary,
        string = scheme.tertiary,
        comment = scheme.onSurfaceVariant,
        number = scheme.secondary,
        punctuation = scheme.onSurfaceVariant,
        tag = scheme.primary,
        attribute = scheme.tertiary,
        annotation = scheme.error,
        key = scheme.primary,
        literal = scheme.error,
        plain = scheme.onSurface,
    )
}

/**
 * Tokenize the whole text, then split into one coloured [AnnotatedString] per line so a lazy
 * viewer keeps multi-line strings and block comments coloured correctly.
 */
fun highlightLines(lines: List<String>, language: HlLanguage, colors: TokenColors): List<AnnotatedString> {
    val text = lines.joinToString("\n")
    val tokens = Highlighter.tokenize(text, language)
    val result = ArrayList<AnnotatedString>(lines.size)
    var lineStart = 0
    var tokenIndex = 0
    for (line in lines) {
        val lineEnd = lineStart + line.length
        result += buildAnnotatedString {
            append(line)
            // Skip tokens fully before this line.
            while (tokenIndex < tokens.size && tokens[tokenIndex].end <= lineStart) tokenIndex++
            var k = tokenIndex
            while (k < tokens.size && tokens[k].start < lineEnd) {
                val tk = tokens[k]
                val from = (tk.start - lineStart).coerceAtLeast(0)
                val to = (tk.end - lineStart).coerceAtMost(line.length)
                if (to > from) addStyle(SpanStyle(color = colors.of(tk.type)), from, to)
                k++
            }
        }
        lineStart = lineEnd + 1 // account for the '\n'
    }
    return result
}
