package com.davide.seddio.easygallery.data

import android.content.Context
import java.io.InputStream

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

    /** Opens only [ADMIN1] and [COUNTRIES]; same exceptions as [load]. */
    fun loadNames(context: Context): GazetteerNames {
        val assets = context.applicationContext.assets
        return loadNames { assets.open(it) }
    }

    // The seam that lets a test prove which assets are opened.
    internal fun loadNames(open: (String) -> InputStream): GazetteerNames =
        open(ADMIN1).use { admin1 ->
            open(COUNTRIES).use { countries -> GazetteerNames.load(admin1, countries) }
        }
}
