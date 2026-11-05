package android.util

/** android.util.Base64 replacement backed by java.util.Base64. Flags follow Android's values. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    private fun encoder(flags: Int): java.util.Base64.Encoder {
        var enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder()
        else if (flags and NO_WRAP != 0) java.util.Base64.getEncoder()
        else java.util.Base64.getMimeEncoder(76, if (flags and CRLF != 0) "\r\n".toByteArray() else "\n".toByteArray())
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        return enc
    }

    private fun decodeBytes(input: ByteArray, flags: Int): ByteArray {
        // Android's decoder is lenient: accepts both alphabets, whitespace and missing padding
        val cleaned = String(input, Charsets.ISO_8859_1)
            .filterNot { it.isWhitespace() }
            .replace('-', '+').replace('_', '/')
            .trimEnd('=')
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return java.util.Base64.getDecoder().decode(padded)
    }

    @JvmStatic fun decode(str: String, flags: Int): ByteArray = decodeBytes(str.toByteArray(Charsets.ISO_8859_1), flags)
    @JvmStatic fun decode(input: ByteArray, flags: Int): ByteArray = decodeBytes(input, flags)
    @JvmStatic fun decode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray =
        decodeBytes(input.copyOfRange(offset, offset + len), flags)

    @JvmStatic fun encode(input: ByteArray, flags: Int): ByteArray = encoder(flags).encode(input)
    @JvmStatic fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray =
        encoder(flags).encode(input.copyOfRange(offset, offset + len))
    @JvmStatic fun encodeToString(input: ByteArray, flags: Int): String = String(encode(input, flags), Charsets.ISO_8859_1)
    @JvmStatic fun encodeToString(input: ByteArray, offset: Int, len: Int, flags: Int): String =
        String(encode(input, offset, len, flags), Charsets.ISO_8859_1)
}
