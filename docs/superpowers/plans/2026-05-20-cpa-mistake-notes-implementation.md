# CPA 错题笔记应用实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建完整的 CPA 错题笔记 Android 应用，包含数据库、手写画布、首页、录入、复习、错题分析五大模块

**Architecture:** 
- Clean Architecture：UI 层（Compose + 手写View）→ Domain 层（Model + Repository 接口）→ Data 层（Room + DataStore）
- Hilt 依赖注入
- 单 Activity 多 Screen 导航

**Tech Stack:** Kotlin + Jetpack Compose + Room + Hilt + Navigation Compose + Android 原生 View（手写）

---

## 第一阶段：项目骨架与数据库

### Task 1: 创建项目基础结构

**Files:**
- Create: `app/src/main/java/com/mistakenotes/MistakeNotesApp.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/theme/Color.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/theme/Type.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/mistakenotes/MainActivity.kt:1-24`

- [ ] **Step 1: 创建 Application 类**

```kotlin
// app/src/main/java/com/mistakenotes/MistakeNotesApp.kt
package com.mistakenotes

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MistakeNotesApp : Application()
```

- [ ] **Step 2: 创建颜色主题文件**

```kotlin
// app/src/main/java/com/mistakenotes/ui/theme/Color.kt
package com.mistakenotes.ui.theme

import androidx.compose.ui.graphics.Color

val InkStoneBlack = Color(0xFF1A1A1A)
val CardDark = Color(0xFF242424)
val TextCream = Color(0xFFE8E4DC)
val AmberGold = Color(0xFFD4A574)
val SuccessGreen = Color(0xFF6ABF6A)
val ErrorRed = Color(0xFFD44040)
```

- [ ] **Step 3: 创建字体主题文件**

```kotlin
// app/src/main/java/com/mistakenotes/ui/theme/Type.kt
package com.mistakenotes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp)
)
```

- [ ] **Step 4: 创建 Theme.kt**

```kotlin
// app/src/main/java/com/mistakenotes/ui/theme/Theme.kt
package com.mistakenotes.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AmberGold,
    secondary = TextCream,
    background = InkStoneBlack,
    surface = CardDark,
    onPrimary = InkStoneBlack,
    onSecondary = InkStoneBlack,
    onBackground = TextCream,
    onSurface = TextCream,
    error = ErrorRed
)

@Composable
fun MistakeNotesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = InkStoneBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
```

- [ ] **Step 5: 修改 MainActivity.kt**

```kotlin
// app/src/main/java/com/mistakenotes/MainActivity.kt
package com.mistakenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MistakeNotesTheme {
                // 导航将在后续步骤添加
            }
        }
    }
}
```

- [ ] **Step 6: 更新 AndroidManifest.xml 添加 Application 类**

```xml
<!-- app/src/main/AndroidManifest.xml -->
<application
    android:name=".MistakeNotesApp"
    ... >
```

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/mistakenotes/MistakeNotesApp.kt app/src/main/java/com/mistakenotes/ui/theme/ app/src/main/AndroidManifest.xml
git commit -m "feat: add project skeleton and theme"
```

---

### Task 2: 创建数据库实体与 DAO

**Files:**
- Create: `app/src/main/java/com/mistakenotes/domain/model/Subject.kt`
- Create: `app/src/main/java/com/mistakenotes/domain/model/Chapter.kt`
- Create: `app/src/main/java/com/mistakenotes/domain/model/KnowledgePoint.kt`
- Create: `app/src/main/java/com/mistakenotes/domain/model/Mistake.kt`
- Create: `app/src/main/java/com/mistakenotes/domain/model/ReviewRecord.kt`
- Create: `app/src/main/java/com/mistakenotes/data/local/Entities.kt`
- Create: `app/src/main/java/com/mistakenotes/data/local/Dao.kt`
- Create: `app/src/main/java/com/mistakenotes/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/mistakenotes/data/local/Converters.kt`

- [ ] **Step 1: 创建 Subject 实体**

```kotlin
// app/src/main/java/com/mistakenotes/domain/model/Subject.kt
package com.mistakenotes.domain.model

data class Subject(
    val id: Long = 0,
    val name: String,
    val color: Long = 0xFFD4A574
)
```

- [ ] **Step 2: 创建 Chapter 实体**

```kotlin
// app/src/main/java/com/mistakenotes/domain/model/Chapter.kt
package com.mistakenotes.domain.model

data class Chapter(
    val id: Long = 0,
    val subjectId: Long,
    val name: String,
    val order: Int = 0
)
```

- [ ] **Step 3: 创建 KnowledgePoint 实体**

```kotlin
// app/src/main/java/com/mistakenotes/domain/model/KnowledgePoint.kt
package com.mistakenotes.domain.model

data class KnowledgePoint(
    val id: Long = 0,
    val chapterId: Long,
    val name: String,
    val isPreset: Boolean = false
)
```

- [ ] **Step 4: 创建 Mistake 实体（含题目类型枚举）**

```kotlin
// app/src/main/java/com/mistakenotes/domain/model/Mistake.kt
package com.mistakenotes.domain.model

enum class QuestionType {
    SINGLE_CHOICE,
    MULTI_CHOICE,
    ESSAY
}

data class Mistake(
    val id: Long = 0,
    val title: String = "",
    val subjectId: Long,
    val chapterId: Long,
    val knowledgePointId: Long,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val questionImagePath: String? = null,
    val questionText: String? = null,
    val options: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val referenceAnswer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isTop: Boolean = false
)
```

- [ ] **Step 5: 创建 ReviewRecord 实体（含复习结果枚举）**

```kotlin
// app/src/main/java/com/mistakenotes/domain/model/ReviewRecord.kt
package com.mistakenotes.domain.model

enum class ReviewResult {
    CORRECT,
    WRONG,
    SKIP
}

data class ReviewRecord(
    val id: Long = 0,
    val mistakeId: Long,
    val reviewDate: Long = System.currentTimeMillis(),
    val result: ReviewResult = ReviewResult.SKIP,
    val score: Int? = null,
    val nextReviewDate: Long? = null,
    val correctCount: Int = 0
)
```

- [ ] **Step 6: 创建 Room Entity 类（对应数据库表）**

```kotlin
// app/src/main/java/com/mistakenotes/data/local/Entities.kt
package com.mistakenotes.data.local

import androidx.room.*

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFFD4A574
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val name: String,
    val order: Int = 0
)

