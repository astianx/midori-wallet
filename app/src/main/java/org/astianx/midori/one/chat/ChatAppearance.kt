package org.astianx.midori.one.chat

import androidx.compose.runtime.staticCompositionLocalOf
import org.astianx.midori.one.R
import org.json.JSONObject

enum class ChatWallpaperStyle(val label: Int) { BOTANICAL(R.string.style_botanical), PLAIN(R.string.style_plain), DOTS(R.string.style_dots), GRID(R.string.style_grid), PHOTO(R.string.style_photo) }
enum class BubbleStyle(val label: Int, val radius: Int) { SOFT(R.string.style_soft, 16), ROUND(R.string.style_round, 28), SQUARE(R.string.style_square, 8) }
enum class AvatarStyle(val label: Int, val radius: Int) { ROUNDED(R.string.style_soft, 19), CIRCLE(R.string.style_round, 100), SQUARE(R.string.style_square, 8) }

data class ChatAppearance(
    val wallpaper: ChatWallpaperStyle = ChatWallpaperStyle.PLAIN,
    val wallpaperOpacity: Int = 20,
    val photo: String? = null,
    val bubbles: BubbleStyle = BubbleStyle.SOFT,
    val fontSize: Int = 16,
    val avatarStyle: AvatarStyle = AvatarStyle.CIRCLE,
    val avatarSize: Int = 52,
    val compact: Boolean = false,
    val showPreviews: Boolean = true,
    val showPresence: Boolean = true,
    val groupBubbles: Boolean = true,
    val showMessageAvatars: Boolean = false,
    val emojiSize: Int = 24,
    val sendOnEnter: Boolean = false
) {
    fun toJson(includeLocalPhoto: Boolean = true) = JSONObject()
        .put("wallpaper", (if (!includeLocalPhoto && wallpaper == ChatWallpaperStyle.PHOTO) ChatWallpaperStyle.PLAIN else wallpaper).name)
        .put("wallpaperOpacity", wallpaperOpacity).put("bubbles", bubbles.name).put("fontSize", fontSize)
        .put("avatarStyle", avatarStyle.name).put("avatarSize", avatarSize).put("compact", compact)
        .put("showPreviews", showPreviews).put("showPresence", showPresence).put("groupBubbles", groupBubbles)
        .put("showMessageAvatars", showMessageAvatars).put("emojiSize", emojiSize).put("sendOnEnter", sendOnEnter)
        .apply { if (includeLocalPhoto && photo != null) put("photo", photo) }
    companion object {
        fun fromJson(j: JSONObject, allowLocalPhoto: Boolean = true): ChatAppearance {
            val photo = j.optString("photo").takeIf { it.isNotBlank() }
            require(photo == null || (allowLocalPhoto && photo.matches(Regex("[a-f0-9-]{36}\\.jpg"))))
            val wallpaper = ChatWallpaperStyle.valueOf(j.getString("wallpaper"))
            require(allowLocalPhoto || wallpaper != ChatWallpaperStyle.PHOTO)
            return ChatAppearance(wallpaper, j.getInt("wallpaperOpacity").also { require(it in 5..60) }, photo,
                BubbleStyle.valueOf(j.getString("bubbles")), j.getInt("fontSize").also { require(it in 13..24) },
                AvatarStyle.valueOf(j.getString("avatarStyle")), j.getInt("avatarSize").also { require(it in 40..64) },
                j.getBoolean("compact"), j.getBoolean("showPreviews"), j.getBoolean("showPresence"),
                j.getBoolean("groupBubbles"), j.getBoolean("showMessageAvatars"),
                j.getInt("emojiSize").also { require(it in 20..36) }, j.getBoolean("sendOnEnter"))
        }
    }
}
internal val LocalChatAppearance = staticCompositionLocalOf { ChatAppearance() }
internal fun ChatUiState.appearanceFor(peer: String?) = conversationOptions[peer]?.appearance ?: appearance
