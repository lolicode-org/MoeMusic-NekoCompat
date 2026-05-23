package org.lolicode.moemusic.nekocompat

import org.lolicode.moemusic.api.MoeMusicUser
import org.lolicode.moemusic.api.model.TrackInfo
import org.lolicode.moemusic.api.permission.MoeMusicPermission
import org.lolicode.moemusic.api.service.IPermissionService

internal fun visibleQueueForPlayer(
    queue: List<TrackInfo>,
    permissionService: IPermissionService,
    viewer: MoeMusicUser?,
): List<TrackInfo> {
    if (viewer == null) return emptyList()
    if (!permissionService.has(MoeMusicPermission.QUEUE_VIEW, viewer)) return emptyList()
    return queue
}
