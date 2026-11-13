package com.vknxworks.textctrl.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/** A SIM (or eSIM) that can send texts. */
data class Sim(val subId: Int, val slot: Int, val label: String, val number: String?)

/** The phone's SIMs, for choosing which one a text goes out on. */
class Sims(private val context: Context) {
    private val subs: SubscriptionManager? get() = context.getSystemService(SubscriptionManager::class.java)

    private val canRead: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun active(): List<Sim> {
        if (!canRead) return emptyList()
        return runCatching {
            subs?.activeSubscriptionInfoList.orEmpty().map { info ->
                Sim(
                    subId = info.subscriptionId,
                    slot = info.simSlotIndex,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${info.simSlotIndex + 1}",
                    number = phoneNumber(info.subscriptionId),
                )
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }

    /** Your own number on that SIM, when the phone knows it. */
    private fun phoneNumber(subId: Int): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching { subs?.getPhoneNumber(subId) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Your own numbers, so a group picture message doesn't add you to your own chat. */
    fun ownNumbers(): List<String> = active().mapNotNull { it.number }

    /** The SIM Android sends texts on by default. */
    fun defaultSubId(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()

    /** The SmsManager for [subId], or the phone's default one. */
    fun smsManager(subId: Int): SmsManager {
        val base = context.getSystemService(SmsManager::class.java)
        val id = if (subId >= 0 && active().any { it.subId == subId }) subId else defaultSubId()
        return if (id >= 0) base.createForSubscriptionId(id) else base
    }

    /** The real subscription a text went out on. */
    fun resolve(subId: Int): Int {
        val list = active()
        return when {
            subId >= 0 && list.any { it.subId == subId } -> subId
            else -> defaultSubId().takeIf { it >= 0 } ?: list.firstOrNull()?.subId ?: -1
        }
    }
}
