package com.superman.exoplayerdemo

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 *
 * @author 张学阳
 * @date : 2025/8/10
 * @description:
 */
class MyMediaSessionCallback(private val player: Player) : MediaSession.Callback {
    // 1. 处理连接请求
//    @OptIn(UnstableApi::class)
//    override fun onConnect(
//        session: MediaSession,
//        controller: MediaSession.ControllerInfo
//    ): MediaSession.ConnectionResult {
//        // 1. 获取所有可用的会话命令 (由 MediaSession.Builder 定义或默认)
//        val availableSessionCommands = session.availableSessionCommands
//
//        // 2. 获取所有可用的播放器命令 (由 Player 实现定义，例如 ExoPlayer)
//        // 你可以通过 player.getAvailableCommands() 来获取实际可用的播放器命令
//        // 或者，如果你想授予所有 Player 定义的通用命令，可以这样做：
//        val availablePlayerCommands = Player.Commands.EMPTY.buildUpon()
//            .addAllCommands() // 添加所有 Player 接口定义的命令
//            .build()
//        // 更准确的做法是使用 player.availableCommands，如果 player 实例已经完全初始化
//        // val actualPlayerCommands = player.availableCommands
//
//
//        Log.d(
//            "MyMediaSessionCallback",
//            "Controller trying to connect: ${controller.packageName}, UID: ${controller.uid}"
//        )
//
//        // 你可以在这里根据 controller.packageName 或其他信息来做更细致的权限控制
//        // 例如，只允许特定的应用连接，或者根据应用类型授予不同的命令集
//        if (shouldAllowConnection(controller)) {
//            Log.d(
//                "MyMediaSessionCallback",
//                "Connection accepted for ${controller.packageName}. " +
//                        "SessionCommands: $availableSessionCommands, PlayerCommands: $availablePlayerCommands"
//            )
//            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
//                .setAvailableSessionCommands(availableSessionCommands)
//                .setAvailablePlayerCommands(availablePlayerCommands) // 使用构建的或实际的 PlayerCommands
//                .build()
//        } else {
//            Log.w("MyMediaSessionCallback", "Connection rejected for ${controller.packageName}.")
//            // 如果拒绝连接
//            // return MediaSession.ConnectionResult.REJECTED_RESULT // (这是一个简化的表示，实际API可能不同)
//            // 更标准的拒绝方式是不返回 AcceptedResultBuilder 或者根据文档抛出异常（但不推荐在 onConnect 抛异常）
//            // 通常是返回一个没有命令的 ConnectionResult，或者根据具体场景返回一个特定的错误码 SessionResult（但不适用于 onConnect）
//            // 最简单的拒绝是返回一个空的 ConnectionResult 或者不调用 AcceptedResultBuilder
//            // 但 Media3 的设计倾向于如果不接受，则不完成连接或者在其他地方处理拒绝逻辑。
//            // 一个明确的拒绝方式是：
//            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
//                .setAvailableSessionCommands(SessionCommands.EMPTY)
//                .setAvailablePlayerCommands(Player.Commands.EMPTY)
//                .build() // 授予空命令集，实际上是拒绝了有效交互
//
//        }
//    }

    private fun shouldAllowConnection(controller: MediaSession.ControllerInfo): Boolean {
        // 在这里实现你的连接逻辑
        // 例如，你可以检查 controller.packageName
        // if (controller.packageName == "com.my.trusted.app") return true
        return true // 示例：允许所有连接
    }

    // 2. 处理添加媒体项的请求 (当控制器调用 mediaController.addMediaItem(s))
    @OptIn(UnstableApi::class)
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        Log.d(
            "MyMediaSessionCallback",
            "onAddMediaItems called by ${controller.packageName} with ${mediaItems.size} items."
        )
        // 假设我们在这里可能需要根据 mediaId 解析实际的 URI 或元数据
        // 这个示例中我们直接使用传入的 mediaItems，但你可以修改它们
        val updatedMediaItems = mediaItems.map { mediaItem ->
            if (mediaItem.requestMetadata.mediaUri == null && mediaItem.mediaId.isNotEmpty()) {
                // 例如，如果 mediaUri 为空但有 mediaId，你可能需要根据 mediaId 查找 URI
                // val actualUri = resolveUriFromMediaId(mediaItem.mediaId)
                // mediaItem.buildUpon().setUri(actualUri).build()
                mediaItem // 在此示例中保持原样
            } else {
                mediaItem
            }
        }.toMutableList()

        // 将媒体项添加到播放器
        // ExoPlayer 的 Player 接口有 addMediaItems 方法
        player.addMediaItems(updatedMediaItems)
        Log.d("MyMediaSessionCallback", "Media items added to player.")

        // 返回处理后的（可能已更新的）媒体项列表
        return Futures.immediateFuture(updatedMediaItems)
    }
// Media3 的 MediaSession 通常会自动将 Player 接口中定义的命令
    // (如 play, pause, seekToNext, etc.) 代理给关联的 Player 实例。
    // 因此，对于这些标准播放命令，你通常不需要在 Callback 中显式重写并调用 player.play() 等。
    // Player 接口已经定义了 onPlay, onPause 等回调，MediaSession 会监听 Player 的这些变化。

    // 但是，如果你需要做一些额外的逻辑，或者处理 Player 接口之外的命令，
    // 你仍然可以重写相应的 onXxx 方法。

    @OptIn(UnstableApi::class)
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int // 例如 Player.COMMAND_PLAY_PAUSE
    ): Int {
        // 这个方法允许你基于控制器决定是否允许某个播放器命令。
        // 返回 SessionResult.RESULT_SUCCESS 表示允许，
        // SessionResult.RESULT_ERROR_NOT_SUPPORTED 表示不允许。
        // 默认实现通常会检查 Player.isCommandAvailable(playerCommand)
        Log.d(
            "MyMediaSessionCallback",
            "onPlayerCommandRequest for command: $playerCommand by ${controller.packageName}"
        )
        return super.onPlayerCommandRequest(session, controller, playerCommand) // 调用父类实现
    }

    // 处理自定义命令示例 (如果需要)
    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Log.d(
            "MyMediaSessionCallback",
            "onCustomCommand: ${customCommand.customAction} from ${controller.packageName}"
        )
        if (customCommand.customAction == "MY_CUSTOM_ACTION_EXAMPLE") {
            // 执行你的自定义操作
            // val customData = args.getString("custom_key")
            // Log.d("MyMediaSessionCallback", "Handling MY_CUSTOM_ACTION_EXAMPLE with data: $customData")
            // player.setShuffleModeEnabled(!player.shuffleModeEnabled) // 例如，切换随机播放
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controller, customCommand, args) // 调用父类处理未知命令
    }
// 你可以重写其他方法，例如：
    // onPlaybackResumption() - 当由于音频焦点丢失或其他原因暂停后，会话请求恢复播放
    // onSetRating(session: MediaSession, controller: ControllerInfo, mediaId: String, rating: Rating)
    // onSetPlaybackSpeed(session: MediaSession, controller: ControllerInfo, speed: Float)
    // 等等...
}