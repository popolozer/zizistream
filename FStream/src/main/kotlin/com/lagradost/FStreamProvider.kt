package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.FStreamExtractor.invokeBlackInk
import com.lagradost.FStreamExtractor.invokeBlueSeries
import com.lagradost.FStreamExtractor.invokeDezor
import com.lagradost.FStreamExtractor.invokeTrivod
import com.lagradost.FStreamExtractor.invokeWiflix
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.extractors.VidSrcExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Locale


open class FStreamProvider : TmdbProvider() {
    override var name = "FStream"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override var lang = "fr"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    /** AUTHOR : Hexated & Sora */
    companion object {
        /** TOOLS */
        private const val tmdbAPI = "https://api.themoviedb.org/3"

        private val apiKey =
            base64DecodeAPI("ZTM=NTg=MjM=MjM=ODc=MzI=OGQ=MmE=Nzk=Nzk=ZjI=NTA=NDY=NDA=MzA=YjA=") // PLEASE DON'T STEAL

        // const val twoEmbedAPI = "https://www.2embed.to"
        /** FRENCH SOURCES */
        const val tivrod = "todrak"
        const val frenchStream = "https://french-streaming.is" // disabled
        const val blueSeries = "https://www.blueseries.uno"
        const val wiflix = "https://www.wiflix.voto"
        val blackInkUrl = base64DecodeAPI("LmlvaW5vYXJrMi5kd3d3Oi8vdHBzaHQ=") // ALLDEBRID SOURCE - PLEASE DON'T TALK TOO MUCH ABOUT THIS SOURCE PUBLICLY
        var blackInkApiAppName : String? = null
        var blackInkApiKey : String? = null

        /** VOSTFR (ENGLISH + subtitles) SOURCES */

        const val smashyStreamAPI = "https://embed.smashystream.com" // disabled


        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

        fun base64DecodeAPI(api: String): String {
            return api.chunked(4).map { base64Decode(it) }.reversed().joinToString("")
        }
    }

    data class dezorChannel(
        val name: String, // the global channel name
        val ids: List<Pair<String, String>?> = listOf(), // List<Pair("Canal+", "1167283567"), Pair("The name of the specific source", "id of the channel")>
        val logo: String? = null,
    )

