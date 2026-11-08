package cn.family.learning.data

import androidx.room.*

@Entity(tableName="drafts")
data class Draft(@PrimaryKey val key: String,val accountId: String,val childId: String,val kind: String,val payload: String,val updatedAt: Long = System.currentTimeMillis())
@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE `key`=:key") suspend fun get(key: String): Draft?
    @Query("SELECT * FROM drafts WHERE accountId=:accountId AND childId=:childId AND kind=:kind ORDER BY updatedAt DESC") suspend fun list(accountId: String,childId: String,kind: String): List<Draft>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(draft: Draft)
    @Query("DELETE FROM drafts WHERE `key`=:key") suspend fun remove(key: String)
    @Query("DELETE FROM drafts WHERE accountId=:accountId") suspend fun removeAccount(accountId: String)
    @Query("UPDATE OR IGNORE drafts SET `key`=:newOwner || substr(`key`,length(:oldOwner)+1), accountId=:newOwner WHERE accountId=:oldOwner")
    suspend fun scopeLegacy(oldOwner: String,newOwner: String)
}
@Database(entities=[Draft::class],version=1,exportSchema=false)
abstract class LocalDatabase: RoomDatabase() { abstract fun drafts(): DraftDao }
