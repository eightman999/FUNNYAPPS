package com.shunlight_library.novel_reader

import android.app.Application
import android.content.Context

class NovelReaderApplication : Application() {
    companion object {
        private lateinit var instance: NovelReaderApplication

        fun getAppContext(): Context = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}