    val dezorChannels: List<dezorChannel> = listOf( // Json found here: https://www.kool.to/web-tv/channels
        dezorChannel(
            "BEIN SPORTS 1",
            listOf(Pair("BEIN SPORTS 1 FHD", "3190911914"), Pair("BEIN SPORTS 1", "3276315396"), Pair("BEIN SPORTS 1 FHD", "3777541800"), Pair("BEIN SPORTS 1 (BACKUP)", "3166605092"), Pair("BEIN SPORTS 1 HD", "3833941143")),
            "https://clipground.com/images/logo-bein-sport-png-10.png"

        ),
        dezorChannel(
            "BEIN SPORTS 2",
            listOf(Pair("BEIN SPORTS 2", "2229854676"), Pair("BEIN SPORTS 2 FHD", "3634727533"), Pair("BEIN SPORTS 2 HD", "1779059572"), Pair("BEIN SPORTS 2 (BACKUP)","3235883775")),
            "https://clipground.com/images/logo-bein-sport-png-10.png"
        ),
        dezorChannel(
            "AMAZON PRIME (MATCH TIME)",
            listOf(Pair("AMAZON PRIME 1 (MATCH TIME)", "3165478429"), Pair("AMAZON PRIME 2 (MATCH TIME)", "1445692287"), Pair("AMAZON PRIME 3 (MATCH TIME)", "3111728542"), Pair("AMAZON PRIME 4 (MATCH TIME)", "1482113018"), Pair("AMAZON PRIME 5 (MATCH TIME)", "3070593307"), Pair("AMAZON PRIME 6 (MATCH TIME)", "1568887929")),
            "https://external-content.duckduckgo.com/iu/?u=https%3A%2F%2Fimages-na.ssl-images-amazon.com%2Fimages%2FG%2F01%2Fx-locale%2Fcs%2Fhelp%2Fimages%2Fgateway%2FPrime_clear-bg._CB314331504_.png&f=1&nofb=1&ipt=ffbb5a9ac6464a30047b8045695b4e192e65a0282f3224748cbde01057fac732&ipo=images"
        ),
        dezorChannel(
            "Canal +",
            listOf(Pair("Canal+ FHD", "1167283567"), Pair("Canal+ HD", "832300098"), Pair("Canal+", "4027024149"), Pair("Canal+", "2965448383"), Pair("CANAL+ GRAND ECRAN FHD", "3560753150")),
            "https://www.creads.com/wp-content/uploads/2021/05/google_avatar_canalplus.jpg"
        ),
        dezorChannel(
            "Canal + FOOT",
            listOf(Pair("Canal+ FOOT", "1917850176"), Pair("Canal+ FOOT", "3896894274"), Pair("CANAL+ PREMIER LEAGUE", "3968052032")),
            "https://www.creads.com/wp-content/uploads/2021/05/google_avatar_canalplus.jpg"
        ),
        dezorChannel(
            "Canal + SPORT",
            listOf(Pair("CANAL+ SPORT FHD", "69256085"), Pair("CANAL+ SPORT 1", "2508404066"), Pair("CANAL+ SPORT 360", "3980332539"), Pair("CANAL+ SPORT HD", "2255978217"), Pair("CANAL+ SPORT 2", "2510731168"), Pair("CANAL SPORT HD", "4143669776")),
            "https://upload.wikimedia.org/wikipedia/commons/1/1a/Canal%2B_Sport_2015.png"
        ),
        dezorChannel(
            "TF1",
            listOf(Pair("TF1 FHD", "884647994"), Pair("TF1 HD", "3381704448"), Pair("TF1", "2377044192"), Pair("TF1 SERIES & FILM", "3956331954"), Pair("TF1 SERIES & FILM HD", "1818420678")),
            "https://upload.wikimedia.org/wikipedia/commons/d/dc/TF1_logo_2013.png"
        ),
        dezorChannel(
            "France 2",
            listOf(Pair("FRANCE 2 HD", "1762746523"), Pair("FRANCE 2", "1204568908")),
            "https://upload.wikimedia.org/wikipedia/commons/3/33/France_2_logo.png"
        ),
        dezorChannel(
            "France 3",
            listOf(Pair("FRANCE 3", "694124314"), Pair("FRANCE 3 HD", "2780520453"), Pair("FRANCE 3 HD", "747162365")),
            "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/France_3_logo.png/363px-France_3_logo.png"

        ),
        dezorChannel(
            "Canal + CINEMA",
            listOf(Pair("CANAL+ CINEMA", "475297060"), Pair("CANAL+ CINEMA FHD", "2457744063"), Pair("CANAL+ CINEMA HD", "3509508771"), Pair("CANAL+ CINEMA", "1165957405")),
            "https://upload.wikimedia.org/wikipedia/fr/thumb/f/f8/Canal%2B_Cin%C3%A9ma_2013.svg/2560px-Canal%2B_Cin%C3%A9ma_2013.svg.png"
        ),
        dezorChannel(
            "Canal + F1",
            listOf(Pair("CANAL+ F1", "983820941")),
            "https://static.wikia.nocookie.net/logopedia/images/7/71/Canal_Formula1.png/revision/latest?cb=20200324160011"
        ),
        dezorChannel(
            "National Geographic",
            listOf(Pair("NAT GEO FHD", "3100166130"), Pair("NAT GEO", "150017062"), Pair("NAT GEO WILD FHD", "1535069626"), Pair("NAT GEO WILD HD", "3698416080")),
            "https://i.pinimg.com/originals/10/f1/5a/10f15ac49ed74ed8015795429a26abee.png"
        ),
        dezorChannel(
            "RMC SPORT",
            listOf(Pair("RMC SPORT 1 HD", "304213723"), Pair("RMC SPORT 1", "3569497491"), Pair("RMC SPORT 2 FHD", "2805539700"), Pair("RMC SPORT 1 FHD", "2662604721"), Pair("RMC SPORT 2 FHD", "2805539700"), Pair("RMC SPORT 2 (BACKUP)", "3252825390")),
            "https://www.tv-direct.net/wp-content/uploads/2017/05/rmc-sport-tv-logo-1280x720.jpg"
        ),
        dezorChannel(
            "M6",
            listOf(Pair("M6 FHD", "1706367784"), Pair("M6 HD", "1379034779"), Pair("M6", "2774500403")),
            "https://www.tv-direct.net/wp-content/uploads/2016/09/m6-logo-1068x600.jpg"
        ),
        dezorChannel(
            "L'EQUIPE",
            listOf(Pair("L EQUIPE", "1283657219"), Pair("L EQUIPE HD", "944795100"), Pair("LEQUIPE", "1472655688")),
            "https://assets.website-files.com/5c1922e22200fb24773c7093/5e6213a8452770ee0e455b3b_lequipe-vignette-01.png"
        ),
        dezorChannel(
            "ARTE",
            listOf(Pair("ARTE HD", "1254368368"), Pair("ARTE FHD", "2369739567"), Pair("ARTE", "3281958409")),
            "https://alloforfait.fr/wp-content/uploads/2019/12/logo-arte.jpg"
        ),
        dezorChannel(
            "ATP TENNIS TV",
            listOf(Pair("ATP TENNIS TV FHD", "1649133605")),
            "https://seeklogo.com/images/A/ATP-logo-BB517B1AC0-seeklogo.com.png"
        ),
    )

