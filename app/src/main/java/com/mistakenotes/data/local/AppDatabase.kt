package com.mistakenotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SubjectEntity::class,
        ChapterEntity::class,
        KnowledgePointEntity::class,
        MistakeEntity::class,
        ReviewRecordEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun chapterDao(): ChapterDao
    abstract fun knowledgePointDao(): KnowledgePointDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun reviewRecordDao(): ReviewRecordDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = OFF")

                // Step 1: Delete all old chapters (FKs off = no cascade)
                db.execSQL("DELETE FROM chapters WHERE subjectId IN (1, 2, 3, 4, 5)")

                // Step 2: Insert all new chapters
                val newChapters = listOf(
                    // 会计 (subjectId=1, id 1-30)
                    Triple(1L, 1L, "第一章 总论"),
                    Triple(2L, 1L, "第二章 存货"),
                    Triple(3L, 1L, "第三章 固定资产"),
                    Triple(4L, 1L, "第四章 无形资产"),
                    Triple(5L, 1L, "第五章 投资性房地产"),
                    Triple(6L, 1L, "第六章 长投与合营安排☆"),
                    Triple(7L, 1L, "第七章 资产减值"),
                    Triple(8L, 1L, "第八章 负债"),
                    Triple(9L, 1L, "第九章 职工薪酬"),
                    Triple(10L, 1L, "第十章 股份支付☆"),
                    Triple(11L, 1L, "第十一章 借款费用"),
                    Triple(12L, 1L, "第十二章 或有事项"),
                    Triple(13L, 1L, "第十三章 金融工具☆"),
                    Triple(14L, 1L, "第十四章 租赁☆"),
                    Triple(15L, 1L, "第十五章 持有待售和终止经营"),
                    Triple(16L, 1L, "第十六章 所有者权益"),
                    Triple(17L, 1L, "第十七章 收入费用利润☆"),
                    Triple(18L, 1L, "第十八章 政府补助"),
                    Triple(19L, 1L, "第十九章 所得税☆"),
                    Triple(20L, 1L, "第二十章 非货币性资产交换"),
                    Triple(21L, 1L, "第二十一章 债务重组"),
                    Triple(22L, 1L, "第二十二章 外币折算"),
                    Triple(23L, 1L, "第二十三章 财务报告"),
                    Triple(24L, 1L, "第二十四章 会计政策、估计和差错更正"),
                    Triple(25L, 1L, "第二十五章 资产负债表日后事项"),
                    Triple(26L, 1L, "第二十六章 企业合并"),
                    Triple(27L, 1L, "第二十七章 合并财务报表☆"),
                    Triple(28L, 1L, "第二十八章 每股收益"),
                    Triple(29L, 1L, "第二十九章 公允价值计量"),
                    Triple(30L, 1L, "第三十章 政府及民间非营利组织会计"),
                    // 审计 (subjectId=2, id 31-53)
                    Triple(31L, 2L, "第一章 总览"),
                    Triple(32L, 2L, "第二章 审计计划"),
                    Triple(33L, 2L, "第三章 审计证据"),
                    Triple(34L, 2L, "第四章 审计抽样方法"),
                    Triple(35L, 2L, "第五章 信息技术对审计的影响"),
                    Triple(36L, 2L, "第六章 风险评估"),
                    Triple(37L, 2L, "第七章 风险应对"),
                    Triple(38L, 2L, "第八章 销售与收款循环的审计"),
                    Triple(39L, 2L, "第九章 采购与付款循环的审计"),
                    Triple(40L, 2L, "第十章 生产与存货循环的审计"),
                    Triple(41L, 2L, "第十一章 货币资金审计"),
                    Triple(42L, 2L, "第十二章 审计沟通"),
                    Triple(43L, 2L, "第十三章 注册会计师利用他人的工作"),
                    Triple(44L, 2L, "第十四章 审计报告"),
                    Triple(45L, 2L, "第十五章 审计沟通"),
                    Triple(46L, 2L, "第十六章 注册会计师的质量管理"),
                    Triple(47L, 2L, "第十七章 职业道德"),
                    Triple(48L, 2L, "第十八章 持续经营"),
                    Triple(49L, 2L, "第十九章 舞弊"),
                    Triple(50L, 2L, "第二十章 审计沟通"),
                    Triple(51L, 2L, "第二十一章 审计监管"),
                    Triple(52L, 2L, "第二十二章 审计证据"),
                    Triple(53L, 2L, "第二十三章 审计档案"),
                    // 财务成本管理 (subjectId=3, id 54-75)
                    Triple(54L, 3L, "第一章 财务管理基本原理"),
                    Triple(55L, 3L, "第二章 财务报表分析与财务预测"),
                    Triple(56L, 3L, "第三章 增长率与资金需求"),
                    Triple(57L, 3L, "第四章 价值评估基础"),
                    Triple(58L, 3L, "第五章 投资项目资本预算"),
                    Triple(59L, 3L, "第六章 债券和股票估值"),
                    Triple(60L, 3L, "第七章 期权价值评估"),
                    Triple(61L, 3L, "第八章 企业价值评估"),
                    Triple(62L, 3L, "第九章 资本结构决策"),
                    Triple(63L, 3L, "第十章 股利分配决策"),
                    Triple(64L, 3L, "第十一章 长期筹资决策"),
                    Triple(65L, 3L, "第十二章 营运资本管理"),
                    Triple(66L, 3L, "第十三章 产品成本计算"),
                    Triple(67L, 3L, "第十四章 标准成本法"),
                    Triple(68L, 3L, "第十五章 作业成本法"),
                    Triple(69L, 3L, "第十六章 全面预算"),
                    Triple(70L, 3L, "第十七章 本量利分析"),
                    Triple(71L, 3L, "第十八章 短期经营决策"),
                    Triple(72L, 3L, "第十九章 业绩评价"),
                    Triple(73L, 3L, "第二十章 责任会计"),
                    Triple(74L, 3L, "第二十一章 成本性态分析"),
                    Triple(75L, 3L, "第二十二章 资金时间价值"),
                    // 税法 (subjectId=4, id 76-89)
                    Triple(76L, 4L, "第一章 税法总论"),
                    Triple(77L, 4L, "第二章 增值税法"),
                    Triple(78L, 4L, "第三章 消费税法"),
                    Triple(79L, 4L, "第四章 企业所得税法"),
                    Triple(80L, 4L, "第五章 个人所得税法"),
                    Triple(81L, 4L, "第六章 关税法和船舶吨税法"),
                    Triple(82L, 4L, "第七章 资源税法和环境保护税法"),
                    Triple(83L, 4L, "第八章 房产税法、契税法和土地增值税法"),
                    Triple(84L, 4L, "第九章 车辆购置税法、车船税法和印花税法"),
                    Triple(85L, 4L, "第十章 国际税收"),
                    Triple(86L, 4L, "第十一章 税收征收管理法"),
                    Triple(87L, 4L, "第十二章 税务行政法制"),
                    Triple(88L, 4L, "第十三章 税务代理"),
                    Triple(89L, 4L, "第十四章 税务筹划"),
                    // 经济法 (subjectId=5, id 90-101)
                    Triple(90L, 5L, "第一章 法律基本原理"),
                    Triple(91L, 5L, "第二章 基本民事法律制度"),
                    Triple(92L, 5L, "第三章 物权法律制度"),
                    Triple(93L, 5L, "第四章 合同法律制度"),
                    Triple(94L, 5L, "第五章 合伙企业法律制度"),
                    Triple(95L, 5L, "第六章 公司法律制度"),
                    Triple(96L, 5L, "第七章 证券法律制度"),
                    Triple(97L, 5L, "第八章 企业破产法律制度"),
                    Triple(98L, 5L, "第九章 票据法律制度"),
                    Triple(99L, 5L, "第十章 企业国有资产法律制度"),
                    Triple(100L, 5L, "第十一章 反垄断法律制度"),
                    Triple(101L, 5L, "第十二章 涉外经济法律制度")
                )
                newChapters.forEach { (id, subjectId, name) ->
                    db.execSQL(
                        "INSERT INTO chapters (id, subjectId, name, `order`) VALUES (?, ?, ?, ?)",
                        arrayOf(id, subjectId, name, 0)
                    )
                }
                db.execSQL("""
                    UPDATE chapters SET "order" = (
                        SELECT COUNT(*) FROM chapters c2
                        WHERE c2.subjectId = chapters.subjectId AND c2.id <= chapters.id
                    )
                """.trimIndent())

                // Step 3: Remap mistakes.chapterId (old → new)
                // 审计/财管/税法: simple +2 shift
                for (range in listOf(29L to 51L, 52L to 73L, 74L to 87L)) {
                    db.execSQL("UPDATE mistakes SET chapterId = chapterId + 2 WHERE chapterId BETWEEN ${range.first} AND ${range.second}")
                    db.execSQL("UPDATE knowledge_points SET chapterId = chapterId + 2 WHERE chapterId BETWEEN ${range.first} AND ${range.second}")
                }
                // 会计: complex mapping
                db.execSQL("""
                    UPDATE mistakes SET chapterId = CASE chapterId
                        WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 WHEN 4 THEN 4
                        WHEN 5 THEN 5 WHEN 6 THEN 6 WHEN 7 THEN 7 WHEN 8 THEN 13
                        WHEN 9 THEN 9 WHEN 10 THEN 11 WHEN 11 THEN 10 WHEN 12 THEN 12
                        WHEN 13 THEN 14 WHEN 14 THEN 15 WHEN 15 THEN 16 WHEN 16 THEN 17
                        WHEN 17 THEN 18 WHEN 18 THEN 19 WHEN 19 THEN 22 WHEN 20 THEN 24
                        WHEN 21 THEN 25 WHEN 22 THEN 26 WHEN 23 THEN 27 WHEN 24 THEN 28
                        WHEN 25 THEN 29 WHEN 26 THEN 30 WHEN 27 THEN 30 WHEN 28 THEN 1
                        ELSE chapterId
                    END WHERE chapterId BETWEEN 1 AND 28
                """.trimIndent())
                db.execSQL("""
                    UPDATE knowledge_points SET chapterId = CASE chapterId
                        WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 WHEN 4 THEN 4
                        WHEN 5 THEN 5 WHEN 6 THEN 6 WHEN 7 THEN 7 WHEN 8 THEN 13
                        WHEN 9 THEN 9 WHEN 10 THEN 11 WHEN 11 THEN 10 WHEN 12 THEN 12
                        WHEN 13 THEN 14 WHEN 14 THEN 15 WHEN 15 THEN 16 WHEN 16 THEN 17
                        WHEN 17 THEN 18 WHEN 18 THEN 19 WHEN 19 THEN 22 WHEN 20 THEN 24
                        WHEN 21 THEN 25 WHEN 22 THEN 26 WHEN 23 THEN 27 WHEN 24 THEN 28
                        WHEN 25 THEN 29 WHEN 26 THEN 30 WHEN 27 THEN 30 WHEN 28 THEN 1
                        ELSE chapterId
                    END WHERE chapterId BETWEEN 1 AND 28
                """.trimIndent())
                // 经济法: complex mapping
                db.execSQL("""
                    UPDATE mistakes SET chapterId = CASE chapterId
                        WHEN 88 THEN 90 WHEN 89 THEN 91 WHEN 90 THEN 92 WHEN 91 THEN 93
                        WHEN 92 THEN 94 WHEN 93 THEN 95 WHEN 94 THEN 96 WHEN 95 THEN 97
                        WHEN 96 THEN 98 WHEN 97 THEN 99 WHEN 98 THEN 100 WHEN 99 THEN 101
                        WHEN 100 THEN 90 WHEN 101 THEN 90
                        ELSE chapterId
                    END WHERE chapterId BETWEEN 88 AND 101
                """.trimIndent())
                db.execSQL("""
                    UPDATE knowledge_points SET chapterId = CASE chapterId
                        WHEN 88 THEN 90 WHEN 89 THEN 91 WHEN 90 THEN 92 WHEN 91 THEN 93
                        WHEN 92 THEN 94 WHEN 93 THEN 95 WHEN 94 THEN 96 WHEN 95 THEN 97
                        WHEN 96 THEN 98 WHEN 97 THEN 99 WHEN 98 THEN 100 WHEN 99 THEN 101
                        WHEN 100 THEN 90 WHEN 101 THEN 90
                        ELSE chapterId
                    END WHERE chapterId BETWEEN 88 AND 101
                """.trimIndent())

                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE chapters SET name = '第一章 总览' WHERE id = 31")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE chapters SET name = '第一章 审计概述' WHERE id = 31")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE chapters SET name = '第一章 总览' WHERE id = 31")
            }
        }

        val prepopulateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // CPA 考试六科（彩虹色）
                val subjects = listOf(
                    Triple(1L, "会计", 0xFFE74C3CL),             // 红
                    Triple(2L, "审计", 0xFFE67E22L),             // 橙
                    Triple(3L, "财务成本管理", 0xFFF1C40FL),     // 黄
                    Triple(4L, "税法", 0xFF2ECC71L),             // 绿
                    Triple(5L, "经济法", 0xFF3498DBL),           // 蓝
                    Triple(6L, "公司战略与风险管理", 0xFF9B59B6L) // 紫
                )
                subjects.forEach { (id, name, color) ->
                    db.execSQL(
                        "INSERT INTO subjects (id, name, color) VALUES (?, ?, ?)",
                        arrayOf(id, name, color)
                    )
                }

                // CPA 考试章节 - Triple(章节ID, 科目ID, 章节名称)
                val chapters = listOf(
                    // 会计 (subjectId=1, id 1-30)
                    Triple(1L, 1L, "第一章 总论"),
                    Triple(2L, 1L, "第二章 存货"),
                    Triple(3L, 1L, "第三章 固定资产"),
                    Triple(4L, 1L, "第四章 无形资产"),
                    Triple(5L, 1L, "第五章 投资性房地产"),
                    Triple(6L, 1L, "第六章 长投与合营安排☆"),
                    Triple(7L, 1L, "第七章 资产减值"),
                    Triple(8L, 1L, "第八章 负债"),
                    Triple(9L, 1L, "第九章 职工薪酬"),
                    Triple(10L, 1L, "第十章 股份支付☆"),
                    Triple(11L, 1L, "第十一章 借款费用"),
                    Triple(12L, 1L, "第十二章 或有事项"),
                    Triple(13L, 1L, "第十三章 金融工具☆"),
                    Triple(14L, 1L, "第十四章 租赁☆"),
                    Triple(15L, 1L, "第十五章 持有待售和终止经营"),
                    Triple(16L, 1L, "第十六章 所有者权益"),
                    Triple(17L, 1L, "第十七章 收入费用利润☆"),
                    Triple(18L, 1L, "第十八章 政府补助"),
                    Triple(19L, 1L, "第十九章 所得税☆"),
                    Triple(20L, 1L, "第二十章 非货币性资产交换"),
                    Triple(21L, 1L, "第二十一章 债务重组"),
                    Triple(22L, 1L, "第二十二章 外币折算"),
                    Triple(23L, 1L, "第二十三章 财务报告"),
                    Triple(24L, 1L, "第二十四章 会计政策、估计和差错更正"),
                    Triple(25L, 1L, "第二十五章 资产负债表日后事项"),
                    Triple(26L, 1L, "第二十六章 企业合并"),
                    Triple(27L, 1L, "第二十七章 合并财务报表☆"),
                    Triple(28L, 1L, "第二十八章 每股收益"),
                    Triple(29L, 1L, "第二十九章 公允价值计量"),
                    Triple(30L, 1L, "第三十章 政府及民间非营利组织会计"),

                    // 审计 (subjectId=2, id 31-53)
                    Triple(31L, 2L, "第一章 总览"),
                    Triple(32L, 2L, "第二章 审计计划"),
                    Triple(33L, 2L, "第三章 审计证据"),
                    Triple(34L, 2L, "第四章 审计抽样方法"),
                    Triple(35L, 2L, "第五章 信息技术对审计的影响"),
                    Triple(36L, 2L, "第六章 风险评估"),
                    Triple(37L, 2L, "第七章 风险应对"),
                    Triple(38L, 2L, "第八章 销售与收款循环的审计"),
                    Triple(39L, 2L, "第九章 采购与付款循环的审计"),
                    Triple(40L, 2L, "第十章 生产与存货循环的审计"),
                    Triple(41L, 2L, "第十一章 货币资金审计"),
                    Triple(42L, 2L, "第十二章 审计沟通"),
                    Triple(43L, 2L, "第十三章 注册会计师利用他人的工作"),
                    Triple(44L, 2L, "第十四章 审计报告"),
                    Triple(45L, 2L, "第十五章 审计沟通"),
                    Triple(46L, 2L, "第十六章 注册会计师的质量管理"),
                    Triple(47L, 2L, "第十七章 职业道德"),
                    Triple(48L, 2L, "第十八章 持续经营"),
                    Triple(49L, 2L, "第十九章 舞弊"),
                    Triple(50L, 2L, "第二十章 审计沟通"),
                    Triple(51L, 2L, "第二十一章 审计监管"),
                    Triple(52L, 2L, "第二十二章 审计证据"),
                    Triple(53L, 2L, "第二十三章 审计档案"),

                    // 财务成本管理 (subjectId=3, id 54-75)
                    Triple(54L, 3L, "第一章 财务管理基本原理"),
                    Triple(55L, 3L, "第二章 财务报表分析与财务预测"),
                    Triple(56L, 3L, "第三章 增长率与资金需求"),
                    Triple(57L, 3L, "第四章 价值评估基础"),
                    Triple(58L, 3L, "第五章 投资项目资本预算"),
                    Triple(59L, 3L, "第六章 债券和股票估值"),
                    Triple(60L, 3L, "第七章 期权价值评估"),
                    Triple(61L, 3L, "第八章 企业价值评估"),
                    Triple(62L, 3L, "第九章 资本结构决策"),
                    Triple(63L, 3L, "第十章 股利分配决策"),
                    Triple(64L, 3L, "第十一章 长期筹资决策"),
                    Triple(65L, 3L, "第十二章 营运资本管理"),
                    Triple(66L, 3L, "第十三章 产品成本计算"),
                    Triple(67L, 3L, "第十四章 标准成本法"),
                    Triple(68L, 3L, "第十五章 作业成本法"),
                    Triple(69L, 3L, "第十六章 全面预算"),
                    Triple(70L, 3L, "第十七章 本量利分析"),
                    Triple(71L, 3L, "第十八章 短期经营决策"),
                    Triple(72L, 3L, "第十九章 业绩评价"),
                    Triple(73L, 3L, "第二十章 责任会计"),
                    Triple(74L, 3L, "第二十一章 成本性态分析"),
                    Triple(75L, 3L, "第二十二章 资金时间价值"),

                    // 税法 (subjectId=4, id 76-89)
                    Triple(76L, 4L, "第一章 税法总论"),
                    Triple(77L, 4L, "第二章 增值税法"),
                    Triple(78L, 4L, "第三章 消费税法"),
                    Triple(79L, 4L, "第四章 企业所得税法"),
                    Triple(80L, 4L, "第五章 个人所得税法"),
                    Triple(81L, 4L, "第六章 关税法和船舶吨税法"),
                    Triple(82L, 4L, "第七章 资源税法和环境保护税法"),
                    Triple(83L, 4L, "第八章 房产税法、契税法和土地增值税法"),
                    Triple(84L, 4L, "第九章 车辆购置税法、车船税法和印花税法"),
                    Triple(85L, 4L, "第十章 国际税收"),
                    Triple(86L, 4L, "第十一章 税收征收管理法"),
                    Triple(87L, 4L, "第十二章 税务行政法制"),
                    Triple(88L, 4L, "第十三章 税务代理"),
                    Triple(89L, 4L, "第十四章 税务筹划"),

                    // 经济法 (subjectId=5, id 90-101)
                    Triple(90L, 5L, "第一章 法律基本原理"),
                    Triple(91L, 5L, "第二章 基本民事法律制度"),
                    Triple(92L, 5L, "第三章 物权法律制度"),
                    Triple(93L, 5L, "第四章 合同法律制度"),
                    Triple(94L, 5L, "第五章 合伙企业法律制度"),
                    Triple(95L, 5L, "第六章 公司法律制度"),
                    Triple(96L, 5L, "第七章 证券法律制度"),
                    Triple(97L, 5L, "第八章 企业破产法律制度"),
                    Triple(98L, 5L, "第九章 票据法律制度"),
                    Triple(99L, 5L, "第十章 企业国有资产法律制度"),
                    Triple(100L, 5L, "第十一章 反垄断法律制度"),
                    Triple(101L, 5L, "第十二章 涉外经济法律制度"),

                    // 公司战略与风险管理 (subjectId=6, id 102-112)
                    Triple(102L, 6L, "第一章 战略管理框架"),
                    Triple(103L, 6L, "第二章 战略分析工具"),
                    Triple(104L, 6L, "第三章 战略选择方法"),
                    Triple(105L, 6L, "第四章 战略实施过程"),
                    Triple(106L, 6L, "第五章 战略控制与评价"),
                    Triple(107L, 6L, "第六章 公司治理结构"),
                    Triple(108L, 6L, "第七章 风险管理框架"),
                    Triple(109L, 6L, "第八章 内部控制体系"),
                    Triple(110L, 6L, "第九章 风险应对策略"),
                    Triple(111L, 6L, "第十章 战略变革管理"),
                    Triple(112L, 6L, "第十一章 企业国际化战略")
                )

                // 按科目分组计算章节序号
                chapters.groupBy { it.second }.forEach { (_, subjectChapters) ->
                    subjectChapters.forEachIndexed { index, triple ->
                        db.execSQL(
                            "INSERT INTO chapters (id, subjectId, name, `order`) VALUES (?, ?, ?, ?)",
                            arrayOf(triple.first, triple.second, triple.third, index + 1)
                        )
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Update subject colors to rainbow scheme (migration from old AmberGold)
                val colors = mapOf(
                    1L to 0xFFE74C3CL,  // 会计 - 红
                    2L to 0xFFE67E22L,  // 审计 - 橙
                    3L to 0xFFF1C40FL,  // 财务成本管理 - 黄
                    4L to 0xFF2ECC71L,  // 税法 - 绿
                    5L to 0xFF3498DBL,  // 经济法 - 蓝
                    6L to 0xFF9B59B6L   // 公司战略与风险管理 - 紫
                )
                colors.forEach { (id, color) ->
                    db.execSQL("UPDATE subjects SET color = ? WHERE id = ?", arrayOf(color, id))
                }
            }
        }
    }
}