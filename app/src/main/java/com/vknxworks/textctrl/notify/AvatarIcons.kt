package com.vknxworks.textctrl.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.ui.components.avatarColor

/**
 * Pictures for conversation shortcuts and bubbles, as adaptive icons.
 *
 * Android (and every brand's skin on top of it) masks a shortcut or bubble icon to its own shape. A plain
 * square bitmap is first shrunk to fit inside that shape, so a contact photo ended up as a smaller disc
 * inside the bubble, which made the bubble look smaller and further from the screen edge than it is,
 * more so on skins that add padding of their own (ColorOS, OxygenOS, realme UI). An adaptive icon is what
 * they expect: the picture fills the visible area exactly, whatever the shape.
 *
 * Layout of an adaptive icon: a 108-unit canvas of which only the middle 72 units are ever visible.
 */
object AvatarIcons {
    private const val SIZE = 288
    private const val INSET = SIZE * 18 / 108
    private val inner = Rect(INSET, INSET, SIZE - INSET, SIZE - INSET)
    private const val BACKDROP = 0xFF121418.toInt()

    /** The picture of a chat: the person's, or one for the group. */
    fun forChat(context: Context, contacts: Contacts, addresses: List<String>): IconCompat {
        if (addresses.size == 1) return forPerson(context, contacts, addresses[0])
        val key = addresses.sorted().joinToString()
        val letters = addresses.asSequence()
            .mapNotNull { a -> contacts.lookup(a).name?.firstOrNull { it.isLetter() }?.uppercaseChar() }
            .take(2)
            .joinToString("")
        return if (letters.length == 2) letterIcon(context, key, letters) else silhouetteIcon(key, group = true)
    }

    /** One person: their contact photo, their initial, or a person outline for a number. */
    fun forPerson(context: Context, contacts: Contacts, address: String): IconCompat {
        val contact = contacts.lookup(address)
        contact.photo?.let { uri -> photoIcon(context, uri)?.let { return it } }
        val letter = contact.name?.firstOrNull { it.isLetter() }?.uppercaseChar()
        return if (letter != null) letterIcon(context, address, letter.toString()) else silhouetteIcon(address, group = false)
    }

    private fun photoIcon(context: Context, uri: Uri): IconCompat? {
        val photo = runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        return runCatching {
            val side = minOf(photo.width, photo.height)
            val crop = Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // The hidden outer ring takes the photo's own average colour, so no shape ever shows a seam.
            val average = Bitmap.createScaledBitmap(photo, 1, 1, true).getPixel(0, 0)
            canvas.drawColor(average or 0xFF000000.toInt())
            canvas.drawBitmap(photo, crop, inner, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            IconCompat.createWithAdaptiveBitmap(out)
        }.getOrNull()
    }

    private fun backdrop(key: String): Pair<Int, Int> {
        val color = avatarColor(key).toArgb()
        // Like the avatars in the app: a deep tint of the person's colour, with the colour itself on top.
        return ColorUtils.blendARGB(BACKDROP, color, 0.24f) to color
    }

    private fun letterIcon(context: Context, key: String, text: String): IconCompat {
        val (bg, ink) = backdrop(key)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(bg)
        val base = runCatching { ResourcesCompat.getFont(context, R.font.manrope) }.getOrNull() ?: Typeface.DEFAULT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create(base, 700, false)
            textAlign = Paint.Align.CENTER
            textSize = inner.height() * if (text.length > 1) 0.36f else 0.46f
        }
        val y = inner.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, inner.exactCenterX(), y, paint)
        return IconCompat.createWithAdaptiveBitmap(out)
    }

    /** A head and shoulders (two for a group), for numbers that aren't in your contacts. */
    private fun silhouetteIcon(key: String, group: Boolean): IconCompat {
        val (bg, ink) = backdrop(key)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        val unit = inner.width() / 100f
        fun person(cx: Float, scale: Float) {
            val headR = 14f * unit * scale
            val headY = inner.top + 40f * unit
            canvas.drawCircle(cx, headY, headR, paint)
            val w = 30f * unit * scale
            val top = headY + headR + 4f * unit * scale
            val path = Path().apply {
                addRoundRect(RectF(cx - w, top, cx + w, top + 26f * unit * scale), floatArrayOf(w, w, w, w, 4f, 4f, 4f, 4f), Path.Direction.CW)
            }
            canvas.drawPath(path, paint)
        }
        if (group) {
            paint.alpha = 150
            person(inner.left + 62f * unit, 0.8f)
            paint.alpha = 255
            person(inner.left + 40f * unit, 0.9f)
        } else {
            person(inner.exactCenterX(), 1f)
        }
        return IconCompat.createWithAdaptiveBitmap(out)
    }
}
