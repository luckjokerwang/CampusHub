package com.campushub.app.core.service.example

import com.campushub.app.core.bridge.StorageBridge
import com.campushub.app.core.model.PluginCategory
import com.campushub.app.core.plugin.AmountTone
import com.campushub.app.core.plugin.BentoCardSize
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.CardState
import com.campushub.app.core.plugin.ConfigField
import com.campushub.app.core.plugin.ConfigFieldType
import com.campushub.app.core.plugin.ConfigGroup
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.plugin.DetailNavIcon
import com.campushub.app.core.plugin.DetailPage
import com.campushub.app.core.plugin.DetailSection
import com.campushub.app.core.plugin.DetailSource
import com.campushub.app.core.plugin.GlanceDetail
import com.campushub.app.core.plugin.GlanceModel
import com.campushub.app.core.plugin.GlanceSource
import com.campushub.app.core.plugin.GlanceTarget
import com.campushub.app.core.plugin.KpiItem
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.core.plugin.PluginManifest
import com.campushub.app.core.plugin.RowItem
import com.campushub.app.core.service.CampusService
import com.campushub.app.core.service.ServiceResult

/**
 * 示例服务：**完全离线**，用来演示一个校园插件该长什么样。
 *
 * 它实现了四件宿主能力：
 * - [ConfigurablePlugin]：登录表单由宿主渲染，凭据写进 [storage]（即 `SecureStorage.forPlugin(id)`）
 * - [GlanceSource]：首页卡片数据
 * - [DetailSource]：详情页数据
 *
 * 刻意不联网、不编造真实校园数据：所有数字都来自本地演示记录，
 * 这样公开壳在断网环境也能完整走通「未登录 → 登录 → 卡片 → 详情」。
 *
 * 接入真实服务时，把 [ExampleApi] 指向你自己的网关，并在
 * `ExampleProtocol` 里定义字段映射；本类的结构可以原样保留。
 */
