package app.auralis.music.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object WifiGate {
    /** True when the active network has Wi‑Fi transport and is not cellular. */
    fun isWifi(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isMetered(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return cm.isActiveNetworkMetered
    }

    /** HiRes / original offline downloads may proceed. */
    fun allowHiResDownload(context: Context, wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        return isWifi(context) && !isMetered(context)
    }
}
