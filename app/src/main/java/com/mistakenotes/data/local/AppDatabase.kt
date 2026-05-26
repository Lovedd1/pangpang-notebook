package com.mistakenotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 2,
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
        val prepopulateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // CPA 考试六科
                val subjects = listOf(
                    Pair(1L, "会计"),
                    Pair(2L, "审计"),
                    Pair(3L, "财务成本管理"),
                    Pair(4L, "税法"),
                    Pair(5L, "经济法"),
                    Pair(6L, "公司战略与风险管理")
                )
                subjects.forEach { (id, name) ->
                    db.execSQL(
                        "INSERT INTO subjects (id, name, color) VALUES (?, ?, ?)",
                        arrayOf(id, name, 0xFFD4A574)
                    )
                }

                // CPA 考试章节 - Triple(章节ID, 科目ID, 章节名称)
                val chapters = listOf(
                    // 会计 (subjectId=1, id 1-28)
                    Triple(1L, 1L, "第一章 总论"),
                    Triple(2L, 1L, "第二章 存货"),
                    Triple(3L, 1L, "第三章 固定资产"),
                    Triple(4L, 1L, "第四章 无形资产"),
                    Triple(5L, 1L, "第五章 投资性房地产"),
                    Triple(6L, 1L, "第六章 长期股权投资"),
                    Triple(7L, 1L, "第七章 资产减值"),
                    Triple(8L, 1L, "第八章 金融工具"),
                    Triple(9L, 1L, "第九章 职工薪酬"),
                    Triple(10L, 1L, "第十章 借款费用"),
                    Triple(11L, 1L, "第十一章 股份支付"),
                    Triple(12L, 1L, "第十二章 或有事项"),
                    Triple(13L, 1L, "第十三章 租赁"),
                    Triple(14L, 1L, "第十四章 持有待售的非流动资产和处置组"),
                    Triple(15L, 1L, "第十五章 所有者权益"),
                    Triple(16L, 1L, "第十六章 收入、费用和利润"),
                    Triple(17L, 1L, "第十七章 政府补助"),
                    Triple(18L, 1L, "第十八章 所得税"),
                    Triple(19L, 1L, "第十九章 外币折算"),
                    Triple(20L, 1L, "第二十章 会计政策、会计估计变更和差错更正"),
                    Triple(21L, 1L, "第二十一章 资产负债表日后事项"),
                    Triple(22L, 1L, "第二十二章 企业合并"),
                    Triple(23L, 1L, "第二十三章 合并财务报表"),
                    Triple(24L, 1L, "第二十四章 每股收益"),
                    Triple(25L, 1L, "第二十五章 公允价值计量"),
                    Triple(26L, 1L, "第二十六章 政府会计"),
                    Triple(27L, 1L, "第二十七章 民间非营利组织会计"),
                    Triple(28L, 1L, "第二十八章 会计法规"),

                    // 审计 (subjectId=2, id 29-51)
                    Triple(29L, 2L, "第一章 审计概述"),
                    Triple(30L, 2L, "第二章 审计计划"),
                    Triple(31L, 2L, "第三章 审计证据"),
                    Triple(32L, 2L, "第四章 审计抽样方法"),
                    Triple(33L, 2L, "第五章 信息技术对审计的影响"),
                    Triple(34L, 2L, "第六章 风险评估"),
                    Triple(35L, 2L, "第七章 风险应对"),
                    Triple(36L, 2L, "第八章 销售与收款循环的审计"),
                    Triple(37L, 2L, "第九章 采购与付款循环的审计"),
                    Triple(38L, 2L, "第十章 生产与存货循环的审计"),
                    Triple(39L, 2L, "第十一章 货币资金审计"),
                    Triple(40L, 2L, "第十二章 审计沟通"),
                    Triple(41L, 2L, "第十三章 注册会计师利用他人的工作"),
                    Triple(42L, 2L, "第十四章 审计报告"),
                    Triple(43L, 2L, "第十五章 审计沟通"),
                    Triple(44L, 2L, "第十六章 注册会计师的质量管理"),
                    Triple(45L, 2L, "第十七章 职业道德"),
                    Triple(46L, 2L, "第十八章 持续经营"),
                    Triple(47L, 2L, "第十九章 舞弊"),
                    Triple(48L, 2L, "第二十章 审计沟通"),
                    Triple(49L, 2L, "第二十一章 审计监管"),
                    Triple(50L, 2L, "第二十二章 审计证据"),
                    Triple(51L, 2L, "第二十三章 审计档案"),

                    // 财务成本管理 (subjectId=3, id 52-73)
                    Triple(52L, 3L, "第一章 财务管理基本原理"),
                    Triple(53L, 3L, "第二章 财务报表分析与财务预测"),
                    Triple(54L, 3L, "第三章 增长率与资金需求"),
                    Triple(55L, 3L, "第四章 价值评估基础"),
                    Triple(56L, 3L, "第五章 投资项目资本预算"),
                    Triple(57L, 3L, "第六章 债券和股票估值"),
                    Triple(58L, 3L, "第七章 期权价值评估"),
                    Triple(59L, 3L, "第八章 企业价值评估"),
                    Triple(60L, 3L, "第九章 资本结构决策"),
                    Triple(61L, 3L, "第十章 股利分配决策"),
                    Triple(62L, 3L, "第十一章 长期筹资决策"),
                    Triple(63L, 3L, "第十二章 营运资本管理"),
                    Triple(64L, 3L, "第十三章 产品成本计算"),
                    Triple(65L, 3L, "第十四章 标准成本法"),
                    Triple(66L, 3L, "第十五章 作业成本法"),
                    Triple(67L, 3L, "第十六章 全面预算"),
                    Triple(68L, 3L, "第十七章 本量利分析"),
                    Triple(69L, 3L, "第十八章 短期经营决策"),
                    Triple(70L, 3L, "第十九章 业绩评价"),
                    Triple(71L, 3L, "第二十章 责任会计"),
                    Triple(72L, 3L, "第二十一章 成本性态分析"),
                    Triple(73L, 3L, "第二十二章 资金时间价值"),

                    // 税法 (subjectId=4, id 74-87)
                    Triple(74L, 4L, "第一章 税法总论"),
                    Triple(75L, 4L, "第二章 增值税法"),
                    Triple(76L, 4L, "第三章 消费税法"),
                    Triple(77L, 4L, "第四章 企业所得税法"),
                    Triple(78L, 4L, "第五章 个人所得税法"),
                    Triple(79L, 4L, "第六章 关税法和船舶吨税法"),
                    Triple(80L, 4L, "第七章 资源税法和环境保护税法"),
                    Triple(81L, 4L, "第八章 房产税法、契税法和土地增值税法"),
                    Triple(82L, 4L, "第九章 车辆购置税法、车船税法和印花税法"),
                    Triple(83L, 4L, "第十章 国际税收"),
                    Triple(84L, 4L, "第十一章 税收征收管理法"),
                    Triple(85L, 4L, "第十二章 税务行政法制"),
                    Triple(86L, 4L, "第十三章 税务代理"),
                    Triple(87L, 4L, "第十四章 税务筹划"),

                    // 经济法 (subjectId=5, id 88-101)
                    Triple(88L, 5L, "第一章 法律基本原理"),
                    Triple(89L, 5L, "第二章 基本民事法律制度"),
                    Triple(90L, 5L, "第三章 物权法律制度"),
                    Triple(91L, 5L, "第四章 合同法律制度"),
                    Triple(92L, 5L, "第五章 婚姻家庭和继承法律制度"),
                    Triple(93L, 5L, "第六章 公司法律制度"),
                    Triple(94L, 5L, "第七章 证券法律制度"),
                    Triple(95L, 5L, "第八章 企业破产法律制度"),
                    Triple(96L, 5L, "第九章 票据与支付结算法律制度"),
                    Triple(97L, 5L, "第十章 保险法律制度"),
                    Triple(98L, 5L, "第十一章 知识产权法律制度"),
                    Triple(99L, 5L, "第十二章 市场竞争法律制度"),
                    Triple(100L, 5L, "第十三章 劳动法律制度"),
                    Triple(101L, 5L, "第十四章 电子商务法律制度"),

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
        }
    }
}