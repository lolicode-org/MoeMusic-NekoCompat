package org.lolicode.moemusic.nekocompat

import org.lolicode.moemusic.api.MoeMusicUser
import org.lolicode.moemusic.api.PermissionDeniedException
import org.lolicode.moemusic.api.model.TrackInfo
import org.lolicode.moemusic.api.permission.MoeMusicPermission
import org.lolicode.moemusic.api.service.IPermissionService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueueVisibilityTest {
    private val sampleQueue = listOf(
        TrackInfo(
            id = "track-1",
            title = "Track One",
            artists = emptyList(),
            durationMs = 123_000) {
                sourceId = "source-a"
                submittedByUserName = "tester"
            }
    )

    @Test
    fun `authorized viewers receive the queue`() {
        val viewer = FakePlayer()
        val permissions = FakePermissionService(allowed = true)

        val result = visibleQueueForPlayer(sampleQueue, permissions, viewer)

        assertEquals(sampleQueue, result)
        assertEquals(MoeMusicPermission.QUEUE_VIEW, permissions.lastPermission)
        assertEquals(viewer, permissions.lastPlayer)
    }

    @Test
    fun `missing or unauthorized viewers receive an empty queue`() {
        val deniedViewer = FakePlayer()
        val deniedPermissions = FakePermissionService(allowed = false)

        assertTrue(visibleQueueForPlayer(sampleQueue, deniedPermissions, deniedViewer).isEmpty())
        assertTrue(visibleQueueForPlayer(sampleQueue, deniedPermissions, viewer = null).isEmpty())
    }

    private class FakePermissionService(
        private val allowed: Boolean,
    ) : IPermissionService {
        var lastPermission: MoeMusicPermission? = null
        var lastPlayer: MoeMusicUser? = null

        override fun has(permission: MoeMusicPermission, user: MoeMusicUser): Boolean {
            lastPermission = permission
            lastPlayer = user
            return allowed
        }

        override fun require(permission: MoeMusicPermission, user: MoeMusicUser?) {
            if (user == null || allowed) return
            throw PermissionDeniedException()
        }
    }

    private class FakePlayer : MoeMusicUser() {
        override val displayName: String = "tester"
        override val id: UUID = UUID.randomUUID()
        override val locale: String = "en_us"

        override fun hasPermission(permission: String, defaultLevel: Int): Boolean =
            error("unused in QueueVisibilityTest")
    }
}
