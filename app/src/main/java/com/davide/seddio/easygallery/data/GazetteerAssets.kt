package com.davide.seddio.easygallery.data

import android.content.Context

/** The Android side of the gazetteer: opens the three packaged assets and hands them to the pure loader. */
object GazetteerAssets {
    const val PLACES = "gazetteer/places.tsv"
    const val ADMIN1 = "gazetteer/admin1.tsv"
    const val COUNTRIES = "gazetteer/countries.tsv"

    /** Throws an IOException: [GazetteerLoadException] for an empty, truncated, mis-versioned or malformed asset, or a missing one. */
    fun load(context: Context, capKm: Double = GAZETTEER_CAP_KM): Gazetteer {
        val assets = context.applicationContext.assets
        return assets.open(PLACES).use { places ->
            assets.open(ADMIN1).use { admin1 ->
                assets.open(COUNTRIES).use { countries ->
                    Gazetteer.load(places, admin1, countries, capKm)
                }
            }
        }
    }
}
