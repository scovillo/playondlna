package com.example.upnpdlna

class YoutubeDlFileName(private val rootDir: String, private val fileExtension: String) {

    fun extract(expression: String): String? {
        return this.extractBetween(expression, this.rootDir, this.fileExtension)
    }

    private fun extractBetween(input: String, start: String, end: String): String? {
        val regex = Regex("(?<=${Regex.escape(start)}).*?${Regex.escape(end)}")
        return regex.find(input)?.value
    }
}