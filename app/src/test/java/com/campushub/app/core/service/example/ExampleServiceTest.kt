package com.campushub.app.core.service.example

import com.campushub.app.core.bridge.StorageBridge
import com.campushub.app.core.plugin.CardState
import com.campushub.app.core.plugin.GlanceTarget
import com.campushub.app.core.service.ServiceResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 示例服务：覆盖未登录 / 登录 / 命令三条主路径。 */
class ExampleServiceTest {

    private class FakeStorage : StorageBridge {
        private val values = linkedMapOf<String, String>()
        override fun getItem(key: String): String? = values[key]
        override fun setItem(key: String, value: String) { values[key] = value }
        override fun removeItem(key: String) { values.remove(key) }
        override fun clear() = values.clear()
        override fun getAll(): Map<String, String> = values.toMap()
    }

    private fun service(storage: StorageBridge? = FakeStorage()) = ExampleService(storage)

    @Test
    fun `未登录时卡片显示 UNBOUND 且指向登录`() = runTest {
        val glance = service().glance("example_overview")

        assertEquals(CardState.UNBOUND, glance.state)
        assertEquals(GlanceTarget.Login, glance.tapTarget)
    }

    @Test
    fun `未登录时详情返回 Unauthorized`() = runTest {
        val result = service().detailPages()

        assertTrue(result is ServiceResult.Unauthorized)
    }

    @Test
    fun `提交空学号返回业务错误且不写入存储`() = runTest {
        val storage = FakeStorage()
        val result = service(storage).submit(ExampleService.GROUP_ACCOUNT, mapOf("student_id" to "  "))

        assertEquals(ServiceResult.BusinessError(-1, "学号不能为空"), result)
        assertTrue(storage.getAll().isEmpty())
    }

    @Test
    fun `提交未知分组返回业务错误`() = runTest {
        val result = service().submit("nope", mapOf("student_id" to "2024001"))

        assertTrue(result is ServiceResult.BusinessError)
    }

    @Test
    fun `登录成功后卡片变为 IDLE 并可进详情`() = runTest {
        val svc = service()
        svc.submit(ExampleService.GROUP_ACCOUNT, mapOf("student_id" to "2024001", "nickname" to "小明"))

        val glance = svc.glance("example_overview")
        val pages = svc.detailPages()

        assertEquals(CardState.IDLE, glance.state)
        assertEquals("小明", glance.title)
        assertEquals(GlanceTarget.Page("overview"), glance.tapTarget)
        assertTrue(pages is ServiceResult.Ok)
        assertEquals("overview", (pages as ServiceResult.Ok).value.single().id)
    }

    @Test
    fun `打卡命令累加计数并让卡片转为 DONE`() = runTest {
        val svc = service()
        svc.submit(ExampleService.GROUP_ACCOUNT, mapOf("student_id" to "2024001"))

        val command = svc.onDetailCommand("example.checkin", null, null)
        val glance = svc.glance("example_overview")

        assertEquals(ServiceResult.Ok("已记录一次示例打卡"), command)
        assertEquals(CardState.DONE, glance.state)
        assertEquals("1", glance.readout)
    }

    @Test
    fun `未知命令返回业务错误`() = runTest {
        val result = service().onDetailCommand("example.unknown", null, null)

        assertTrue(result is ServiceResult.BusinessError)
    }

    @Test
    fun `退出登录后卡片回到 UNBOUND`() = runTest {
        val svc = service()
        svc.submit(ExampleService.GROUP_ACCOUNT, mapOf("student_id" to "2024001"))

        svc.logout()

        assertFalse(svc.isLoggedIn())
        assertEquals(CardState.UNBOUND, svc.glance("example_overview").state)
    }

    @Test
    fun `无存储时提交与命令都明确失败而不是静默通过`() = runTest {
        val svc = service(storage = null)

        assertTrue(svc.submit(ExampleService.GROUP_ACCOUNT, mapOf("student_id" to "1")) is ServiceResult.BusinessError)
        assertTrue(svc.onDetailCommand("example.checkin", null, null) is ServiceResult.BusinessError)
    }

    @Test
    fun `未知卡片返回 EMPTY`() = runTest {
        assertEquals(CardState.EMPTY, service().glance("nope").state)
    }
}
