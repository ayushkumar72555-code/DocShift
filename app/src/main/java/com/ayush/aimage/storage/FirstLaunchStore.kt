package com.ayush.aimage.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("aimage_prefs")

object FirstLaunchStore {

    private val FIRST_LAUNCH = booleanPreferencesKey("first_launch")

    suspend fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[FIRST_LAUNCH] != false
    }

    suspend fun setLaunched(context: Context) {
        context.dataStore.edit {
            it[FIRST_LAUNCH] = false
        }
    }
}
