package com.superman.drilldemo.play // 替换为你的实际包名

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

@UnstableApi
class GlideBitmapLoader(private val context: Context) : BitmapLoader { // <--- 实现 common.util.BitmapLoader

    /**
     * 判断 Glide 是否可能支持给定的 MIME 类型。
     * Glide 内部会自动检测格式，所以这里可以相对宽松。
     */
    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/") // 例如: "image/jpeg", "image/png", "image/webp", "image/gif"
    }

    /**
     * 使用 Glide 从字节数组解码 Bitmap。
     */
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        try {
            Glide.with(context)
                .asBitmap()
                .load(data)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        future.set(resource.copy(Bitmap.Config.ARGB_8888, true))
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        // 可以更详细地记录错误
                        future.setException(RuntimeException("Glide failed to decode bitmap from byte array"))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {

                    }
                })
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    /**
     * 使用 Glide 从 Uri 加载 Bitmap。
     * 这与你之前的实现相同。
     */
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        try {
            Glide.with(context)
                .asBitmap()
                .load(uri)
                // .placeholder(R.drawable.placeholder_image) // 可选
                // .error(R.drawable.error_image)           // 可选
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        // 确保返回的 Bitmap 是可变的并且是 ARGB_8888
                        future.set(resource.copy(Bitmap.Config.RGB_565, true))
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        // Log.e("GlideBitmapLoader", "Failed to load bitmap from $uri")
                        // 对于 loadBitmap，MediaSession 可能期望在失败时得到 null 或者一个包含 null 的 Future
                        // 如果 MediaSession 不能处理 Exception，这里返回一个包含 null 的 Future 更安全
                        // 但由于接口签名是 ListenableFuture<Bitmap> 而不是 ListenableFuture<Bitmap?>
                        // 最好是 setException，或者如果可以，让调用者处理 null。
                        // 然而，set(null) 可能会导致 NullPointerException 如果 Future 的泛型不是 @Nullable。
                        // 为了与接口 ListenableFuture<Bitmap> 保持一致，这里抛出异常。
                        // 如果 MediaSession 期望失败时返回 null 并且不崩溃，那接口签名应该是 ListenableFuture<Bitmap?>
                        future.setException(RuntimeException("Glide failed to load bitmap from URI: $uri"))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {

                    }
                })
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    // `loadBitmapFromMetadata` 是 `androidx.media3.common.util.BitmapLoader` 中的默认方法，
    // 所以我们不需要在这里显式地重新实现它，除非我们想改变它的默认行为。
    // 它会根据 `artworkData` 或 `artworkUri` 调用我们上面实现的 `decodeBitmap` 或 `loadBitmap`。
    // override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
    //     return BitmapLoader.super.loadBitmapFromMetadata(metadata) // 调用默认实现
    // }
}

