package com.merotype.keyboard.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Main database for MeroType keyboard
 * Stores: words, variants, phrases, n-grams, user vocabulary
 */

// Entities
@Entity(tableName = "words", indices = [Index(value = ["text"])])
data class WordEntity(
    @PrimaryKey val text: String,
    val frequency: Int,
    val type: String,
    val variants: String,  // comma-separated
    val meaning: String? = null,
    val examples: String? = null,
    val source: String,
    val license: String,
    val addedDate: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "variants", indices = [
    Index(value = ["variant"]),
    Index(value = ["canonical"])
])
data class VariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val canonical: String,
    val variant: String,
    val frequency: Int
)

@Entity(tableName = "phrases", indices = [Index(value = ["phrase"])])
data class PhraseEntity(
    @PrimaryKey val phrase: String,
    val frequency: Int,
    val meaning: String? = null,
    val source: String
)

@Entity(tableName = "ngrams", indices = [Index(value = ["ngram", "n"])])
data class NGramEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ngram: String,
    val n: Int,  // 2 for bigram, 3 for trigram
    val frequency: Int,
    val source: String
)

@Entity(tableName = "word_pairs", indices = [Index(value = ["first_word"])])
data class WordPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstWord: String,
    val secondWord: String,
    val frequency: Int,
    val probability: Double
)

@Entity(tableName = "abbreviations", indices = [Index(value = ["abbreviation"])])
data class AbbreviationEntity(
    @PrimaryKey val abbreviation: String,
    val expansions: String,  // JSON: ["ke", "ko"]
    val frequencies: String, // JSON: [0.6, 0.4]
    val source: String
)

@Entity(tableName = "entities")
data class EntityEntity(
    @PrimaryKey val name: String,
    val type: String,  // "city", "person_name", "organization"
    val aliases: String,  // JSON array
    val frequency: Int,
    val source: String
)

@Entity(tableName = "user_vocabulary")
data class UserVocabularyEntity(
    @PrimaryKey val word: String,
    val category: String,
    val frequency: Int,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime,
    val source: String  // "user_typed", "contact_name", etc.
)

// DAOs
@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE text LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByPrefix(prefix: String, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE text = :word")
    suspend fun findByWord(word: String): WordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: WordEntity)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int
}

@Dao
interface VariantDao {
    @Query("SELECT * FROM variants WHERE variant LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByVariantPrefix(prefix: String, limit: Int): List<VariantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(variant: VariantEntity)
}

@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases WHERE phrase LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByPrefix(prefix: String, limit: Int): List<PhraseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phrase: PhraseEntity)
}

@Dao
interface NGramDao {
    @Query("SELECT * FROM ngrams WHERE ngram LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByPrefix(prefix: String, limit: Int): List<NGramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ngram: NGramEntity)
}

@Dao
interface WordPairDao {
    @Query("SELECT * FROM word_pairs WHERE first_word = :firstWord ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByFirstWord(firstWord: String, limit: Int): List<WordPairEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pair: WordPairEntity)
}

@Dao
interface AbbreviationDao {
    @Query("SELECT * FROM abbreviations WHERE abbreviation = :abbr")
    suspend fun findByAbbreviation(abbr: String): AbbreviationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(abbr: AbbreviationEntity)
}

@Dao
interface EntityDao {
    @Query("SELECT * FROM entities WHERE name LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByNamePrefix(prefix: String, limit: Int): List<EntityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EntityEntity)
}

@Dao
interface UserVocabularyDao {
    @Query("SELECT * FROM user_vocabulary WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun findByWordPrefix(prefix: String, limit: Int): List<UserVocabularyEntity>

    @Query("SELECT * FROM user_vocabulary WHERE word = :word")
    suspend fun findByWord(word: String): UserVocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vocab: UserVocabularyEntity)

    @Query("DELETE FROM user_vocabulary WHERE lastSeen < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: LocalDateTime)
}

// Database
@Database(
    entities = [
        WordEntity::class,
        VariantEntity::class,
        PhraseEntity::class,
        NGramEntity::class,
        WordPairEntity::class,
        AbbreviationEntity::class,
        EntityEntity::class,
        UserVocabularyEntity::class
    ],
    version = 1
)
abstract class LocalLanguageDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun variantDao(): VariantDao
    abstract fun phraseDao(): PhraseDao
    abstract fun ngramDao(): NGramDao
    abstract fun wordPairDao(): WordPairDao
    abstract fun abbreviationDao(): AbbreviationDao
    abstract fun entityDao(): EntityDao
    abstract fun userVocabularyDao(): UserVocabularyDao

    companion object {
        @Volatile
        private var instance: LocalLanguageDatabase? = null

        fun getInstance(context: Context): LocalLanguageDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LocalLanguageDatabase::class.java,
                    "merotype_language.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
