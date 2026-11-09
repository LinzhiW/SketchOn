package com.example.gemtronicring.p4e7a91c3.data.local

import android.content.Context
import com.example.gemtronicring.p4e7a91c3.data.remote.Session

/** Keeps the signed-in user across app launches. */
interface SessionStore {
    fun load(): Session?

    fun save(session: Session)

    fun clear()
}

/** In the app's private preferences; other apps cannot read it. */
class PreferencesSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    override fun load(): Session? {
        val token = prefs.getString(TOKEN, null) ?: return null
        val name = prefs.getString(NAME, null) ?: return null
        return Session(token, name)
    }

    override fun save(session: Session) {
        prefs.edit().putString(TOKEN, session.token).putString(NAME, session.name).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TOKEN = "token"
        const val NAME = "name"
    }
}
