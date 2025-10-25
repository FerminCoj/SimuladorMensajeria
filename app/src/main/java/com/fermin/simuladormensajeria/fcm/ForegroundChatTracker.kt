package com.fermin.simuladormensajeria.fcm

import android.content.Context
import androidx.core.content.edit

object ForegroundChatTracker {
    private const val PREF = "chat_prefs"
    private const val KEY_ACTIVE_CHAT = "active_chat_id"

    fun setActiveChat(context: Context, chatId: String?) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit {
            if (chatId == null) remove(KEY_ACTIVE_CHAT)
            else putString(KEY_ACTIVE_CHAT, chatId)
        }
    }

    fun getActiveChatId(context: Context): String? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getString(KEY_ACTIVE_CHAT, null)
    }
}