@Entity(
    tableName = "knowledge_points",
    foreignKeys = [ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["id"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class KnowledgePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: Long,
    val name: String,
    val isPreset: Boolean = false
)

@Entity(
    tableName = "mistakes",
    foreignKeys = [
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = KnowledgePointEntity::class, parentColumns = ["id"], childColumns = ["knowledgePointId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val subjectId: Long,
    val chapterId: Long,
    val knowledgePointId: Long,
    val questionType: String = "SINGLE_CHOICE",
    val questionImagePath: String? = null,
    val questionText: String? = null,
    val options: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val referenceAnswer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isTop: Boolean = false
)

@Entity(
    tableName = "review_records",
    foreignKeys = [ForeignKey(
        entity = MistakeEntity::class,
        parentColumns = ["id"],
        childColumns = ["mistakeId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReviewRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: Long,
    val reviewDate: Long = System.currentTimeMillis(),
    val result: String = "SKIP",
    val score: Int? = null,
    val nextReviewDate: Long? = null,
    val correctCount: Int = 0
)
```

- [ ] **Step 7: 创建 DAO 接口**

```kotlin
// app/src/main/java/com/mistakenotes/data/local/Dao.kt
package com.mistakenotes.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY id") fun getAll(): Flow<List<SubjectEntity>>
    @Query("SELECT * FROM subjects WHERE id = :id") suspend fun getById(id: Long): SubjectEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: SubjectEntity): Long
    @Delete suspend fun delete(entity: SubjectEntity)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE subjectId = :subjectId ORDER BY `order`") fun getBySubject(subjectId: Long): Flow<List<ChapterEntity>>
    @Query("SELECT * FROM chapters WHERE id = :id") suspend fun getById(id: Long): ChapterEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: ChapterEntity): Long
    @Delete suspend fun delete(entity: ChapterEntity)
}

@Dao
interface KnowledgePointDao {
    @Query("SELECT * FROM knowledge_points WHERE chapterId = :chapterId") fun getByChapter(chapterId: Long): Flow<List<KnowledgePointEntity>>
    @Query("SELECT * FROM knowledge_points WHERE id = :id") suspend fun getById(id: Long): KnowledgePointEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: KnowledgePointEntity): Long
    @Delete suspend fun delete(entity: KnowledgePointEntity)
}

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes ORDER BY isTop DESC, createdAt DESC") fun getAll(): Flow<List<MistakeEntity>>
    @Query("SELECT * FROM mistakes WHERE subjectId = :subjectId ORDER BY isTop DESC, createdAt DESC") fun getBySubject(subjectId: Long): Flow<List<MistakeEntity>>
    @Query("SELECT * FROM mistakes WHERE id = :id") suspend fun getById(id: Long): MistakeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: MistakeEntity): Long
    @Update suspend fun update(entity: MistakeEntity)
    @Delete suspend fun delete(entity: MistakeEntity)
}

@Dao
interface ReviewRecordDao {
    @Query("SELECT * FROM review_records WHERE mistakeId = :mistakeId ORDER BY reviewDate DESC") fun getByMistake(mistakeId: Long): Flow<List<ReviewRecordEntity>>
    @Query("SELECT * FROM review_records ORDER BY reviewDate DESC") fun getAll(): Flow<List<ReviewRecordEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: ReviewRecordEntity): Long
}
```

- [ ] **Step 8: 创建 Converters（JSON 转换器）**

```kotlin
// app/src/main/java/com/mistakenotes/data/local/Converters.kt
package com.mistakenotes.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return null
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val jsonArray = JSONArray(value)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
}
```

- [ ] **Step 9: 创建 AppDatabase**

```kotlin
// app/src/main/java/com/mistakenotes/data/local/AppDatabase.kt
package com.mistakenotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SubjectEntity::class,
        ChapterEntity::class,
        KnowledgePointEntity::class,
        MistakeEntity::class,
        ReviewRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun chapterDao(): ChapterDao
    abstract fun knowledgePointDao(): KnowledgePointDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun reviewRecordDao(): ReviewRecordDao
}
```

- [ ] **Step 10: 提交**

```bash
git add app/src/main/java/com/mistakenotes/domain/model/ app/src/main/java/com/mistakenotes/data/local/
git commit -m "feat: add database entities and DAOs"
```

---

### Task 3: 创建 Repository 与 DI Module

**Files:**
- Create: `app/src/main/java/com/mistakenotes/data/repository/MistakeRepository.kt`
- Create: `app/src/main/java/com/mistakenotes/di/DatabaseModule.kt`

- [ ] **Step 1: 创建 MistakeRepository**

```kotlin
// app/src/main/java/com/mistakenotes/data/repository/MistakeRepository.kt
package com.mistakenotes.data.repository

import com.mistakenotes.data.local.*
import com.mistakenotes.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MistakeRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val chapterDao: ChapterDao,
    private val knowledgePointDao: KnowledgePointDao,
    private val mistakeDao: MistakeDao,
    private val reviewRecordDao: ReviewRecordDao
) {
    // Subject
    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    suspend fun getSubjectById(id: Long): Subject? = subjectDao.getById(id)?.toDomain()
    suspend fun insertSubject(subject: Subject): Long = subjectDao.insert(subject.toEntity())
    suspend fun deleteSubject(subject: Subject) = subjectDao.delete(subject.toEntity())

    // Chapter
    fun getChaptersBySubject(subjectId: Long): Flow<List<Chapter>> =
        chapterDao.getBySubject(subjectId).map { list -> list.map { it.toDomain() } }
    suspend fun insertChapter(chapter: Chapter): Long = chapterDao.insert(chapter.toEntity())

    // KnowledgePoint
    fun getKnowledgePointsByChapter(chapterId: Long): Flow<List<KnowledgePoint>> =
        knowledgePointDao.getByChapter(chapterId).map { list -> list.map { it.toDomain() } }
    suspend fun insertKnowledgePoint(kp: KnowledgePoint): Long = knowledgePointDao.insert(kp.toEntity())

    // Mistake
    fun getAllMistakes(): Flow<List<Mistake>> = mistakeDao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    fun getMistakesBySubject(subjectId: Long): Flow<List<Mistake>> =
        mistakeDao.getBySubject(subjectId).map { list -> list.map { it.toDomain() } }
    suspend fun getMistakeById(id: Long): Mistake? = mistakeDao.getById(id)?.toDomain()
    suspend fun insertMistake(mistake: Mistake): Long = mistakeDao.insert(mistake.toEntity())
    suspend fun updateMistake(mistake: Mistake) = mistakeDao.update(mistake.toEntity())
    suspend fun deleteMistake(mistake: Mistake) = mistakeDao.delete(mistake.toEntity())

    // ReviewRecord
    fun getReviewRecordsByMistake(mistakeId: Long): Flow<List<ReviewRecord>> =
        reviewRecordDao.getByMistake(mistakeId).map { list -> list.map { it.toDomain() } }
    fun getAllReviewRecords(): Flow<List<ReviewRecord>> = reviewRecordDao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    suspend fun insertReviewRecord(record: ReviewRecord): Long = reviewRecordDao.insert(record.toEntity())
}

// Extension functions for mapping
fun SubjectEntity.toDomain() = Subject(id, name, color)
fun Subject.toEntity() = SubjectEntity(id, name, color)

fun ChapterEntity.toDomain() = Chapter(id, subjectId, name, order)
fun Chapter.toEntity() = ChapterEntity(id, subjectId, name, order)

fun KnowledgePointEntity.toDomain() = KnowledgePoint(id, chapterId, name, isPreset)
fun KnowledgePoint.toEntity() = KnowledgePointEntity(id, chapterId, name, isPreset)

