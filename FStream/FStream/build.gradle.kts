dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
}

// use an integer for version numbers
version = 5


cloudstream {
    language = "fr"
    // All of these properties are optional, you can safely remove them

    description = "Pas de nouvelles mises à jour prévues"
    authors = listOf("codeberg.org/7TE/FStream")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )
    requiresResources = true
}
