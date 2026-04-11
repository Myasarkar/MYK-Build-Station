package com.myksoft.build.utils

import java.util.regex.Pattern

/**
 * MYK Build - Error Parser (Akıllı Hata Ayıklayıcı)
 * Gradle loglarını analiz eder, BUILD FAILED durumunda Regex ile hata detaylarını ayıklar.
 */
data class ParsedError(
    val filePath: String,
    val line: String,
    val message: String,
    val isCausedBy: Boolean = false
)

class ErrorParser {

    // Regex: /path/to/file.kt:42: error: Unresolved reference
    // Regex: /path/to/file.java:42: error: cannot find symbol
    private val compileErrorPattern = Pattern.compile("^(.*?):(\\d+):\\s*(?:error|e):\\s*(.*)$", Pattern.CASE_INSENSITIVE)
    
    // Regex: Caused by: org.gradle.api.GradleException...
    private val causedByPattern = Pattern.compile("^\\s*Caused by:\\s*(.*)$", Pattern.CASE_INSENSITIVE)

    /**
     * Tüm log akışını alır ve sadece kritik hataları ayıklayarak temiz bir liste döner.
     */
    fun parseLogs(logs: List<String>): List<ParsedError> {
        val extractedErrors = mutableListOf<ParsedError>()
        var buildFailedEncountered = false

        for (line in logs) {
            if (line.contains("BUILD FAILED")) {
                buildFailedEncountered = true
            }

            // 1. Kotlin/Java Derleme Hatalarını Yakala
            val compileMatcher = compileErrorPattern.matcher(line)
            if (compileMatcher.find()) {
                val file = compileMatcher.group(1)?.substringAfterLast("/") ?: "Bilinmeyen Dosya"
                val lineNumber = compileMatcher.group(2) ?: "?"
                val message = compileMatcher.group(3) ?: "Bilinmeyen Hata"
                
                extractedErrors.add(ParsedError(file, lineNumber, message))
            }

            // 2. Stacktrace içindeki "Caused by" bloklarını Yakala
            val causedByMatcher = causedByPattern.matcher(line)
            if (causedByMatcher.find()) {
                val causeMessage = causedByMatcher.group(1) ?: "Bilinmeyen Sebep"
                // Aynı hatayı defalarca eklememek için basit bir filtre
                if (extractedErrors.none { it.message == causeMessage }) {
                    extractedErrors.add(ParsedError("Sistem/Gradle", "-", causeMessage, isCausedBy = true))
                }
            }
        }

        // Eğer BUILD FAILED olduysa ama Regex hiçbir şey yakalayamadıysa, genel bir hata dön
        if (buildFailedEncountered && extractedErrors.isEmpty()) {
            extractedErrors.add(ParsedError("Bilinmeyen", "-", "Derleme başarısız oldu ancak spesifik bir hata satırı bulunamadı. Lütfen tüm logları inceleyin."))
        }

        return extractedErrors
    }
}