fun MistakeEntity.toDomain() = Mistake(
    id, title, subjectId, chapterId, knowledgePointId,
    QuestionType.valueOf(questionType), questionImagePath, questionText, options,
    correctAnswer, explanation, referenceAnswer, createdAt, isFavorite, isTop
)
fun Mistake.toEntity() = MistakeEntity(
    id, title, subjectId, chapterId, knowledgePointId,
    questionType.name, questionImagePath, questionText, options,
    correctAnswer, explanation, referenceAnswer, createdAt, isFavorite, isTop
)

fun ReviewRecordEntity.toDomain() = ReviewRecord(
    id, mistakeId, reviewDate, ReviewResult.valueOf(result), score, nextReviewDate, correctCount
)
fun ReviewRecord.toEntity() = ReviewRecordEntity(
    id, mistakeId, reviewDate, result.name, score, nextReviewDate, correctCount
)
```

- [ ] **Step 2: 创建 Hilt DI Module**

```kotlin
// app/src/main/java/com/mistakenotes/di/DatabaseModule.kt
package com.mistakenotes.di

import android.content.Context
import androidx.room.Room
import com.mistakenotes.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mistake_notes.db"
        ).build()
    }

    @Provides
    fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideKnowledgePointDao(db: AppDatabase): KnowledgePointDao = db.knowledgePointDao()

    @Provides
    fun provideMistakeDao(db: AppDatabase): MistakeDao = db.mistakeDao()

    @Provides
    fun provideReviewRecordDao(db: AppDatabase): ReviewRecordDao = db.reviewRecordDao()
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/data/repository/MistakeRepository.kt app/src/main/java/com/mistakenotes/di/DatabaseModule.kt
git commit -m "feat: add repository and DI module"
```

---

## 第二阶段：手写画布核心

### Task 4: 手写画布数据结构与渲染器

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/canvas/StrokePoint.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/canvas/VectorStroke.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/canvas/StrokeRenderer.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/canvas/VectorLayer.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/canvas/HandwritingCanvas.kt`

- [ ] **Step 1: 创建 StrokePoint 数据类**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/StrokePoint.kt
package com.mistakenotes.ui.canvas

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 VectorStroke 数据类**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/VectorStroke.kt
package com.mistakenotes.ui.canvas

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class VectorStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint> = emptyList(),
    val color: Color = Color.Black,
    val baseThickness: Float = 3f
)
```

- [ ] **Step 3: 创建 VectorLayer 类**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/VectorLayer.kt
package com.mistakenotes.ui.canvas

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class VectorLayer {
    val strokes = mutableStateListOf<VectorStroke>()
    var opacity: Float = 1f
    var isVisible: Boolean = true
    var isLocked: Boolean = false

    fun addStroke(stroke: VectorStroke) {
        strokes.add(stroke)
    }

    fun removeStroke(strokeId: String) {
        strokes.removeIf { it.id == strokeId }
    }

    fun clear() {
        strokes.clear()
    }
}
```

- [ ] **Step 4: 创建 StrokeRenderer（Catmull-Rom 样条平滑 + 多边形渲染）**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/StrokeRenderer.kt
package com.mistakenotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.pow

object StrokeRenderer {

    fun renderStroke(canvas: Canvas, stroke: VectorStroke) {
        if (stroke.points.size < 2) return

        val path = Path()
        val smoothedPoints = catmullRomSmooth(stroke.points)

        if (smoothedPoints.size < 2) return

        // Build stroke polygon with pressure-based width
        val leftPoints = mutableListOf<Offset>()
        val rightPoints = mutableListOf<Offset>()

        for (i in smoothedPoints.indices) {
            val point = smoothedPoints[i]
            val pressure = point.pressure.coerceIn(0.1f, 1f)
            val halfWidth = stroke.baseThickness * pressure / 2

            // Calculate tangent direction
            val tangent = if (i == 0) {
                val next = smoothedPoints[i + 1]
                Offset(next.x - point.x, next.y - point.y)
            } else if (i == smoothedPoints.lastIndex) {
                val prev = smoothedPoints[i - 1]
                Offset(point.x - prev.x, point.y - prev.y)
            } else {
                val prev = smoothedPoints[i - 1]
                val next = smoothedPoints[i + 1]
                Offset((next.x - prev.x) / 2, (next.y - prev.y) / 2)
            }

            val normal = Offset(-tangent.y, tangent.x).normalized() * halfWidth

            leftPoints.add(Offset(point.x + normal.x, point.y + normal.y))
            rightPoints.add(Offset(point.x - normal.x, point.y - normal.y))
        }

        // Build path
        path.moveTo(leftPoints[0].x, leftPoints[0].y)
        for (i in 1 until leftPoints.size) {
            path.lineTo(leftPoints[i].x, leftPoints[i].y)
        }
        for (i in rightPoints.indices.reversed()) {
            path.lineTo(rightPoints[i].x, rightPoints[i].y)
        }
        path.close()

        canvas.drawPath(
            path = path,
            color = stroke.color,
            style = Fill
        )

        // Draw round caps at start and end
        val startPoint = smoothedPoints.first()
        val endPoint = smoothedPoints.last()
        val startPressure = startPoint.pressure.coerceIn(0.1f, 1f)
        val endPressure = endPoint.pressure.coerceIn(0.1f, 1f)

        canvas.drawOval(
            oval = androidx.compose.ui.geometry.Rect(
                center = Offset(startPoint.x, startPoint.y),
                halfWidth = stroke.baseThickness * startPressure / 2,
                halfHeight = stroke.baseThickness * startPressure / 2
            ),
            color = stroke.color,
            style = Fill
        )
        canvas.drawOval(
            oval = androidx.compose.ui.geometry.Rect(
                center = Offset(endPoint.x, endPoint.y),
                halfWidth = stroke.baseThickness * endPressure / 2,
                halfHeight = stroke.baseThickness * endPressure / 2
            ),
            color = stroke.color,
            style = Fill
        )
    }

    private fun catmullRomSmooth(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 2) return points
        if (points.size == 2) return points

        val result = mutableListOf<StrokePoint>()
        val segments = 4 // 4 interpolation points between each pair

        for (i in 0 until points.size - 1) {
            val p0 = if (i > 0) points[i - 1] else points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i < points.size - 2) points[i + 2] else points[i + 1]

