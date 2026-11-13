package com.vknxworks.textctrl

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.textctrl.ui.lock.AppLock
import kotlinx.coroutines.launch

/** A string from strings.xml, for code outside Compose (errors, notifications, toasts). */
fun str(@StringRes id: Int, vararg args: Any): String = TextCtrlApp.instance.getString(id, *args)

class TextCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        container.notifier.createChannels()
        container.contacts.refresh()
        // Alarms don't survive a restart or an update: set the schedule and the daily clean-up again.
        container.scheduler.restore()
        container.scheduler.setDailyCleanup(true)
        container.appScope.launch { runCatching { container.cleaner.runIfDue() } }
        container.widgets.start()
        AppLock.onLocked = { container.onLocked() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val s = container.settings.settings.value
                AppLock.onForeground(container.vault.enabled, s.lockTimeout.millis)
                container.refreshDefault()
            }

            override fun onStop(owner: LifecycleOwner) {
                val s = container.settings.settings.value
                AppLock.onBackground(container.vault.enabled, s.lockTimeout.millis)
                // Hidden chats close as soon as you leave the app (not for a picker or share sheet it opened).
                if (!AppLock.exemptTrip) container.closeHidden()
            }
        })
    }

    companion object {
        lateinit var instance: TextCtrlApp
            private set
    }
}
