package app.auralis.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class AuralisApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.client.http)
            // Authenticated artwork URLs can contain API keys or encoded passwords.
            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
            .crossfade(380)
            .build()

    companion object {
        lateinit var instance: AuralisApp
            private set
    }
}