            for (j in 0 until segments) {
                val t = j.toFloat() / segments
                val x = catmullRomInterpolate(p0.x, p1.x, p2.x, p3.x, t)
                val y = catmullRomInterpolate(p0.y, p1.y, p2.y, p3.y, t)
                val pressure = lerp(p1.pressure, p2.pressure, t).coerceIn(0.1f, 1f)
                result.add(StrokePoint(x, y, pressure))
            }
        }
        result.add(points.last())
        return result
    }

    private fun catmullRomInterpolate(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        return 0.5f * (
            2 * p1 +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t.pow(2) +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t.pow(3)
        )
    }

    private fun Offset.normalized(): Offset {
        val length = kotlin.math.sqrt(x * x + y * y)
        return if (length > 0) Offset(x / length, y / length) else this
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
```

- [ ] **Step 5: 创建 HandwritingCanvas 主组件**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/HandwritingCanvas.kt
package com.mistakenotes.ui.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Canvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

enum class DrawingTool { PEN, HIGHIGHTER, ERASER }

data class CanvasState(
    val currentTool: DrawingTool = DrawingTool.PEN,
    val penColor: Color = Color.Blue,
    val penThickness: Float = 3f,
    val isPenDown: Boolean = false,
    val currentStroke: VectorStroke? = null
)

@Composable
fun HandwritingCanvas(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    onStrokeCompleted: (VectorStroke) -> Unit = {}
) {
    val density = LocalDensity.current
    var canvasState by remember { mutableStateOf(CanvasState()) }
    val layers = remember { listOf(VectorLayer(), VectorLayer()) }
    val undoStack = remember { mutableStateListOf<List<VectorStroke>>() }
    val redoStack = remember { mutableStateListOf<List<VectorStroke>>() }

    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        canvasState = canvasState.copy(isPenDown = true)
                        val stroke = VectorStroke(
                            points = listOf(
                                StrokePoint(
                                    event.x,
                                    event.y,
                                    event.pressure.coerceIn(0.1f, 1f)
                                )
                            ),
                            color = canvasState.currentTool.let {
                                if (it == DrawingTool.HIGHIGHTER) canvasState.penColor.copy(alpha = 0.3f)
                                else canvasState.penColor
                            },
                            baseThickness = canvasState.penThickness
                        )
                        canvasState = canvasState.copy(currentStroke = stroke)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val currentStroke = canvasState.currentStroke ?: return@pointerInteropFilter true
                        val newPoints = currentStroke.points + StrokePoint(
                            event.x,
                            event.y,
                            event.pressure.coerceIn(0.1f, 1f)
                        )
                        canvasState = canvasState.copy(
                            currentStroke = currentStroke.copy(points = newPoints)
                        )
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val stroke = canvasState.currentStroke
                        if (stroke != null && stroke.points.size >= 2) {
                            layers[0].addStroke(stroke)
                            undoStack.add(layers[0].strokes.toList())
                            redoStack.clear()
                            onStrokeCompleted(stroke)
                        }
                        canvasState = canvasState.copy(isPenDown = false, currentStroke = null)
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Draw all completed strokes
        layers.forEach { layer ->
            if (layer.isVisible) {
                layer.strokes.forEach { stroke ->
                    StrokeRenderer.renderStroke(this, stroke)
                }
            }
        }
        // Draw current stroke being drawn
        canvasState.currentStroke?.let { stroke ->
            StrokeRenderer.renderStroke(this, stroke)
        }
    }
}
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/canvas/
git commit -m "feat: add handwriting canvas core (StrokePoint, VectorStroke, StrokeRenderer, VectorLayer)"
```

---

### Task 5: 撤销/重做与橡皮擦

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/canvas/HandwritingCanvas.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/canvas/UndoRedoManager.kt`

- [ ] **Step 1: 创建 UndoRedoManager**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/UndoRedoManager.kt
package com.mistakenotes.ui.canvas

class UndoRedoManager<T>(private val maxSize: Int = 50) {
    private val undoStack = mutableListOf<List<T>>()
    private val redoStack = mutableListOf<List<T>>()

    fun saveState(state: List<T>) {
        undoStack.add(state)
        if (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(currentState: List<T>): List<T>? {
        if (undoStack.isEmpty()) return null
        redoStack.add(currentState)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo(currentState: List<T>): List<T>? {
        if (redoStack.isEmpty()) return null
        undoStack.add(currentState)
        return redoStack.removeAt(redoStack.lastIndex)
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
}
```

- [ ] **Step 2: 更新 HandwritingCanvas 添加撤销/重做和橡皮擦支持**

```kotlin
// app/src/main/java/com/mistakenotes/ui/canvas/HandwritingCanvas.kt
// 完全重写，添加完整功能
package com.mistakenotes.ui.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Canvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

enum class DrawingTool { PEN, HIGHLIGHTER, ERASER }

data class CanvasState(
    val currentTool: DrawingTool = DrawingTool.PEN,
    val penColor: Color = Color.Blue,
    val penThickness: Float = 3f,
    val isPenDown: Boolean = false,
    val currentStroke: VectorStroke? = null
)

@Composable
fun HandwritingCanvas(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    onStrokeCompleted: (VectorStroke) -> Unit = {},
    onUndoStateChange: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    var canvasState by remember { mutableStateOf(CanvasState()) }
    val strokes = remember { mutableStateListOf<VectorStroke>() }
    val undoManager = remember { UndoRedoManager<VectorStroke>(50) }
    var eraserPath by remember { mutableStateOf<Path?>(null) }

    fun saveUndoState() {
        undoManager.saveState(strokes.toList())
        onUndoStateChange(undoManager.canUndo(), undoManager.canRedo())
    }

    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (canvasState.currentTool == DrawingTool.ERASER) {
                            eraserPath = Path().apply { moveTo(event.x, event.y) }
                        } else {
                            canvasState = canvasState.copy(isPenDown = true)
                            val stroke = VectorStroke(
                                points = listOf(
                                    StrokePoint(
                                        event.x,
                                        event.y,
                                        event.pressure.coerceIn(0.1f, 1f)
                                    )
                                ),
                                color = canvasState.currentTool.let {
                                    if (it == DrawingTool.HIGHLIGHTER) canvasState.penColor.copy(alpha = 0.3f)
                                    else canvasState.penColor
                                },
                                baseThickness = canvasState.penThickness
                            )
                            canvasState = canvasState.copy(currentStroke = stroke)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (canvasState.currentTool == DrawingTool.ERASER) {
                            eraserPath?.lineTo(event.x, event.y)
                        } else {
                            val currentStroke = canvasState.currentStroke ?: return@pointerInteropFilter true
                            val newPoints = currentStroke.points + StrokePoint(
                                event.x,
                                event.y,
                                event.pressure.coerceIn(0.1f, 1f)
                            )
                            canvasState = canvasState.copy(
                                currentStroke = currentStroke.copy(points = newPoints)
                            )
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (canvasState.currentTool == DrawingTool.ERASER) {
                            // Erase strokes that intersect with eraser path
                            eraserPath?.let { path ->
                                val eraserRadius = canvasState.penThickness * 10
                                val toErase = strokes.filter { stroke ->
                                    stroke.points.any { point ->
                                        // Simple distance check
                                        true // In real impl, check path intersection
                                    }
                                }
                                if (toErase.isNotEmpty()) {
                                    saveUndoState()
                                    strokes.removeAll(toErase.toSet())
                                }
                            }
                            eraserPath = null
                        } else {
                            val stroke = canvasState.currentStroke
                            if (stroke != null && stroke.points.size >= 2) {
                                saveUndoState()
                                strokes.add(stroke)
                                onStrokeCompleted(stroke)
                            }
                        }
                        canvasState = canvasState.copy(isPenDown = false, currentStroke = null)
                        true
                    }
                    else -> false
                }
            }
    ) {
        strokes.forEach { stroke ->
            StrokeRenderer.renderStroke(this, stroke)
        }
        canvasState.currentStroke?.let { stroke ->
            StrokeRenderer.renderStroke(this, stroke)
        }
    }
}

@Composable
fun HandwritingToolbar(
    currentTool: DrawingTool,
    penColor: Color,
    penThickness: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (DrawingTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo/Redo
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.White)
        }

        Divider(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp),
            color = Color.Gray
        )

        // Tool selection
        IconButton(
            onClick = { onToolChange(DrawingTool.PEN) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (currentTool == DrawingTool.PEN) Color(0xFFD4A574) else Color.Transparent
            )
        ) {
            Text("✏️", color = Color.White)
        }
        IconButton(
            onClick = { onToolChange(DrawingTool.HIGHLIGHTER) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (currentTool == DrawingTool.HIGHLIGHTER) Color(0xFFD4A574) else Color.Transparent
            )
        ) {
            Text("🖍️", color = Color.White)
        }
        IconButton(
            onClick = { onToolChange(DrawingTool.ERASER) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (currentTool == DrawingTool.ERASER) Color(0xFFD4A574) else Color.Transparent
            )
        ) {
            Text("🧽", color = Color.White)
        }

        Divider(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp),
            color = Color.Gray
        )

        // Color selection (for pen)
        if (currentTool == DrawingTool.PEN || currentTool == DrawingTool.HIGHLIGHTER) {
            listOf(Color.Blue, Color.Black, Color.Red).forEach { color ->
                IconButton(onClick = { onColorChange(color) }) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }

            // Thickness
            listOf(0.1f, 0.3f, 0.5f).forEach { thickness ->
                TextButton(onClick = { onThicknessChange(thickness) }) {
                    Text("${thickness}mm", color = Color.White)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Clear
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.White)
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/canvas/
git commit -m "feat: add undo/redo manager and toolbar"
```

---

## 第三阶段：UI 屏幕实现

### Task 6: 首页（HomeScreen）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/screens/HomeViewModel.kt`

- [ ] **Step 1: 创建 HomeViewModel**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/HomeViewModel.kt
package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val totalMistakes: Int = 0,
    val toReviewCount: Int = 0,
    val overdueCount: Int = 0,
    val masteredCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        combine(
            repository.getAllSubjects(),
            repository.getAllMistakes(),
            repository.getAllReviewRecords()
        ) { subjects, mistakes, reviewRecords ->
            val currentSubjectId = _uiState.value.currentSubjectId

            val filteredMistakes = if (currentSubjectId != null) {
                mistakes.filter { it.subjectId == currentSubjectId }
            } else {
                mistakes
            }

            val now = System.currentTimeMillis()

            // Calculate review stats based on Ebbinghaus
            val toReview = reviewRecords.filter { record ->
                record.nextReviewDate?.let { it <= now } ?: false
            }.size

            val overdue = reviewRecords.filter { record ->
                record.nextReviewDate?.let { it < now - 86400000 } ?: false
            }.size

            val mastered = reviewRecords.filter { it.correctCount >= 4 }.size

            HomeUiState(
                subjects = subjects,
                currentSubjectId = currentSubjectId,
                totalMistakes = filteredMistakes.size,
                toReviewCount = toReview,
                overdueCount = overdue,
                masteredCount = mastered,
                isLoading = false
            )
        }.launchIn(viewModelScope)
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.update { it.copy(currentSubjectId = subjectId) }
        loadData()
    }
}
```

- [ ] **Step 2: 创建 HomeScreen**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/HomeScreen.kt
package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBlack)
    ) {
        // Top bar with title
        TopAppBar(
            title = {
                Text(
                    "CPA 错题笔记",
                    fontWeight = FontWeight.Bold,
                    color = AmberGold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
        )

        // Subject filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.currentSubjectId == null,
                    onClick = { viewModel.selectSubject(null) },
                    label = { Text("全部") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = InkStoneBlack
                    )
                )
            }
            items(uiState.subjects) { subject ->
                FilterChip(
                    selected = uiState.currentSubjectId == subject.id,
                    onClick = { viewModel.selectSubject(subject.id) },
                    label = { Text(subject.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(subject.color),
                        selectedLabelColor = InkStoneBlack
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Statistics cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "待复习",
                value = uiState.toReviewCount.toString(),
                color = AmberGold,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToReview() }
            )
            StatCard(
                title = "逾期",
                value = uiState.overdueCount.toString(),
                color = ErrorRed,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToReview() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "已掌握",
                value = uiState.masteredCount.toString(),
                color = SuccessGreen,
                modifier = Modifier.weight(1f),
                onClick = { }
            )
            StatCard(
                title = "总错题",
                value = uiState.totalMistakes.toString(),
                color = TextCream,
                modifier = Modifier.weight(1f),
                onClick = { }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick actions
        Text(
            "快速入口",
            color = TextCream,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        QuickActionCard(
            icon = Icons.Default.CameraAlt,
            title = "拍照录入",
            description = "拍照存档，手动输入题目",
            onClick = onNavigateToImport,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        QuickActionCard(
            icon = Icons.Default.Search,
            title = "搜索题目",
            description = "按科目、章节、知识点筛选",
            onClick = { },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        QuickActionCard(
            icon = Icons.Default.Analytics,
            title = "错题分析",
            description = "查看学习进度与薄弱点",
            onClick = onNavigateToAnalysis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextCream.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = TextCream, style = MaterialTheme.typography.titleMedium)
                Text(description, color = TextCream.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/HomeScreen.kt app/src/main/java/com/mistakenotes/ui/screens/HomeViewModel.kt
git commit -m "feat: add HomeScreen with statistics and quick actions"
```