class ExampleService(
    private val storage: StorageBridge?
) : CampusService, ConfigurablePlugin, GlanceSource, DetailSource {

    companion object {
        const val ID = "com.example.campus.service"
        const val GROUP_ACCOUNT = "account"

        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_CHECKINS = "checkins"
    }

    override val id: String = ID
    override val displayName: String = "示例服务"

    override val category: PluginCategory = PluginCategory.UTILITY

    override val manifest: PluginManifest = PluginManifest(
        id = ID,
        name = displayName,
        description = "离线示例：演示配置、卡片与详情的最小接线",
        iconGlyph = "例",
        accent = PluginAccent.NEUTRAL,
        cards = listOf(
            CardSpec(
                id = CARD_OVERVIEW,
                title = "示例概览",
                iconGlyph = "例",
                accent = PluginAccent.NEUTRAL,
                defaultSize = BentoCardSize.EXPANDED_2X1
            )
        )
    )

    // ------------------------------------------------------------ 凭据

    private fun studentId(): String = storage?.getItem(KEY_STUDENT_ID).orEmpty().trim()

    private fun nickname(): String = storage?.getItem(KEY_NICKNAME).orEmpty().trim()

    /** 演示用的本地计数，代表"服务端数据"的替身。 */
    private fun checkins(): Int = storage?.getItem(KEY_CHECKINS)?.toIntOrNull() ?: 0

    fun isLoggedIn(): Boolean = studentId().isNotBlank()

    // ------------------------------------------------------------ ConfigurablePlugin

    override val configGroups: List<ConfigGroup> = listOf(
        ConfigGroup(
            id = GROUP_ACCOUNT,
            label = "示例账号",
            fields = listOf(
                ConfigField(
                    key = "student_id",
                    label = "学号",
                    type = ConfigFieldType.TEXT,
                    placeholder = "任意非空字符串即可"
                ),
                ConfigField(
                    key = "nickname",
                    label = "昵称",
                    type = ConfigFieldType.TEXT,
                    required = false,
                    placeholder = "用于卡片标题"
                )
            ),
            hint = "这是离线示例，不会发出任何网络请求，也不会校验真实身份。"
        )
    )

    override fun isConfigured(groupId: String): Boolean =
        groupId == GROUP_ACCOUNT && isLoggedIn()

    override fun prefill(groupId: String): Map<String, String> = mapOf(
        "student_id" to studentId(),
        "nickname" to nickname()
    )

    override suspend fun submit(groupId: String, values: Map<String, String>): ServiceResult<String> {
        if (groupId != GROUP_ACCOUNT) {
            return ServiceResult.BusinessError(-1, "未知的配置分组：$groupId")
        }
        val studentId = values["student_id"].orEmpty().trim()
        if (studentId.isBlank()) {
            return ServiceResult.BusinessError(-1, "学号不能为空")
        }
        val st = storage ?: return ServiceResult.BusinessError(-1, "本地存储不可用")
        st.setItem(KEY_STUDENT_ID, studentId)
        st.setItem(KEY_NICKNAME, values["nickname"].orEmpty().trim())
        if (st.getItem(KEY_CHECKINS).isNullOrBlank()) st.setItem(KEY_CHECKINS, "0")
        return ServiceResult.Ok("已保存示例账号")
    }

    override suspend fun logout() {
        storage?.removeItem(KEY_STUDENT_ID)
        storage?.removeItem(KEY_NICKNAME)
    }

    // ------------------------------------------------------------ GlanceSource

    override suspend fun glance(cardId: String): GlanceModel {
        if (cardId != CARD_OVERVIEW) {
            return GlanceModel(
                cardId = cardId,
                state = CardState.EMPTY,
                title = "示例概览",
                sub = "未知卡片"
            )
        }
        if (!isLoggedIn()) {
            return GlanceModel(
                cardId = cardId,
                state = CardState.UNBOUND,
                title = "示例服务",
                sub = "未登录",
                tapTarget = GlanceTarget.Login
            )
        }
        val done = checkins()
        return GlanceModel(
            cardId = cardId,
            state = if (done > 0) CardState.DONE else CardState.IDLE,
            title = nickname().ifBlank { "同学" },
            serviceLabel = displayName,
            chip = if (done > 0) "已打卡 $done 次" else "待打卡",
            readout = "$done",
            readoutUnit = "次",
            sub = "离线示例数据，不来自任何真实校园接口",
            details = listOf(
                GlanceDetail("学号", studentId()),
                GlanceDetail("数据来源", "本地存储")
            ),
            tapTarget = GlanceTarget.Page(PAGE_OVERVIEW)
        )
    }

    // ------------------------------------------------------------ DetailSource

    override suspend fun detailPages(): ServiceResult<List<DetailPage>> {
        if (!isLoggedIn()) {
            return ServiceResult.Unauthorized("请先登录示例账号")
        }
        val done = checkins()
        val page = DetailPage(
            id = PAGE_OVERVIEW,
            title = "示例概览",
            navLabel = "概览",
            navIcon = DetailNavIcon.OVERVIEW,
            headerAction = null,
            sections = listOf(
                DetailSection.Kpi(
                    items = listOf(
                        KpiItem(label = "累计打卡", value = "$done", unit = "次"),
                        KpiItem(label = "账号", value = studentId())
                    )
                ),
                DetailSection.Records(
                    title = "最近记录",
                    emptyText = "还没有记录，点下方按钮演示一次。",
                    rows = if (done > 0) {
                        (1..done.coerceAtMost(5)).map { i ->
                            RowItem(
                                id = "checkin_$i",
                                title = "示例打卡 #$i",
                                sub = "由本地演示数据生成",
                                amount = "+1",
                                tone = AmountTone.IN
                            )
                        }
                    } else {
                        emptyList()
                    }
                )
            ),
            actions = listOf(
                com.campushub.app.core.plugin.PageAction(
                    label = "演示打卡 +1",
                    commandId = COMMAND_CHECK_IN
                )
            ),
            note = "本页全部数据来自本地存储；公开壳不包含任何真实校园服务实现。"
        )
        return ServiceResult.Ok(listOf(page))
    }

    override suspend fun onDetailCommand(
        commandId: String,
        rowId: String?,
        input: String?
    ): ServiceResult<String> {
        if (commandId != COMMAND_CHECK_IN) {
            return ServiceResult.BusinessError(-1, "不支持的命令：$commandId")
        }
        val st = storage ?: return ServiceResult.BusinessError(-1, "本地存储不可用")
        st.setItem(KEY_CHECKINS, (checkins() + 1).toString())
        return ServiceResult.Ok("已记录一次示例打卡")
    }
}

private const val CARD_OVERVIEW = "example_overview"
private const val PAGE_OVERVIEW = "overview"
private const val COMMAND_CHECK_IN = "example.checkin"
