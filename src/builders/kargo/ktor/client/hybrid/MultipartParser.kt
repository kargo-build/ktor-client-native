package builders.kargo.ktor.client.hybrid

/**
 * A resilient multipart parser designed for Kotlin Native environments
 * where standard Ktor multipart parsing might be unavailable or buggy.
 */
object HybridMultipart {
    
    data class ParsedMultipart(
        val fields: Map<String, String>,
        val files: Map<String, ParsedFile>
    )

    data class ParsedFile(
        val bytes: ByteArray,
        val contentType: String,
        val filename: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedFile) return false
            return bytes.contentEquals(other.bytes) && 
                   contentType == other.contentType && 
                   filename == other.filename
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + (filename?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Parses raw multipart bytes using the provided boundary.
     * Uses a resilient strategy that handles truncated streams or missing final boundaries.
     */
    fun parseBytes(data: ByteArray, boundary: String): ParsedMultipart {
        val fields = mutableMapOf<String, String>()
        val files = mutableMapOf<String, ParsedFile>()

        val boundaryBytes = "--$boundary".encodeToByteArray()
        val parts = splitByBoundary(data, boundaryBytes)

        for (partBytes in parts) {
            if (partBytes.isEmpty()) continue

            val headerEndIndex = findDoubleCRLF(partBytes)
            if (headerEndIndex == -1) continue

            val headerBytes = partBytes.copyOfRange(0, headerEndIndex)
            val headerText = headerBytes.decodeToString()

            val bodyStart = headerEndIndex + 4
            val bodyEnd = if (partBytes.size >= 2 &&
                partBytes[partBytes.size - 2] == '\r'.code.toByte() &&
                partBytes[partBytes.size - 1] == '\n'.code.toByte()
            ) partBytes.size - 2 else partBytes.size

            if (bodyStart > bodyEnd) continue
            val partBody = partBytes.copyOfRange(bodyStart, bodyEnd)

            val name = extractHeaderParam(headerText, "name") ?: continue
            val filename = extractHeaderParam(headerText, "filename")
            val partContentType = extractContentType(headerText)

            if (filename != null || partContentType != null) {
                files[name] = ParsedFile(
                    bytes = partBody,
                    contentType = partContentType ?: "application/octet-stream",
                    filename = filename
                )
            } else {
                fields[name] = partBody.decodeToString()
            }
        }

        return ParsedMultipart(fields, files)
    }

    private fun splitByBoundary(data: ByteArray, boundary: ByteArray): List<ByteArray> {
        val parts = mutableListOf<ByteArray>()
        var start = 0

        while (start < data.size) {
            val idx = indexOf(data, boundary, start)
            val end = if (idx == -1) data.size else idx

            if (start > 0) {
                val partData = data.copyOfRange(start, end)
                if (partData.isNotEmpty()) parts.add(partData)
            }

            if (idx == -1) break

            start = idx + boundary.size

            // Skip \r\n or -- after boundary
            if (start < data.size - 1 && data[start] == '-'.code.toByte() && data[start + 1] == '-'.code.toByte()) {
                break // End boundary
            }
            if (start < data.size - 1 && data[start] == '\r'.code.toByte() && data[start + 1] == '\n'.code.toByte()) {
                start += 2
            }
        }

        return parts
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, fromIndex: Int = 0): Int {
        outer@ for (i in fromIndex..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun findDoubleCRLF(data: ByteArray): Int {
        val pattern = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        return indexOf(data, pattern)
    }

    private fun extractHeaderParam(headers: String, param: String): String? {
        val regex = Regex("""(?<![a-z])$param\s*=\s*["']?([^"';\s]+)["']?""", RegexOption.IGNORE_CASE)
        return regex.find(headers)?.groupValues?.get(1)
    }

    private fun extractContentType(headers: String): String? {
        val line = headers.lineSequence().find { it.startsWith("Content-Type:", ignoreCase = true) }
        return line?.substringAfter(":")?.trim()
    }
}