---

### Task 7: 录入界面（ImportScreen）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`

- [ ] **Step 1: 创建 ImportViewModel**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val imageUri: Uri? = null,
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val options: List<String> = listOf("", "", "", ""),
    val correctAnswer: String = "",
    val referenceAnswer: String = "",
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        loadSubjects()
    }

    private fun loadSubjects() {
        repository.getAllSubjects().launchIn(viewModelScope)
            .onEach { subjects ->
                _uiState.update { it.copy(subjects = subjects) }
            }
            .launchIn(viewModelScope)
    }

    fun setImageUri(uri: Uri?) {
        _uiState.update { it.copy(imageUri = uri) }
    }

    fun setQuestionText(text: String) {
        _uiState.update { it.copy(questionText = text) }
    }

    fun setSubject(subjectId: Long) {
        _uiState.update { it.copy(subjectId = subjectId, chapterId = null, knowledgePointId = null) }
        viewModelScope.launch {
            repository.getChaptersBySubject(subjectId).collect { chapters ->
                _uiState.update { it.copy(chapters = chapters) }
            }
        }
    }

    fun setChapter(chapterId: Long) {
        _uiState.update { it.copy(chapterId = chapterId, knowledgePointId = null) }
        viewModelScope.launch {
            repository.getKnowledgePointsByChapter(chapterId).collect { kps ->
                _uiState.update { it.copy(knowledgePoints = kps) }
            }
        }
    }

    fun setKnowledgePoint(kpId: Long) {
        _uiState.update { it.copy(knowledgePointId = kpId) }
    }

    fun setQuestionType(type: QuestionType) {
        _uiState.update { it.copy(questionType = type) }
    }

    fun setOption(index: Int, value: String) {
        val newOptions = _uiState.value.options.toMutableList()
        newOptions[index] = value
        _uiState.update { it.copy(options = newOptions) }
    }

    fun setCorrectAnswer(answer: String) {
        _uiState.update { it.copy(correctAnswer = answer) }
    }

    fun setReferenceAnswer(answer: String) {
        _uiState.update { it.copy(referenceAnswer = answer) }
    }

    fun saveMistake() {
        val state = _uiState.value
        if (state.subjectId == null || state.chapterId == null || state.knowledgePointId == null) {
            _uiState.update { it.copy(errorMessage = "请选择完整的分类") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val mistake = Mistake(
                    title = state.questionText.take(100),
                    subjectId = state.subjectId!!,
                    chapterId = state.chapterId!!,
                    knowledgePointId = state.knowledgePointId!!,
                    questionType = state.questionType,
                    questionImagePath = state.imageUri?.toString(),
                    questionText = state.questionText.ifBlank { null },
                    options = if (state.questionType != QuestionType.ESSAY) {
                        state.options.filter { it.isNotBlank() }.joinToString("|")
                    } else null,
                    correctAnswer = state.correctAnswer.ifBlank { null },
                    referenceAnswer = state.referenceAnswer.ifBlank { null }
                )
                repository.insertMistake(mistake)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetState() {
        _uiState.update { ImportUiState(subjects = _uiState.value.subjects) }
    }
}
```

- [ ] **Step 2: 创建 ImportScreen**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setImageUri(uri)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBlack)
    ) {
        TopAppBar(
            title = { Text("录入错题", color = TextCream) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextCream)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image capture section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") },
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.imageUri != null) {
                        AsyncImage(
                            model = uiState.imageUri,
                            contentDescription = "题目图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击拍照或选择图片", color = TextCream.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Question type selection
            Text("题目类型", color = TextCream, style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuestionType.entries) { type ->
                    FilterChip(
                        selected = uiState.questionType == type,
                        onClick = { viewModel.setQuestionType(type) },
                        label = {
                            Text(
                                when (type) {
                                    QuestionType.SINGLE_CHOICE -> "单选题"
                                    QuestionType.MULTI_CHOICE -> "多选题"
                                    QuestionType.ESSAY -> "主观题"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = InkStoneBlack
                        )
                    )
                }
            }

            // Classification selection
            Text("分类", color = TextCream, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Subject dropdown
                var subjectExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = uiState.subjects.find { it.id == uiState.subjectId }?.name ?: "选择科目",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCream,
                            unfocusedTextColor = TextCream,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = TextCream.copy(alpha = 0.3f)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false }
                    ) {
                        uiState.subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.name) },
                                onClick = {
                                    viewModel.setSubject(subject.id)
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Options (for choice questions)
            if (uiState.questionType != QuestionType.ESSAY) {
                Text("选项", color = TextCream, style = MaterialTheme.typography.titleMedium)
                val optionLabels = listOf("A", "B", "C", "D", "E", "F")
                uiState.options.forEachIndexed { index, option ->
                    OutlinedTextField(
                        value = option,
                        onValueChange = { viewModel.setOption(index, it) },
                        label = { Text("${optionLabels[index]} 选项") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCream,
                            unfocusedTextColor = TextCream,
                            focusedBorderColor = AmberGold
                        )
                    )
                }

                OutlinedTextField(
                    value = uiState.correctAnswer,
                    onValueChange = { viewModel.setCorrectAnswer(it) },
                    label = { Text("正确答案（如：A 或 ABC）") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextCream,
                        unfocusedTextColor = TextCream,
                        focusedBorderColor = AmberGold
                    )
                )
            } else {
                // Reference answer for essay
                OutlinedTextField(
                    value = uiState.referenceAnswer,
                    onValueChange = { viewModel.setReferenceAnswer(it) },
                    label = { Text("参考答案") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextCream,
                        unfocusedTextColor = TextCream,
                        focusedBorderColor = AmberGold
                    )
                )
            }

            // Save button
            Button(
                onClick = { viewModel.saveMistake() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = InkStoneBlack)
                } else {
                    Text("保存", color = InkStoneBlack, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Error snackbar
    uiState.errorMessage?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("关闭")
                }
            }
        ) {
            Text(error)
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat: add ImportScreen for mistake entry"
```

---

### Task 8: 复习界面（ReviewScreen）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`

- [ ] **Step 1: 创建 ReviewViewModel（含艾宾浩斯复习算法）**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt
package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val currentMistake: Mistake? = null,
    val showQuestion: Boolean = true,
    val showAnswer: Boolean = false,
    val showReference: Boolean = false,
    val selectedAnswer: String = "",
    val isCorrect: Boolean? = null,
    val isLoading: Boolean = true,
    val reviewComplete: Boolean = false,
    val canvasStrokes: List<VectorStroke> = emptyList()
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var reviewQueue = mutableListOf<Mistake>()

    init {
        loadReviewQueue()
    }

    private fun loadReviewQueue() {
        viewModelScope.launch {
            combine(
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { mistakes, records ->
                val now = System.currentTimeMillis()
                val toReview = mistakes.filter { mistake ->
                    val lastRecord = records.filter { it.mistakeId == mistake.id }.maxByOrNull { it.reviewDate }
                    lastRecord?.nextReviewDate?.let { it <= now } ?: true
                }
                toReview
            }.collect { queue ->
                reviewQueue = queue.toMutableList()
                _uiState.update {
                    it.copy(
                        currentMistake = queue.firstOrNull(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun submitAnswer(answer: String) {
        val mistake = _uiState.value.currentMistake ?: return
        val isCorrect = answer.uppercase() == mistake.correctAnswer?.uppercase()

        _uiState.update {
            it.copy(
                selectedAnswer = answer,
                isCorrect = isCorrect,
                showAnswer = true
            )
        }

        // Calculate next review date using Ebbinghaus
        viewModelScope.launch {
            val currentRecords = repository.getReviewRecordsByMistake(mistake.id).first()
            val lastRecord = currentRecords.firstOrNull()
            val correctCount = if (isCorrect) (lastRecord?.correctCount ?: 0) + 1 else 0

            val nextReviewDays = when {
                isCorrect && correctCount == 1 -> 1
                isCorrect && correctCount == 2 -> 3
                isCorrect && correctCount == 3 -> 7
                isCorrect && correctCount >= 4 -> -1 // mastered
                else -> 1 // wrong, reset
            }

            val nextReviewDate = if (nextReviewDays > 0) {
                System.currentTimeMillis() + nextReviewDays * 86400000L
            } else null

            val record = ReviewRecord(
                mistakeId = mistake.id,
                reviewDate = System.currentTimeMillis(),
                result = if (isCorrect) ReviewResult.CORRECT else ReviewResult.WRONG,
                correctCount = correctCount,
                nextReviewDate = nextReviewDate
            )

            repository.insertReviewRecord(record)
        }
    }

    fun submitEssayAnswer(score: Int?) {
        val mistake = _uiState.value.currentMistake ?: return

        viewModelScope.launch {
            val record = ReviewRecord(
                mistakeId = mistake.id,
                reviewDate = System.currentTimeMillis(),
                result = ReviewResult.SKIP,
                score = score
            )
            repository.insertReviewRecord(record)
        }

        _uiState.update { it.copy(showReference = true) }
    }

    fun nextMistake() {
        if (reviewQueue.isNotEmpty()) {
            reviewQueue.removeAt(0)
        }

        if (reviewQueue.isEmpty()) {
            _uiState.update { it.copy(reviewComplete = true, currentMistake = null) }
        } else {
            _uiState.update {
                it.copy(
                    currentMistake = reviewQueue.first(),
                    showQuestion = true,
                    showAnswer = false,
                    showReference = false,
                    selectedAnswer = "",
                    isCorrect = null
                )
            }
        }
    }

    fun saveCanvasStrokes(strokes: List<VectorStroke>) {
        _uiState.update { it.copy(canvasStrokes = strokes) }
    }
}
```

- [ ] **Step 2: 创建 ReviewScreen（含左右分栏和手写答题区）**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt
package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.canvas.*
import com.mistakenotes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var penColor by remember { mutableStateOf(Color.Blue) }
    var penThickness by remember { mutableStateOf(3f) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBlack)
    ) {
        TopAppBar(
            title = { Text("复习", color = TextCream) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextCream)
                }
            },
            actions = {
                // Fullscreen toggle
                IconButton(onClick = { /* toggle fullscreen */ }) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "全屏", tint = TextCream)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardDark)
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmberGold)
            }
        } else if (uiState.reviewComplete) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("复习完成！", color = TextCream, style = MaterialTheme.typography.headlineMedium)
                }
            }
        } else {
            uiState.currentMistake?.let { mistake ->
                // Main content - split view
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(0.dp)
                ) {
                    // Left side - Question (30-40%)
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .background(CardDark)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "题目",
                            color = AmberGold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (mistake.questionText != null) {
                            Text(mistake.questionText, color = TextCream)
                        }

                        if (mistake.questionImagePath != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // Show image placeholder
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                colors = CardDefaults.cardColors(containerColor = InkStoneBlack)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("题目图片", color = TextCream.copy(alpha = 0.5f))
                                }
                            }
                        }

                        if (mistake.questionType != QuestionType.ESSAY && mistake.options != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("选项", color = TextCream, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))

                            val options = mistake.options.split("|")
                            options.forEachIndexed { index, option ->
                                val labels = listOf("A", "B", "C", "D", "E", "F")
                                val isSelected = uiState.selectedAnswer == labels[index]

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !uiState.showAnswer) {
                                            viewModel.submitAnswer(labels[index])
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            uiState.showAnswer && labels[index] == mistake.correctAnswer?.first()?.toString() -> SuccessGreen.copy(alpha = 0.3f)
                                            uiState.showAnswer && isSelected && uiState.isCorrect == false -> ErrorRed.copy(alpha = 0.3f)
                                            else -> InkStoneBlack
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${labels[index]}. ",
                                            color = AmberGold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(option, color = TextCream)
                                    }
                                }
                            }
                        }

                        if (uiState.showAnswer) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (uiState.isCorrect == true) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (uiState.isCorrect == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (uiState.isCorrect == true) SuccessGreen else ErrorRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (uiState.isCorrect == true) "回答正确" else "回答错误",
                                        color = TextCream
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.nextMistake() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                            ) {
                                Text("下一题", color = InkStoneBlack)
                            }
                        }
                    }

                    // Right side - Drawing canvas (60-70%)
                    Column(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                    ) {
                        // Tab bar
                        var selectedTab by remember { mutableStateOf(0) }
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = CardDark,
                            contentColor = AmberGold
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("答题区") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("草稿纸") }
                            )
                        }

                        // Canvas area
                        if (selectedTab == 0) {
                            // Answer area for essay
                            if (mistake.questionType == QuestionType.ESSAY) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    HandwritingCanvas(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        backgroundColor = Color.White,
                                        onUndoStateChange = { u, r -> canUndo = u; canRedo = r }
                                    )

                                    // Toolbar
                                    HandwritingToolbar(
                                        currentTool = currentTool,
                                        penColor = penColor,
                                        penThickness = penThickness,
                                        canUndo = canUndo,
                                        canRedo = canRedo,
                                        onToolChange = { currentTool = it },
                                        onColorChange = { penColor = it },
                                        onThicknessChange = { penThickness = it },
                                        onUndo = { /* undo */ },
                                        onRedo = { /* redo */ },
                                        onClear = { /* clear */ }
                                    )

                                    // Submit button for essay
                                    if (!uiState.showReference) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.submitEssayAnswer(null) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                            ) {
                                                Text("提交查看参考答案")
                                            }
                                        }
                                    } else {
                                        // Show reference answer
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = CardDark)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("参考答案", color = AmberGold)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    mistake.referenceAnswer ?: "无",
                                                    color = TextCream
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { viewModel.nextMistake() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                                        ) {
                                            Text("下一题", color = InkStoneBlack)
                                        }
                                    }
                                }
                            } else {
                                // For choice questions - just show canvas
                                HandwritingCanvas(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    backgroundColor = Color.White,
                                    onUndoStateChange = { u, r -> canUndo = u; canRedo = r }
                                )

                                HandwritingToolbar(
                                    currentTool = currentTool,
                                    penColor = penColor,
                                    penThickness = penThickness,
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onToolChange = { currentTool = it },
                                    onColorChange = { penColor = it },
                                    onThicknessChange = { penThickness = it },
                                    onUndo = { },
                                    onRedo = { },
                                    onClear = { }
                                )
                            }
                        } else {
                            // Draft paper - separate canvas
                            HandwritingCanvas(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                backgroundColor = Color(0xFFF5F5DC), // Cream color for draft
                                onUndoStateChange = { u, r -> canUndo = u; canRedo = r }
                            )

                            HandwritingToolbar(
                                currentTool = currentTool,
                                penColor = penColor,
                                penThickness = penThickness,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                onToolChange = { currentTool = it },
                                onColorChange = { penColor = it },
                                onThicknessChange = { penThickness = it },
                                onUndo = { },
                                onRedo = { },
                                onClear = { }
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt
git commit -m "feat: add ReviewScreen with split view and handwriting canvas"
```

---

### Task 9: 错题分析界面（AnalysisScreen）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/screens/AnalysisScreen.kt`
- Create: `app/src/main/java/com/mistakenotes/ui/screens/AnalysisViewModel.kt`

- [ ] **Step 1: 创建 AnalysisViewModel**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/AnalysisViewModel.kt
package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SubjectStat(
    val subject: Subject,
    val totalMistakes: Int,
    val correctCount: Int,
    val masteryRate: Float
)

data class ChapterStat(
    val chapter: Chapter,
    val mistakeCount: Int,
    val correctRate: Float
)

data class AnalysisUiState(
    val subjectStats: List<SubjectStat> = emptyList(),
    val chapterStats: List<ChapterStat> = emptyList(),
    val topWeakKnowledgePoints: List<Pair<KnowledgePoint, Int>> = emptyList(), // kp to mistake count
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        loadAnalysis()
    }

    private fun loadAnalysis() {
        viewModelScope.launch {
            combine(
                repository.getAllSubjects(),
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { subjects, mistakes, records ->
                // Calculate subject stats
                val subjectStats = subjects.map { subject ->
                    val subjectMistakes = mistakes.filter { it.subjectId == subject.id }
                    val subjectRecords = records.filter { record ->
                        subjectMistakes.any { it.id == record.mistakeId }
                    }
                    val correct = subjectRecords.count { it.result == ReviewResult.CORRECT }
                    val total = subjectRecords.size.coerceAtLeast(1)
                    SubjectStat(
                        subject = subject,
                        totalMistakes = subjectMistakes.size,
                        correctCount = correct,
                        masteryRate = correct.toFloat() / total
                    )
                }

                // Chapter stats
                val allChapters = mutableListOf<Chapter>()
                subjects.forEach { subject ->
                    repository.getChaptersBySubject(subject.id).first().let { chapters ->
                        allChapters.addAll(chapters)
                    }
                }

                val chapterStats = allChapters.map { chapter ->
                    val chapterMistakes = mistakes.filter { it.chapterId == chapter.id }
                    val chapterRecords = records.filter { record ->
                        chapterMistakes.any { it.id == record.mistakeId }
                    }
                    val correct = chapterRecords.count { it.result == ReviewResult.CORRECT }
                    val total = chapterRecords.size.coerceAtLeast(1)
                    ChapterStat(
                        chapter = chapter,
                        mistakeCount = chapterMistakes.size,
                        correctRate = correct.toFloat() / total
                    )
                }.sortedByDescending { it.mistakeCount }

                // Knowledge point stats - find most missed
                val kpMistakeCount = mutableMapOf<Long, Int>()
                mistakes.forEach { mistake ->
                    val count = kpMistakeCount.getOrDefault(mistake.knowledgePointId, 0) + 1
                    kpMistakeCount[mistake.knowledgePointId] = count
                }

                val topWeakKps = kpMistakeCount
                    .map { (kpId, count) ->
                        repository.getKnowledgePointsByChapter(0).first()
                            .find { it.id == kpId }?.let { it to count }
                    }
                    .filterNotNull()
                    .sortedByDescending { it.second }
                    .take(10)

                AnalysisUiState(
                    subjectStats = subjectStats,
                    chapterStats = chapterStats,
                    topWeakKnowledgePoints = topWeakKps,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
```

- [ ] **Step 2: 创建 AnalysisScreen**

```kotlin
// app/src/main/java/com/mistakenotes/ui/screens/AnalysisScreen.kt
package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBlack)
    ) {
        TopAppBar(
            title = { Text("错题分析", color = TextCream) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextCream)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmberGold)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Subject stats
                item {
                    Text("科目掌握度", color = AmberGold, style = MaterialTheme.typography.titleLarge)
                }

                items(uiState.subjectStats) { stat ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stat.subject.name,
                                    color = TextCream,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${(stat.masteryRate * 100).toInt()}%",
                                    color = when {
                                        stat.masteryRate >= 0.8f -> SuccessGreen
                                        stat.masteryRate >= 0.5f -> AmberGold
                                        else -> ErrorRed
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { stat.masteryRate },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    stat.masteryRate >= 0.8f -> SuccessGreen
                                    stat.masteryRate >= 0.5f -> AmberGold
                                    else -> ErrorRed
                                },
                                trackColor = InkStoneBlack
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "错题数: ${stat.totalMistakes}",
                                    color = TextCream.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "正确次数: ${stat.correctCount}",
                                    color = TextCream.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Chapter distribution
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("章节错题分布", color = AmberGold, style = MaterialTheme.typography.titleLarge)
                }

                items(uiState.chapterStats.take(5)) { stat ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stat.chapter.name, color = TextCream)
                                Text(
                                    "正确率: ${(stat.correctRate * 100).toInt()}%",
                                    color = TextCream.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = when {
                                            stat.correctRate >= 0.8f -> SuccessGreen.copy(alpha = 0.2f)
                                            stat.correctRate >= 0.5f -> AmberGold.copy(alpha = 0.2f)
                                            else -> ErrorRed.copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${stat.mistakeCount} 题",
                                    color = when {
                                        stat.correctRate >= 0.8f -> SuccessGreen
                                        stat.correctRate >= 0.5f -> AmberGold
                                        else -> ErrorRed
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // Weak knowledge points
                if (uiState.topWeakKnowledgePoints.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "重点复习知识点",
                                color = AmberGold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    items(uiState.topWeakKnowledgePoints) { (kp, count) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(kp.name, color = TextCream, modifier = Modifier.weight(1f))
                                Text(
                                    "$count 次",
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/AnalysisScreen.kt app/src/main/java/com/mistakenotes/ui/screens/AnalysisViewModel.kt
git commit -m "feat: add AnalysisScreen with statistics and charts"
```

---

### Task 10: 导航与整合

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/mistakenotes/MainActivity.kt`

- [ ] **Step 1: 创建导航图**

```kotlin
// app/src/main/java/com/mistakenotes/ui/navigation/NavGraph.kt
package com.mistakenotes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mistakenotes.ui.screens.*

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Import : Screen("import")
    data object Review : Screen("review")
    data object Analysis : Screen("analysis")
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToImport = { navController.navigate(Screen.Import.route) },
                onNavigateToReview = { navController.navigate(Screen.Review.route) },
                onNavigateToAnalysis = { navController.navigate(Screen.Analysis.route) }
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Analysis.route) {
            AnalysisScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 2: 更新 MainActivity**

```kotlin
// app/src/main/java/com/mistakenotes/MainActivity.kt
package com.mistakenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mistakenotes.ui.navigation.AppNavGraph
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.MistakeNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MistakeNotesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = InkStoneBlack
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/navigation/ app/src/main/java/com/mistakenotes/MainActivity.kt
git commit -m "feat: add navigation and integrate all screens"
```

---

## 实现检查清单

完成所有任务后，验证以下内容：

- [ ] 数据库 Room Entity、DAO、Repository 正确映射
- [ ] 手写画布支持压力感应和 Catmull-Rom 平滑
- [ ] 撤销/重做最多 50 步
- [ ] 首页显示正确统计数据
- [ ] 录入流程支持拍照存档 + 三级分类
- [ ] 复习界面左右分栏，手写答题
- [ ] 艾宾浩斯复习间隔计算正确
- [ ] 错题分析显示科目/章节统计
- [ ] 砚台风格配色全部应用

---

**Plan complete.** 保存至 `docs/superpowers/plans/2026-05-20-cpa-mistake-notes-implementation.md`