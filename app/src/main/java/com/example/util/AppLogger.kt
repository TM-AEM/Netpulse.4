package com.example.util

import android.util.Log

object AppLogger {
    private const val TAG_PREFIX = "NetPulse_"

    fun d(tag: String, message: String) {
        try {
            Log.d("$TAG_PREFIX$tag", message)
        } catch (_: RuntimeException) {
            println("DEBUG: $TAG_PREFIX$tag: $message")
        }
    }

    fun i(tag: String, message: String) {
        try {
            Log.i("$TAG_PREFIX$tag", message)
        } catch (_: RuntimeException) {
            println("INFO: $TAG_PREFIX$tag: $message")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        try {
            Log.w("$TAG_PREFIX$tag", message, throwable)
        } catch (_: RuntimeException) {
            println("WARN: $TAG_PREFIX$tag: $message ${throwable?.message ?: ""}")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        try {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        } catch (_: RuntimeException) {
            System.err.println("ERROR: $TAG_PREFIX$tag: $message ${throwable?.message ?: ""}")
        }
    }
}
