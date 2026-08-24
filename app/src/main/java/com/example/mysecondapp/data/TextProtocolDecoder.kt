package com.example.mysecondapp.data

/** Decodes the JSON-style Unicode escapes used by Tencent's text protocol endpoints. */
internal fun String.decodeUnicodeEscapes(): String {
    val decoded = StringBuilder(length)
    var index = 0

    while (index < length) {
        // Keep malformed escapes intact so a provider protocol change cannot silently corrupt text.
        if (this[index] == '\\' && index + 5 < length && this[index + 1] == 'u') {
            val codePoint = substring(index + 2, index + 6).toIntOrNull(radix = 16)
            if (codePoint != null) {
                decoded.append(codePoint.toChar())
                index += 6
                continue
            }
        }
        decoded.append(this[index])
        index += 1
    }

    return decoded.toString()
}
