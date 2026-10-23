package com.vknxworks.textctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object ChatsRoute
@Serializable data object ArchivedRoute
@Serializable data object SearchRoute

/** One chat. [highlight] is a message to scroll to (from search). */
@Serializable data class ChatRoute(val threadId: Long, val highlight: String? = null)
/** Choosing who to write to. [forward] picks up text and files waiting in [com.vknxworks.textctrl.AppContainer.composeDraft]. */
@Serializable data class NewChatRoute(val forward: Boolean = false)

@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object BlockedRoute