    /*
    RELEASE TYPES:
    PREMIERE(1),
    THEATRICAL_LIMITED(2),
    THEATRICAL(3),
    DIGITAL(4),
    PHYSICAL(5),
    TV(6);

     */

    private var dateNow = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val releaseFilter = "&with_release_type=4|5&release_date.lte=$dateNow" // with digital / theater release

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/all/day?api_key=$apiKey&region=FR" to "Tendances",
        "$tmdbAPI/movie/popular?api_key=$apiKey&region=FR$releaseFilter" to "Films Populaires",
        "$tmdbAPI/tv/popular?api_key=$apiKey&region=FR" to "Séries Populaires",
        "dezor" to "En direct",
//        "$tmdbAPI/tv/on_the_air?api_key=$apiKey&region=US" to "On The Air TV Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=49" to "HBO",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4330" to "Paramount+",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey&region=FR" to "Films les mieux notés",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey&region=FR" to "Séries les mieux notées",
        // "$tmdbAPI/movie/upcoming?api_key=$apiKey&region=FR" to "Films qui vont sortir",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko" to "Séries Coréennes",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=fr" to "Séries Françaises",
        "$tmdbAPI/tv/airing_today?api_key=$apiKey&region=FR" to "Séries sortant aujourd'hui",
        "$tmdbAPI/tv/airing_today?api_key=$apiKey&with_keywords=210024|222243&sort_by=primary_release_date.desc" to "Animes sortant aujourd'hui",
        "$tmdbAPI/tv/on_the_air?api_key=$apiKey&with_keywords=210024|222243&sort_by=primary_release_date.desc" to "Animes en cours",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243" to "Animes",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_keywords=210024|222243$releaseFilter" to "Films Animes",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    private fun isUpcoming(dateString: String?) : Boolean {
        if(dateString == null) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = format.parse(dateString)?.time ?: return false
        return unixTimeMS < dateTime
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        return if (request.data == "dezor") {
            // dezor response
            if (page==1) {
                val home = dezorChannels.map { channel ->
                    newMovieSearchResponse(
                        channel.name,
                        url = Data(
                            liveChannelsData = channel.ids.toJson(),
                            liveChannelName = channel.name,
                            liveChannelLogo = channel.logo
                        ).toJson(),
                        type = TvType.Live,
                    ) {
                        this.posterUrl = channel.logo
                    }
                } ?: throw ErrorLoadingException("Empty channel list")
                newHomePageResponse(request.name, home)
            }
            else {
                newHomePageResponse(listOf())
            }
        } else {
            val type = if (request.data.contains("/movie")) "movie" else "tv"
            val home = app.get("${request.data}$adultQuery&page=$page&language=fr")
                .parsedSafe<Results>()?.results
                ?.mapNotNull { media ->
                    media.toSearchResponse(type)
                } ?: throw ErrorLoadingException("Invalid Json reponse")
            newHomePageResponse(request.name, home)
        }
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get(
            "$tmdbAPI/search/multi?api_key=$apiKey&language=fr&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}"
        ).parsedSafe<Results>()?.results?.mapNotNull { media ->
            media.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        if(data.liveChannelsData != null) {
            return newMovieLoadResponse(
                data.liveChannelName ?: "Live",
                url = FrenchLinkData(liveChannelsData=data.liveChannelsData, type="live").toJson(),
                dataUrl = FrenchLinkData(liveChannelsData=data.liveChannelsData, type="live").toJson(),
                type = TvType.Live,
            ) {
                this.posterUrl = data.liveChannelLogo
            }

        }
        val type = getType(data.type)
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&language=fr&append_to_response=keywords,credits,external_ids,videos,recommendations"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&language=fr&append_to_response=keywords,credits,external_ids,videos,recommendations"
        }
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val rating = res.vote_average.toString().toRatingInt()
        val genres = res.genres?.mapNotNull { it.name }
        val isAnime =
            genres?.contains("Animation") == true && (res.original_language == "zh" || res.original_language == "ja")
        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }

        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName ?: return@mapNotNull null,
                    getImageUrl(cast.profilePath)
                ),
                roleString = cast.character
            )
        } ?: return null

        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }
            ?.randomOrNull()

        return if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey&language=fr")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        Episode(
                            FrenchLinkData(
                                tmdbid = data.id,
                                imdbId = res.external_ids?.imdb_id,
                                type = data.type,
                                season = eps.seasonNumber,
                                episode = eps.episodeNumber,
                                frenchTitle = title,
                                year = season.airDate?.split("-")?.first()?.toIntOrNull() ?: year,
                                orgTitle = orgTitle,
                                isAnime = isAnime,
                                epsTitle = eps.name,
                            ).toJson(),
                            name = eps.name + if(isUpcoming(eps.airDate)) " - [UPCOMING]" else "",
                            season = eps.seasonNumber,
                            episode = eps.episodeNumber,
                            posterUrl = getImageUrl(eps.stillPath),
                            rating = eps.voteAverage?.times(10)?.roundToInt(),
                            description = eps.overview
                        ).apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            newTvSeriesLoadResponse(
                title,
                url,
                if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = if (isAnime) keywords else genres
                this.rating = rating
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
            }
        } else {

            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                FrenchLinkData(
                    tmdbid = data.id,
                    imdbId = res.external_ids?.imdb_id,
                    type = data.type,
                    frenchTitle = title,
                    year = year,
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    frenchPosterPath = res.posterPath
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = if (isAnime) keywords else genres
                this.rating = rating
                this.recommendations = recommendations
                this.comingSoon = isUpcoming(releaseDate)
                this.actors = actors
                addTrailer(trailer)
            }
        }
    }

    override suspend fun extractorVerifierJob(extractorData: String?) {
        if (extractorData == null) return
        VidSrcExtractor.validatePass(extractorData)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = parseJson<FrenchLinkData>(data)
        if(res.type == "live") {
            argamap(
                {
                    invokeDezor(
                        res.liveChannelsData,
                        callback,
                    )
                },
            )
            return true
        }
        argamap(

            {
                if (res.type == "movie") {
                    invokeTrivod(
                        res.frenchTitle,
                        res.year,
                        callback,
                    )
                }
            }, //TODO RE-ENABLE


            /*{
                invokeFrenchStream(
                    res.frenchTitle,
                    res.season,
                    res.episode,
                    res.frenchSynopsis,
                    subtitleCallback, // may be useless
                    callback,
                )
            },*/
            {
                if (res.type == "tv") {
                    invokeBlueSeries(
                        res.frenchTitle,
                        res.season,
                        res.episode,
                        res.year,
                        subtitleCallback,
                        callback,
                    )
                }
            },
            {
                invokeWiflix(
                    res.frenchTitle,
                    res.season,
                    res.episode,
                    res.frenchSynopsis,
                    subtitleCallback, // may be useless
                    callback,
                )
            },
            /*{
                invokeJellyfin(
                    res.frenchTitle,
                    res.tmdbid,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback,
                )
            },*/
            /*
            {
                invokeSmashyStream(res.imdbId, res.season, res.episode, res.isAnime, subtitleCallback, callback)
            },*/

            {
                if (blackInkApiKey != null && blackInkApiAppName != null)
                    invokeBlackInk(
                        res.frenchTitle,
                        res.season,
                        res.episode,
                        res.tmdbid,
                        res.year,
                        subtitleCallback, // may be useless
                        callback,
                    )
            },
        )
        return true
    }




    data class FrenchLinkData(
        val tmdbid: Int? = null,
        val imdbId: String? = null,
        val type: String? = null, // "anime" | "movie" | "tv" | "live"
        val season: Int? = null,
        val episode: Int? = null,
        val frenchTitle: String? = null,
        val orgTitle: String? = null,
        val year: Int? = null, // val airedYear: Int? = null,
        val epsTitle: String? = null,
        val frenchSynopsis: String? = null,
        val originalSynopsis: String? = null,
        val frenchPosterPath: String? = null,
        val isAnime: Boolean = false,
        val liveChannelsData: String? = null,
    )

    data class Data(
        val id: Int? = null,
        val type: String? = null,
        val aniId: String? = null,
        val malId: Int? = null,
        val liveChannelsData: String? = null,
        val liveChannelName: String? = null,
        val liveChannelLogo: String? = null,
    )

    data class Results(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
    )

    data class Genres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class Keywords(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class KeywordResults(
        @JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    )

    data class Seasons(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("known_for_department") val knownForDepartment: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null,
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null,
        @JsonProperty("tvdb_id") val tvdb_id: String? = null,
    )

    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class LastEpisodeToAir(
        @JsonProperty("episode_number") val episode_number: Int? = null,
        @JsonProperty("season_number") val season_number: Int? = null,
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val vote_average: Any? = null,
        @JsonProperty("original_language") val original_language: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: KeywordResults? = null,
        @JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("videos") val videos: ResultsTrailer? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
    )

    data class EmbedJson(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("sources") val sources: List<String?> = arrayListOf(),
        @JsonProperty("tracks") val tracks: List<String>? = null,
    )

    data class MovieHabData(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("token") val token: String? = null,
    )

    data class MovieHabRes(
        @JsonProperty("data") val data: MovieHabData? = null,
    )

}
