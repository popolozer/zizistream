package com.lagradost

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.FStreamProvider

@CloudstreamPlugin
class FStreamProviderPlugin : Plugin() {
    val fstreamApi = FStreamApi(0)

    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        fstreamApi.init()
        registerMainAPI(FStreamProvider())
        registerExtractorAPI(DoodsProExtractor())
        ioSafe {
            fstreamApi.initialize()
        }
    }

    init {
        this.openSettings = {
            val activity = it as? AppCompatActivity
            if (activity != null) {
                val frag = FStreamSettingsFragment(this, fstreamApi)
                frag.show(activity.supportFragmentManager, fstreamApi.name)
            }
        }
    }
}
