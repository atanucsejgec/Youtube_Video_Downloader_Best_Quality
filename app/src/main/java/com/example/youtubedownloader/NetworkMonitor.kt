package com.example.youtubedownloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkMonitor {

    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun canDownload(context: Context): Boolean {
        if (!isConnected(context)) return false
        if (AppPrefs.isWifiOnly(context) && !isWifi(context)) return false
        return true
    }

    fun getNetworkType(context: Context): String = when {
        !isConnected(context) -> "No Connection"
        isWifi(context) -> "Wi-Fi"
        else -> "Mobile Data"
    }
}