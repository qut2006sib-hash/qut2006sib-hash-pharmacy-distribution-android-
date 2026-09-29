package com.localdistribution.pharmacy.core

/**
 * JSON writer matching Python's json.dumps(ensure_ascii=False) so audit rows
 * written on the phone look exactly like the desktop's.
 */
object Json {
    fun dumps(value: Any?): String = StringBuilder().also { write(it, value, null, 0) }.toString()
    fun pretty(value: Any?): String = StringBuilder().also { write(it, value, 2, 0) }.toString()

    private fun write(out: StringBuilder, value: Any?, indent: Int?, level: Int) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long -> out.append(value.toString())
            is String -> string(out, value)
            is Map<*, *> -> container(out, value.entries.toList(), '{', '}', indent, level) { entry ->
                string(out, entry.key.toString()); out.append(": "); write(out, entry.value, indent, level + 1)
            }
            is List<*> -> container(out, value, '[', ']', indent, level) { write(out, it, indent, level + 1) }
            else -> string(out, value.toString())
        }
    }

    private fun <T> container(out: StringBuilder, items: List<T>, open: Char, close: Char, indent: Int?, level: Int, each: (T) -> Unit) {
        out.append(open)
        if (items.isEmpty()) { out.append(close); return }
        items.forEachIndexed { index, item ->
            if (indent == null) {
                if (index > 0) out.append(", ")
            } else {
                if (index > 0) out.append(",")
                out.append('\n').append(" ".repeat(indent * (level + 1)))
            }
            each(item)
        }
        if (indent != null) out.append('\n').append(" ".repeat(indent * level))
        out.append(close)
    }

    private fun string(out: StringBuilder, value: String) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ') out.append("\\u%04x".format(ch.code)) else out.append(ch)
            }
        }
        out.append('"')
    }

    /** Minimal parser for re-formatting audit JSON in the detail view. */
    fun parse(text: String): Any? = Parser(text).run { val v = value(); skip(); v }

    private class Parser(val s: String) {
        var i = 0
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            skip()
            return when (val c = s[i]) {
                '{' -> { i++; val m = linkedMapOf<String, Any?>(); skip()
                    if (s[i] == '}') { i++; return m }
                    while (true) { skip(); val k = str(); skip(); i++; m[k] = value(); skip()
                        if (s[i] == ',') i++ else { i++; return m } } }
                '[' -> { i++; val l = mutableListOf<Any?>(); skip()
                    if (s[i] == ']') { i++; return l }
                    while (true) { l.add(value()); skip(); if (s[i] == ',') i++ else { i++; return l } } }
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> { val start = i; while (i < s.length && (s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i].isDigit())) i++
                    val t = s.substring(start, i); if (t.isEmpty()) error("bad json at $i: $c"); t.toLongOrNull() ?: t }
            }
        }
        fun str(): String {
            i++; val b = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') { i++; when (s[i]) {
                    'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                    'u' -> { b.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                    else -> b.append(s[i]) } } else b.append(s[i])
                i++
            }
            i++; return b.toString()
        }
    }
}
