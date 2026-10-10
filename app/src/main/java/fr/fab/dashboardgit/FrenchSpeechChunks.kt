package fr.fab.dashboardgit

object FrenchSpeechChunks {
    fun split(text:String,maxWords:Int=18):List<String> {
        val clean=text.trim().replace(Regex("\\s+")," ")
        if(clean.isBlank()) return emptyList()
        val result=mutableListOf<String>()
        val words=clean.split(" ")
        val current=mutableListOf<String>()
        fun flush() {
            if(current.isNotEmpty()) {
                result.add(current.joinToString(" "))
                current.clear()
            }
        }
        words.forEach { word ->
            current.add(word)
            val softBreak=current.size>=10 && word.lastOrNull() in listOf(',', ';', ':')
            val hardBreak=word.lastOrNull() in listOf('.', '!', '?')
            if(hardBreak || softBreak || current.size>=maxWords) flush()
        }
        flush()
        return result
    }
}
