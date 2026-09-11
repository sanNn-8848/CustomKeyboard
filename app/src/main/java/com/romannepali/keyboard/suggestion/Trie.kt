package com.romannepali.keyboard.suggestion

class TrieNode {
    val children = HashMap<Char, TrieNode>()
    var isEndOfWord = false
    var frequency = 0
    var word: String? = null
}

class Trie {
    private val root = TrieNode()

    fun insert(word: String, frequency: Int = 1) {
        var current = root
        for (char in word.lowercase()) {
            current = current.children.getOrPut(char) { TrieNode() }
        }
        current.isEndOfWord = true
        current.frequency = frequency
        current.word = word
    }

    fun search(prefix: String): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        var current = root
        
        for (char in prefix.lowercase()) {
            current = current.children[char] ?: return results
        }
        
        collectWords(current, results)
        return results.sortedByDescending { it.second }
    }

    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>) {
        if (node.isEndOfWord) {
            node.word?.let { word ->
                results.add(Pair(word, node.frequency))
            }
        }
        
        for ((_, child) in node.children) {
            collectWords(child, results)
        }
    }

    fun contains(word: String): Boolean {
        var current = root
        for (char in word.lowercase()) {
            current = current.children[char] ?: return false
        }
        return current.isEndOfWord
    }

    fun getFrequency(word: String): Int {
        var current = root
        for (char in word.lowercase()) {
            current = current.children[char] ?: return 0
        }
        return if (current.isEndOfWord) current.frequency else 0
    }

    fun getAllWords(): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()

        fun collect(node: TrieNode) {
            if (node.isEndOfWord && node.word != null) {
                result.add(node.word!! to node.frequency)
            }
            for (child in node.children.values) {
                collect(child)
            }
        }

        collect(root)
        return result
    }

    fun remove(word: String): Boolean {
        var existed = false

        fun removeRec(node: TrieNode, index: Int): Boolean {
            if (index == word.length) {
                existed = node.isEndOfWord
                if (!existed) return false
                node.isEndOfWord = false
                node.word = null
                node.frequency = 0
                return node.children.isEmpty()
            }
            val child = node.children[word[index].lowercaseChar()] ?: return false
            val prune = removeRec(child, index + 1)
            if (prune) {
                node.children.remove(word[index].lowercaseChar())
            }
            return node.children.isEmpty() && !node.isEndOfWord
        }

        if (word.isNotEmpty()) removeRec(root, 0)
        return existed
    }
}
