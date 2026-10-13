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
        val clean = word.trim().lowercase()

        if (clean.isEmpty() || frequency <= 0) return

        var current = root

        for (char in clean) {
            current = current.children.getOrPut(char) { TrieNode() }
        }

        current.isEndOfWord = true
        current.frequency = frequency
        current.word = clean
    }

    fun search(prefix: String): List<Pair<String, Int>> {
        val cleanPrefix = prefix.trim().lowercase()

        if (cleanPrefix.isEmpty()) {
            return getAllWords().sortedByDescending { it.second }
        }

        val results = mutableListOf<Pair<String, Int>>()
        var current = root

        for (char in cleanPrefix) {
            current = current.children[char] ?: return emptyList()
        }

        collectWords(current, results)

        return results.sortedByDescending { it.second }
    }

    private fun collectWords(
        node: TrieNode,
        results: MutableList<Pair<String, Int>>
    ) {
        if (node.isEndOfWord) {
            node.word?.let { word ->
                results.add(word to node.frequency)
            }
        }

        for (child in node.children.values) {
            collectWords(child, results)
        }
    }

    fun contains(word: String): Boolean {
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return false

        var current = root

        for (char in clean) {
            current = current.children[char] ?: return false
        }

        return current.isEndOfWord
    }

    fun getFrequency(word: String): Int {
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return 0

        var current = root

        for (char in clean) {
            current = current.children[char] ?: return 0
        }

        return if (current.isEndOfWord) {
            current.frequency
        } else {
            0
        }
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
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return false

        var existed = false

        fun removeRec(node: TrieNode, index: Int): Boolean {
            if (index == clean.length) {
                existed = node.isEndOfWord

                if (!existed) return false

                node.isEndOfWord = false
                node.word = null
                node.frequency = 0

                return node.children.isEmpty()
            }

            val char = clean[index]
            val child = node.children[char] ?: return false

            val prune = removeRec(child, index + 1)

            if (prune) {
                node.children.remove(char)
            }

            return node.children.isEmpty() && !node.isEndOfWord
        }

        removeRec(root, 0)

        return existed
    }
}

