package com.sharek.app

import androidx.compose.runtime.mutableStateListOf

/** قائمة الملفات المحددة للإرسال، مشتركة بين شاشات الاختيار وشاشة الإرسال. */
object Selection {
    val files = mutableStateListOf<OutFile>()

    fun add(items: List<OutFile>) {
        for (i in items) if (files.none { it.uri == i.uri }) files.add(i)
    }

    fun remove(f: OutFile) { files.remove(f) }
    fun clear() { files.clear() }
    fun totalSize(): Long = files.sumOf { it.size }
}
