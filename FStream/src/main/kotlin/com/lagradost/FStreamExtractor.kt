package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.FStreamProvider.Companion.smashyStreamAPI
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element

val session = Session(Requests().baseClient)

object FStreamExtractor : FStreamProvider() {

    suspend fun invokeTrivod(
        frenchTitle: String? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val homepath = app.get("https://$tivrod.com").document.select("a#${tivrod}c").attr("href")
        frenchTitle ?: throw ErrorLoadingException("Empty searched title")
        val document = app.post(
            "https://$tivrod.com/$homepath/home/$tivrod",
            data = mapOf("searchword" to frenchTitle.replace(" ", "+").take(20))
        ).document
        val url = document.select("div.column1 > div#hann").first { element ->
            element.select("a").text().contains("($year)", ignoreCase = true)
        }.select("a").attr("href")
        val mainPage = app.get("https://$tivrod.com$url").document
        val iframe = mainPage.selectFirst("iframe")?.attr("src")
            ?: throw ErrorLoadingException(" empty iframe 1212")
        val content = app.get(iframe).document.select("div.video > script").html().toString()
        val m3u8 = Regex("file: \"(.*?)\"").find(content)?.groupValues?.get(1)
            ?: throw ErrorLoadingException(" empty m3u8")
        callback.invoke(
            ExtractorLink(
                tivrod,
                tivrod,
                m3u8,
                "",
                quality = Qualities.P720.value,
                isM3u8 = true,
            )
        )
    }


    private val listOfIps = listOf("45.84.214.194", "45.84.214.171",  "45.84.214.173", "77.247.109.21")

    private suspend fun getDezorServer(
        streamUrl: String?
    ): String? {
        listOfIps.forEach { ip ->
            val serverUrl = streamUrl?.replace(Regex("http(\\S*?)\\/sunshine\\/"), "http://${ip}:8008/sunshine/") ?: throw ErrorLoadingException("Invalid regex replace")
            val isServerWorking = try {
                app.get(serverUrl, timeout = 5).isSuccessful
            } catch (e: Exception){
                false
            }
            if (isServerWorking)  {
                return serverUrl
            }
        }
        return null
    }

    suspend fun invokeDezor(
        liveChannelsData: String? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        tryParseJson<List<Pair<String, String>?>>(liveChannelsData)?.apmap { channel ->
            channel?.first ?: return@apmap
            channel.second ?: return@apmap
            val url = app.get("https://www.kool.to/web-tv/play/${channel.second}/index.m3u8", allowRedirects = false).okhttpResponse.headers["Location"] ?: return@apmap
            val streamUrl = getDezorServer(url) ?: return@apmap
            callback.invoke(
                ExtractorLink(
                    channel.first,
                    channel.first,
                    streamUrl,
                    "",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true,
                )
            )
        }
    }

    private fun Element.FrenchStreamtoSearchResponse(): SearchResponse {
        val posterUrl = fixUrl(select("a.short-poster > img").attr("src"))
        val qualityExtracted = select("span.film-ripz > a").text()
        val type = select("span.mli-eps").text().lowercase()
        val title = select("div.short-title").text()
        val link = select("a.short-poster").attr("href").replace("wvw.", "") //wvw is an issue

        if (!type.contains("eps")) {
            return MovieSearchResponse(
                name = title,
                url = link,
                apiName = title,
                type = TvType.Movie,
                posterUrl = posterUrl,

                )


        } else  // a Serie
        {
            return newAnimeSearchResponse(
                name = title,
                url = link,
                type = TvType.TvSeries,

                ) {
                this.posterUrl = posterUrl
                addDubStatus(
                    isDub = select("span.film-verz").text().uppercase().contains("VF"),
                    episodes = select("span.mli-eps>i").text().toIntOrNull()
                )
            }


        }
    }

    private fun translate(
        // the website has weird naming of series for episode 2 and 1 and original version content
        episodeNumber: String,
        is_vf_available: Boolean,
    ): String {
        return if (episodeNumber == "1") {
            if (is_vf_available) {  // 1 translate differently if vf is available or not
                "FGHIJK"
            } else {
                "episode033"
            }
        } else {
            "episode" + (episodeNumber.toInt() + 32).toString()
        }
    }

    private fun Element.blackInktoLink(): String {
        return this.select("div.video-list > div.video-list-image > a").attr("href")
    }



    data class DarkInkJson (
        //@JsonProperty("fingerprint" ) var fingerprint : Fingerprint? = Fingerprint(),
        //@JsonProperty("effects"     ) var effects     : Effects?     = Effects(),
        @JsonProperty("serverMemo"  ) var serverMemo  : ServerMemo?  = ServerMemo()

    )

    data class ServerMemo (

        @JsonProperty("children" ) var children : ArrayList<String> = arrayListOf(),
        @JsonProperty("errors"   ) var errors   : ArrayList<String> = arrayListOf(),
        @JsonProperty("htmlHash" ) var htmlHash : String?           = null,
        @JsonProperty("data"     ) var data     : DarkInkData?      = DarkInkData(),
        //@JsonProperty("dataMeta" ) var dataMeta : DataMeta?         = DataMeta(),
        @JsonProperty("checksum" ) var checksum : String?           = null

    )

    val blueSeriesYears = arrayListOf(
        Pair("32", "2021"),
        Pair("33", "2020"),
        Pair("34", "2019"),
        Pair("35", "2018"),
        Pair("36", "2017"),
        Pair("37", "2016"),
        Pair("38", "2015"),
        Pair("39", "2014"),
        Pair("40", "2013"),
        Pair("41", "2012"),
        Pair("42", "2011"),
        Pair("43", "2010"),
        Pair("44", "2009"),
        Pair("45", "2008"),
        Pair("46", "2007"),
        Pair("47", "2006"),
        Pair("48", "2005"),
        Pair("49", "2004"),
        Pair("50", "2003"),
        Pair("51", "2002"),
        Pair("52", "2001"),
        Pair("53", "2000"),
        Pair("54", "1999"),
        Pair("55", "1998"),
        Pair("56", "1997"),
        Pair("57", "1996"),
        Pair("58", "1995"),
        Pair("59", "1994"),
        Pair("60", "1993"),
        Pair("61", "1992"),
        Pair("62", "1991"),
        Pair("63", "1990"),
        Pair("64", "1989"),
        Pair("65", "1988"),
        Pair("66", "1987"),
        Pair("67", "1986"),
        Pair("68", "1985"),
        Pair("69", "1984"),
        Pair("70", "1983"),
        Pair("71", "1982"),
        Pair("72", "1981"),
        Pair("73", "1980"),
    )

    val darkInkLanguage = arrayListOf(
        Pair("53", "Albanian"),
        Pair("2", "Arab"),
        Pair("3", "Bengali"),
        Pair("35", "Bulgare"),
        Pair("4", "Chinese"),
        Pair("78", "Croatian"),
        Pair("49", "Czech"),
        Pair("23", "Danish"),
        Pair("40", "Dutch"),
        Pair("5", "English"),
        Pair("28", "Finnish"),
        Pair("6", "French"),
        Pair("7", "French (Canada)"),
        Pair("9", "German"),
        Pair("57", "Greek"),
        Pair("43", "Hebrew"),
        Pair("10", "Hindi"),
        Pair("61", "Hungarian"),
        Pair("42", "Indonesian"),
        Pair("11", "Italian"),
        Pair("12", "Japanese"),
        Pair("13", "Korean"),
        Pair("71", "Lithuanian"),
        Pair("84", "Malay"),
        Pair("65", "Malaysian"),
        Pair("14", "Mandarin"),
        Pair("106", "Muet"),
        Pair("66", "Norwegian"),
        Pair("41", "Persian"),
        Pair("68", "Polish"),
        Pair("15", "Portuguese"),
        Pair("90", "Romanian"),
        Pair("16", "Russian"),
        Pair("17", "Spanish"),
        Pair("33", "Swedish"),
        Pair("105", "Sámegiella"),
        Pair("44", "Thai"),
        Pair("8", "TrueFrench"),
        Pair("18", "Turkish"),
        Pair("96", "Ukrainian"),
        Pair("102", "Vietnamese"),
        Pair("19", "unknown"),
        Pair("1", "MULTI"),
    )


    val darkInkQualities = arrayListOf(

        Pair("1", "DVDRIP"),
        Pair("2", "BDRIP"),
        Pair("3", "BRRIP"),
        Pair("4", "Webrip"),
        Pair("14", "HDTV"),
        Pair("15", "HDRip"),
        Pair("20", "TVrip"),
        Pair("21", "TeleCine (TC)"),
        Pair("33", "DVDRIP LD"),
        Pair("35", "BDRIP LD"),
        Pair("37", "BRRIP LD"),
        Pair("43", "Web-DL"),
        Pair("51", "DVDRIP MKV"),
        Pair("5", "TS"),
        Pair("6", "CAM"),
        Pair("7", "DVDSCR"),
        Pair("8", "R5"),
        Pair("9", "R6"),
        Pair("19", "PDTV"),
        Pair("32", "DVDRIP MD"),
        Pair("34", "BDRIP MD"),
        Pair("36", "BRRIP MD"),
        Pair("38", "DVDSCR MD"),
        Pair("39", "DVDSCR LD"),
        Pair("40", "R5 MD"),
        Pair("41", "TS MD"),
        Pair("42", "TS LD"),
        Pair("44", "HDRiP MD"),
        Pair("45", "HDTS MD"),
        Pair("46", "CAM MD"),
        Pair("47", "HDCAM"),
        Pair("48", "TC"),
        Pair("10", "DVD-R"),
        Pair("11", "Full-DVD"),
        Pair("16", "Blu-Ray 720p"),
        Pair("17", "Blu-Ray 1080p"),
        Pair("18", "Blu-Ray 3D"),
        Pair("31", "HD 720p"),
        Pair("49", "HDLight 720p"),
        Pair("50", "HDLight 1080p"),
        Pair("52", "HD 1080p"),
        Pair("53", "ULTRA HD (x265)"),
        Pair("54", "WEB-DL 720p"),
        Pair("55", "WEB-DL 1080p"),
        Pair("57", "REMUX"),
        Pair("60", "Ultra HDLight (x265)"),
        Pair("61", "HDTV 720p"),
        Pair("62", "HDTV 1080p"),
        Pair("12", "IMG"),
        Pair("13", "ISO"),
        Pair("22", "EXE"),
        Pair("23", "MP3"),
        Pair("24", "FLAC"),
        Pair("25", "M4A"),
        Pair("26", "PDF"),
        Pair("27", "Autre"),
        Pair("28", "CBR"),
        Pair("29", "CBZ"),
        Pair("30", "IPA"),
        Pair("56", "ARCHIVE"),
        Pair("58", "epub"),
        Pair("59", "PKG"),
    )

    data class DarkInkData (

        @JsonProperty("id_post"                             ) var idPost                              : Int?              = null,
        @JsonProperty("id_host"                             ) var idHost                              : Int?              = null,
        @JsonProperty("recaptcha"                           ) var recaptcha                           : String?           = null,
        @JsonProperty("id_lien"                             ) var idLien                              : Int?              = null,
        @JsonProperty("post"                                ) var post                                : ArrayList<String> = arrayListOf(),
        @JsonProperty("liens"                               ) var liens                               : ArrayList<DarkInkLiens>  = arrayListOf(),
        @JsonProperty("video"                               ) var video                               : String?           = null,
        @JsonProperty("loadData"                            ) var loadData                            : Boolean?          = null,
        @JsonProperty("debrided_link"                       ) var debridedLink                        : String?           = null,
        @JsonProperty("book"                                ) var book                                : String?           = null,
        @JsonProperty("tableRecordsPerPage"                 ) var tableRecordsPerPage                 : Int?              = null,
        @JsonProperty("page"                                ) var page                                : Int?              = null,
        //@JsonProperty("paginators"                          ) var paginators                          : Paginators?       = Paginators(),
        @JsonProperty("isTableReordering"                   ) var isTableReordering                   : Boolean?          = null,
        @JsonProperty("tableColumnSearchQueries"            ) var tableColumnSearchQueries            : ArrayList<String> = arrayListOf(),
        @JsonProperty("tableSearchQuery"                    ) var tableSearchQuery                    : String?           = null,
        @JsonProperty("selectedTableRecords"                ) var selectedTableRecords                : ArrayList<String> = arrayListOf(),
        @JsonProperty("tableSortColumn"                     ) var tableSortColumn                     : String?           = null,
        @JsonProperty("tableSortDirection"                  ) var tableSortDirection                  : String?           = null,
        @JsonProperty("toggledTableColumns"                 ) var toggledTableColumns                 : ArrayList<String> = arrayListOf(),
        @JsonProperty("isTableLoaded"                       ) var isTableLoaded                       : Boolean?          = null,
        @JsonProperty("mountedTableAction"                  ) var mountedTableAction                  : String?           = null,
        @JsonProperty("mountedTableActionData"              ) var mountedTableActionData              : ArrayList<String> = arrayListOf(),
        @JsonProperty("mountedTableActionRecord"            ) var mountedTableActionRecord            : String?           = null,
        @JsonProperty("mountedTableBulkAction"              ) var mountedTableBulkAction              : String?           = null,
        @JsonProperty("mountedTableBulkActionData"          ) var mountedTableBulkActionData          : ArrayList<String> = arrayListOf(),
        //@JsonProperty("tableFilters"                        ) var tableFilters                        : TableFilters?     = TableFilters(),
        @JsonProperty("componentFileAttachments"            ) var componentFileAttachments            : ArrayList<String> = arrayListOf(),
        @JsonProperty("mountedFormComponentAction"          ) var mountedFormComponentAction          : String?           = null,
        @JsonProperty("mountedFormComponentActionArguments" ) var mountedFormComponentActionArguments : ArrayList<String> = arrayListOf(),
        @JsonProperty("mountedFormComponentActionData"      ) var mountedFormComponentActionData      : ArrayList<String> = arrayListOf(),
        @JsonProperty("mountedFormComponentActionComponent" ) var mountedFormComponentActionComponent : String?           = null,
        @JsonProperty("episodes_liens_host_1Page"           ) var episodesLiensHost1Page              : Int?              = null

    )

    data class DarkInkLiens (
        @JsonProperty("id_lien"      ) var idLien      : Int?       = null,
        @JsonProperty("lien"         ) var lien        : String?    = null,
        @JsonProperty("id_host"      ) var idHost      : Int?       = null,
        @JsonProperty("id_post"      ) var idPost      : Int?       = null,
        @JsonProperty("id_user"      ) var idUser      : String?    = null,
        @JsonProperty("id_link"      ) var idLink      : String?    = null,
        @JsonProperty("idallo"       ) var idallo      : String?    = null,
        @JsonProperty("taille"       ) var taille      : Long?       = null,
        @JsonProperty("id_partie"    ) var idPartie    : String?    = null,
        @JsonProperty("total_parts"  ) var totalParts  : Int?       = null,
        @JsonProperty("numero"       ) var numero      : Int?       = null,
        @JsonProperty("episode"      ) var episode     : Int?       = null,
        @JsonProperty("full_saison"  ) var fullSaison  : Int?       = null,
        @JsonProperty("vivant"       ) var vivant      : Int?       = null,
        @JsonProperty("reported"     ) var reported    : Int?       = null,
        @JsonProperty("langue"       ) var langue      : Int?       = null,
        @JsonProperty("qualite"      ) var qualite     : Int?       = null,
        @JsonProperty("saison"       ) var saison      : Int?       = null,
        @JsonProperty("sub"          ) var sub         : Int?       = null,
        @JsonProperty("multilang"    ) var multilang   : Multilang? = Multilang(),
        @JsonProperty("active"       ) var active      : Int?       = null,
        @JsonProperty("view"         ) var view        : Int?       = null,
        @JsonProperty("streaming"    ) var streaming   : Int?       = null,
        @JsonProperty("revived"      ) var revived     : String?    = null,
        @JsonProperty("from_user"    ) var fromUser    : Int?       = null,
        @JsonProperty("queue_check"  ) var queueCheck  : String?    = null,
        @JsonProperty("last_dl"      ) var lastDl      : String?    = null,
        @JsonProperty("checked_date" ) var checkedDate : String?    = null,
        @JsonProperty("updated_at"   ) var updatedAt   : String?    = null,
        @JsonProperty("created_at"   ) var createdAt   : String?    = null,
        @JsonProperty("deleted_at"   ) var deletedAt   : String?    = null

    )



    data class Multilang (
        @JsonProperty("lang" ) var lang : ArrayList<String?> = arrayListOf(),
        @JsonProperty("sub"  ) var sub  : ArrayList<String?> = arrayListOf()
    )

    data class uptoboxLink(
        var iframeLink: String,
        var mediaName: String? = null,
        var size: Double? = null, // used for sorting
    )

    data class debridResponse (
        @JsonProperty("status") var status : String? = null,
        @JsonProperty("data") var data : AllDebridData? = null,

        )

    data class Subtitles (
        @JsonProperty("kind"     ) var kind     : String? = null,
        //@JsonProperty("baseLink" ) var baseLink : String? = null,
        @JsonProperty("videoId"  ) var videoId  : String? = null,
        @JsonProperty("id"       ) var id       : Int?    = null,
        @JsonProperty("type"     ) var type     : String? = null,
        @JsonProperty("format"   ) var format   : String? = null,
        @JsonProperty("name"     ) var name     : String? = null,
        @JsonProperty("src"      ) var src      : String? = null,
        @JsonProperty("srclang"  ) var srclang  : String? = null,
        @JsonProperty("label"    ) var label    : String? = null

    )

    data class AllDebridData (
        @JsonProperty("link"       ) var link       : String?              = null,
        @JsonProperty("host"       ) var host       : String?              = null,
        @JsonProperty("filename"   ) var filename   : String?              = null,
        @JsonProperty("streaming"  ) var streaming  : ArrayList<String>    = arrayListOf(),
        @JsonProperty("paws"       ) var paws       : Boolean?             = null,
        @JsonProperty("filesize"   ) var filesize   : Long?                 = null,
        @JsonProperty("subtitles"  ) var subtitles  : ArrayList<Subtitles> = arrayListOf(),
        //@JsonProperty("player"     ) var player     : Player?              = Player(),
        @JsonProperty("streams"    ) var streams    : ArrayList<Streams>   = arrayListOf(),
        @JsonProperty("id"         ) var id         : String?              = null,
        @JsonProperty("hostDomain" ) var hostDomain : String?              = null
    )

    data class Info (
        @JsonProperty("link") val link: String,
        @JsonProperty("size") val size: Long,
    )

    data class Streams (

        @JsonProperty("quality"  ) var quality  : String? = null,
        @JsonProperty("ext"      ) var ext      : String? = null,
        @JsonProperty("filesize" ) var filesize : Long?    = null,
        @JsonProperty("name"     ) var name     : String? = null,
        @JsonProperty("link"     ) var link     : String? = null,
        @JsonProperty("id"       ) var id       : String? = null

    )

    suspend fun invokeFrenchStream(
        frenchTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        frenchSynopsis: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val invokedSourceName = "FrenchStream"
        frenchTitle ?: throw ErrorLoadingException(" empty frenchTitle")
        val searchedText = if (season == null) {
            frenchTitle.replace(" ", "+")
        } else {
            frenchTitle.replace(" ", "+") + " Saison $season"
        }
        val link = "$frenchStream/?do=search&subaction=search&story=$searchedText" // search
        val document =
            app.post(link).document // app.get() permet de télécharger la page html avec une requete HTTP (get)
        val results = document.select("div#dle-content > div.short")
        val allresultshome =
            results.mapNotNull { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
                article.FrenchStreamtoSearchResponse()
            }.take(2) /* .sortedBy {
                FuzzySearch.ratio(frenchTitle, it.name)
            }*/

        allresultshome.apmap { searchResult ->
            val soup = app.get(searchResult.url).document
            val isSerie = searchResult.url.contains("saison-", ignoreCase = true) // TODO FIX ??
            //val description =
            //    soup.selectFirst("div.fdesc")?.text().toString().substringAfterLast(": ")
            // if (season == null && FuzzySearch.ratio(frenchSynopsis, description) < 90) { // todo ratio might not be in a scale of 100
            //    throw ErrorLoadingException("Ligne 146")
            //}


            // val listEpisode = soup.select("div.elink")
            //val rating = soup.select("span[id^=vote-num-id]")?.getOrNull(1)?.text()?.toInt()


            val servers =
                if (isSerie) {

                    val wantedEpisode =
                        if (episode == 2) { // the episode number 2 has id of ABCDE, don't ask any question
                            "ABCDE"
                        } else {
                            "episode" + episode.toString()
                        }

                    val div =
                        if (wantedEpisode == "episode1") {
                            "> div.tabs-sel "  // this element is added when the wanted episode is one (the place changes in the document)
                        } else {
                            ""
                        }
                    val serversvf =// French version servers
                        soup.select("div#$wantedEpisode > div.selink > ul.btnss $div> li")
                            .mapNotNull { li ->  // list of all french version servers
                                val serverUrl = fixUrl(li.selectFirst("a")!!.attr("href"))
                                if (serverUrl.isNotBlank()) {
                                    if (li.text().replace("&nbsp;", "").replace(" ", "")
                                            .isNotBlank()
                                    ) {
                                        Pair(li.text().replace(" ", "") + " VF", fixUrl(serverUrl))
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                    val translated = translate(episode.toString(), serversvf.isNotEmpty())
                    val serversvo =  // Original version servers
                        soup.select("div#$translated > div.selink > ul.btnss $div> li")
                            .mapNotNull { li ->
                                val serverUrl = fixUrlNull(li.selectFirst("a")?.attr("href"))
                                if (!serverUrl.isNullOrEmpty()) {
                                    if (li.text().replace("&nbsp;", "").isNotBlank()) {
                                        Pair(
                                            li.text().replace(" ", "") + " VOSTFR",
                                            fixUrl(serverUrl)
                                        )
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }
                    serversvo + serversvf
                } else {
                    soup.select("nav#primary_nav_wrap > nav#primary_nav_wrap > ul > li > ul > li > a")
                        .mapNotNull { a ->
                            val serverurl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
                            val parent = a.parents()[2]
                            val element = parent.selectFirst("a")?.text().plus(" ")
                            if (a.text().replace("&nbsp;", "").isNotBlank()) {
                                Pair(element.plus(a.text()), fixUrl(serverurl))
                            } else {
                                null
                            }
                        }

                }

            servers.apmap {
                val urlplayer = it.second

                val playerUrl =
                    if (urlplayer.contains("opsktp.com") || urlplayer.contains("flixeo.xyz")) {
                        val header = app.get(
                            "https" + it.second.split("https")[1],
                            allowRedirects = false
                        ).headers
                        header["location"].toString()
                    } else {
                        urlplayer
                    }.replace("https://doodstream.com", "https://dood.yt")
                        .replace("https://doodstream.com", "https://dood.yt")
                loadExtractor(playerUrl, frenchStream, subtitleCallback) { video ->
                    callback.invoke(
                        ExtractorLink(
                            video.name,
                            "$invokedSourceName ${it.first}", // video.name
                            video.url,
                            video.referer,
                            video.quality,
                            video.isM3u8,
                            video.headers,
                            video.extractorData
                        )
                    )
                }
            }

        }
    }


    suspend fun invokeBlueSeries(
        frenchTitle: String? = null,
        wantedSeason: Int? = null,
        wantedEpisode: Int? = null,
        wantedYear: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val invokedSourceName = "BlueSeries"
        frenchTitle ?: throw ErrorLoadingException(" empty frenchTitle")
        wantedSeason ?: throw ErrorLoadingException(" invalid wantedSeason")
        wantedEpisode ?: throw ErrorLoadingException(" invalid wantedEpisode")
        val document = app.get(blueSeries).document
        val hashText = document.select("script").first {
            it.html().contains("var dle_login_hash =")
        }.html().take(300)
        val userHash = Regex("var dle_login_hash = '(.*?)';").find(hashText)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Empty user hash")

        val yearCategory = blueSeriesYears.firstOrNull { it.second == wantedYear.toString() }?.first ?: "0"
        val searchDoc = app.post(
            "$blueSeries/index.php?do=search", data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "1",
                "result_from" to "1",
                "story" to frenchTitle.replace(" ", "+"),
                "titleonly" to "3",
                "searchuser" to	"",
                "replyless" to "0",
                "replylimit" to "0",
                "searchdate" to "0",
                "beforeafter" to "after",
                "sortby" to "title",
                "resorder" to "desc",
                "showpost" to "0",
                "catlist[]" to yearCategory, // date   72 => 81, 71 => 1982
                "user_hash" to userHash,
            )
        ).document


        val url = searchDoc.selectFirst("article > a")?.attr("href")
            ?: throw ErrorLoadingException("invalid url !!")


        val serieMainPage = app.get(url).document
        val seasons = serieMainPage.select("div.seasontab > div > div.content1") //TODO >
        val seasonIndex = seasons.size - wantedSeason
        val episodes = seasons[seasonIndex].select("div.spoiler1 > div.spoiler1-body > a")
        val episodeIndex = episodes.size - wantedEpisode
        val episodeUrl = episodes[episodeIndex].attr("href")
            ?: throw ErrorLoadingException("invalid episode url")
        val episodeMainPage = app.get(episodeUrl).document
        val players = episodeMainPage.select("ul.player-list > li").mapNotNull {
            val playerText = it.select("div.lien").attr("onclick")
            val playerData = Regex("this, '(.*?)', '(.*?)'").find(playerText)
            val playerId = playerData?.groupValues?.get(1) ?: return@mapNotNull null
            val playerName = playerData?.groupValues?.get(2) ?: return@mapNotNull null
            Pair(playerId, playerName)
        }
        players.apmap { player ->
            val playerResponse = app.post(
                "$blueSeries/engine/ajax/Season.php", data = mapOf(
                    "id" to player.first,
                    "xfield" to player.second,
                    "action" to "playEpisode"
                ),
                referer = episodeUrl
            )

            val iframe = playerResponse.document.select("iframe").attr("src")

            loadExtractor(iframe, blueSeries, subtitleCallback) { video ->
                callback.invoke(
                    ExtractorLink(
                        video.name,
                        "$invokedSourceName ${player.second.replace("_", " ")}", // video.name
                        video.url,
                        video.referer,
                        video.quality,
                        video.isM3u8,
                        video.headers,
                        video.extractorData
                    )
                )
            }
        }
    }


    private fun Element.toWiflixSearchResponse(): SearchResponse {
        val type = select("div.nbloc3").text().lowercase()
        val title = select("a.nowrap").text()
        val link = select("a.nowrap").attr("href")
        if (type.contains("film")) {
            return newMovieSearchResponse(
                name = title,
                url = link,
                type = TvType.Movie,
            )
        } else  // an Serie
        {

            return newTvSeriesSearchResponse(
                name = title,
                url = link,
                type = TvType.TvSeries,

                )
        }
    }

    suspend fun invokeWiflix(
        frenchTitle: String? = null,
        wantedSeason: Int? = null,
        wantedEpisode: Int? = null,
        frenchSynopsis: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val invokedSourceName = "Wiflix"
        frenchTitle ?: throw ErrorLoadingException("empty frenchTitle")
        val fixedWantedEpisode = wantedEpisode.toString()
        val catList = if (wantedSeason == null) {
            "1" // movie
        } else {
            "31" // serie
        }
        val query = frenchTitle.replace(" ", "+")
        val document = app.post(
            "$wiflix/index.php", data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "story" to query,
                //"catlist[]" to catList,
            )
        ).document
        val results = document.select("div#dle-content > div.clearfix")
        val foundResult = // its a movie
        // title match
// if correct season, accept
            results.filter { divResult ->
                if (wantedSeason != null) {
                    val foundSeasonText = divResult.select("span.block-sai").text()

                    foundSeasonText.contains("Saison $wantedSeason ") // if correct season, accept
                } else {
                    true
                }
            }.maxByOrNull { divResult ->
                FuzzySearch.ratio(divResult.select("a.mov-t").text(), frenchTitle)
            }?.toWiflixSearchResponse()
                ?: throw ErrorLoadingException("COULDN'T FIND ANY RESULT") // no result here
        //println("found result: $foundResult")
        val loadDocument = app.get(foundResult.url).document

        val isMovie = foundResult.url.contains("/film-en-streaming/")
        if (frenchSynopsis != null && isMovie) // !loadDocument.select("div.screenshots-full").text().contains(frenchSynopsis)
            if (FuzzySearch.partialRatio(frenchSynopsis, loadDocument.select("div.screenshots-full").text()) < 80)
                throw ErrorLoadingException("INVALID MOVIE CHOSEN !!")

        val listOfPlayers = if (isMovie) {
            val listVf = loadDocument.select("div.linkstab > a").map { player ->
                Pair(
                    "https" + player.attr("href").replace("(.*)https".toRegex(), "")
                        .replace("doodstream.com", "dood.yt"),
                    "VF"
                )
            }
            val listVostFr = loadDocument.select("div.linkstab > div > a").map { player ->
                Pair(
                    "https" + player.attr("href").replace("(.*)https".toRegex(), "")
                        .replace("doodstream.com", "dood.yt"),
                    "VOSTFR"
                )
            }
            listVf + listVostFr
        } else {
            val listVf = loadDocument.select("div.ep${fixedWantedEpisode}vf > a").map { player ->
                Pair(
                    "https" + player.attr("href").replace("(.*)https".toRegex(), "")
                        .replace("doodstream.com", "dood.yt"),
                    "VF"
                )
            }
            val listVostFr =
                loadDocument.select("div.ep${fixedWantedEpisode}vs > a").map { player ->
                    Pair(
                        "https" + player.attr("href").replace("(.*)https".toRegex(), "")
                            .replace("doodstream.com", "dood.yt"),
                        "VOSTFR"
                    )
                }
            listVf + listVostFr
        }

        listOfPlayers.apmap { player ->
            try {
                loadExtractor(
                    httpsify(player.first),
                    player.first,
                    subtitleCallback
                ) { link ->
                    callback.invoke(
                        ExtractorLink( // ici je modifie le callback pour ajouter des informations, normalement ce n'est pas nécessaire
                            link.source,
                            "$invokedSourceName ${link.name} ${player.second}",
                            link.url,
                            link.referer,
                            link.quality,
                            link.isM3u8,
                            link.headers,
                            link.extractorData
                        )
                    )
                }
            } catch(e: Exception) {
                logError(e)
            }
        }
    }


    suspend fun invokeBlackInk(
        frenchTitle: String? = null,
        wantedSeason: Int? = null,
        wantedEpisode: Int? = null,
        tmdbId: Int?,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val invokedSourceName = "BlackInk"
        frenchTitle ?: throw ErrorLoadingException("empty frenchTitle blackink")
        year ?: throw ErrorLoadingException("empty year blackink")
        val isTvShow = wantedSeason != null && wantedEpisode != null
        val html =
            app.get(
                "$blackInkUrl/find?search=${frenchTitle.replace(" ", "+")}&type=posts&category="
            ).document

        val whiteList = listOf(
            2,
            15,
            32,
            75,
            76,
            77,
            78,
            79,
        )

        val foundUrl = html.select("div.videos > div.flex > div").firstOrNull { div ->
            val typeWhiteList: (Int) -> Boolean = { wantedCategory ->
                div.select("div.video-list-by").html().contains("?category=${wantedCategory}\"")
            }

            div.select("div.year-production").text().contains(year.toString()) // filter by year

                    && whiteList.any (typeWhiteList) // if wanted media category

                    && if(isTvShow) {
                div.select("a > h4").text().contains("Saison $wantedSeason") // false if wrong season
            } else {
                true
            }
        }?.select("div.video-list > div.video-list-image > a")?.attr("href")
            ?: throw ErrorLoadingException("Media not found on the website !")


        val document = app.get(foundUrl).document

        if (!document.select("ul.list-inline").html().contains("https://www.themoviedb.org/movie/$tmdbId")
            && !document.select("ul.list-inline").html().contains("https://www.themoviedb.org/tv/$tmdbId"))
        {
            throw ErrorLoadingException("Wrong media id found !!!") // WRONG EPISODE SELECTED
        }

        val regex = Regex(""""\d{1,3}":\{"id_lien":""")
        val jsonContentData = document.select("div[x-show=tab === 'uptobox'] > div")
            .attr("wire:initial-data")
            .replace("&quot;", "\"").replace("\\/", "/")
            .replace(",\"liens\":{", ",\"liens\":[")
            .replace(regex=regex, replacement="""{"id_lien":""")
            .replace("}},\"video\":", "}],\"video\":")
        val result = parseJson<DarkInkJson>(jsonContentData).serverMemo?.data?.liens
        val rows: List<uptoboxLink> = result?.filter {
            if(isTvShow) {
                it.episode == wantedEpisode && it.saison == wantedSeason && it.lien?.contains("uptobox.") ?: false// filter links for desired episode / season
            } else {
                it.lien?.contains("uptobox.") ?: false // only get uptobox links
            }

        }?.amap {
            val size = it.taille?.div((1000000000).toDouble())
            val rounded = String.format("%.1f", size)

            val languages: String? =
                it.multilang?.lang?.map { langCode -> // translate available languages id to text
                    darkInkLanguage.firstOrNull { availableLanguage ->
                        availableLanguage.first == langCode
                    }?.second.toString()
                }?.joinToString { " " }

                    ?: darkInkLanguage.firstOrNull { availableLanguage ->
                        availableLanguage.first == it.langue.toString()
                    }?.second
            val quality: String? = darkInkQualities.firstOrNull { availableQuality ->
                it.qualite.toString() == availableQuality.first
            }?.second

            it.lien?.let { iframeLink ->
                uptoboxLink(
                    iframeLink,
                    "$rounded Go $languages $quality",
                )
            }
        }?.filterNotNull() ?: listOf()
        rows.amap { uptoboxLink ->
            /*
            val response = app.get("https://api.alldebrid.com/v4/link/unlock?agent=$blackInkApiAppName&apikey=$blackInkApiKey&link=${uptoboxLink.iframeLink}").text
            val parsedResponse = parseJson<debridResponse>(response)
            parsedResponse.data?.subtitles?.map { subtitleTrack ->
                subtitleTrack.label?.let { label ->
                    subtitleTrack.src?.let { src ->
                        subtitleCallback.invoke(
                            SubtitleFile(
                                label,
                                src
                            )
                        )
                    }
                }
            }

            val id = parsedResponse.data?.id

            val directLinkQuery = app.get("https://api.alldebrid.com/v4/link/streaming?agent=$blackInkApiAppName&apikey=$blackInkApiKey&id=${id}&stream=hls").text
            val directHlsLink = parseJson<debridResponse>(directLinkQuery).data?.link ?: return@amap
*/
            val response = app.get("https://api.alldebrid.com/v4/link/unlock?agent=$blackInkApiAppName&apikey=$blackInkApiKey&link=${uptoboxLink.iframeLink}").text
            val parsedResponse = parseJson<debridResponse>(response)
            parsedResponse.data?.subtitles?.map { subtitleTrack ->
                subtitleTrack.label?.let { label ->
                    subtitleTrack.src?.let { src ->
                        subtitleCallback.invoke(
                            SubtitleFile(
                                label,
                                src
                            )
                        )
                    }
                }
            }
            val directDataLink = parsedResponse.data?.link ?: return@amap


            try {
                callback.invoke(
                    ExtractorLink(
                        invokedSourceName,
                        "$invokedSourceName ${uptoboxLink.mediaName}",
                        directDataLink,
                        "",// null
                        Qualities.Unknown.value,
                        directDataLink.endsWith(".m3u8") ?: false, // always true
                    )
                )
            } catch(e: Exception) {
                logError(e)
            }
        }
    }

    suspend fun invokeSmashyStream(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        isAnime: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$smashyStreamAPI/playere.php?imdb=$imdbId"
        } else {
            "$smashyStreamAPI/playere.php?imdb=$imdbId&season=$season&episode=$episode"
        }

        app.get(
            url,
            referer = "https://smashystream.com/"
        ).document.select("div#_default-servers a.server").map {
            it.attr("data-id") to it.text()
        }.apmap {
            when {
                it.first.contains("/fix") && !isAnime -> {
                    invokeSmashyFfix(it.second, it.first, url, subtitleCallback, callback)
                }
                it.first.contains("/gtop") -> {
                    invokeSmashyGtop(it.second, it.first, callback)
                }
                it.first.contains("/dude_tv") -> {
                    invokeSmashyDude(it.second, it.first, callback)
                }
                it.first.contains("/rip") -> {
                    invokeSmashyRip(it.second, it.first, subtitleCallback, callback)
                }
                else -> return@apmap
            }
        }

    }

    suspend fun invokeJellyfin(
        frenchTitle: String? = null,
        tmdbId: Int? = null,
        wantedSeason: Int? = null,
        wantedEpisode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var everyrequestheader = mapOf("Authorization" to "Emby", "UserId" to "c514fd773cba47d89ddce852dd54dd0d", "Content-type" to "application/json")
        var username = "Raph"
        var jellyfinName = "Jellyfin"
        var userId = "49fb12648a52436f8214d2a806b238d5"
        var jellyfinMainUrl = "http://192.168.1.18:8096"
        var apiKey = "878bc9992e86418ba414adf5e9866beb"
        var authheader = mapOf("X-Emby-Token" to apiKey, "Authorization" to apiKey)

        val response = app.get(
            "$jellyfinMainUrl/Users/$userId/Items?searchTerm=$frenchTitle&Limit=24&Fields=ProviderIds&Recursive=true&EnableTotalRecordCount=false&ImageTypeLimit=1&IncludePeople=false&IncludeMedia=true&IncludeGenres=false&IncludeStudios=false&IncludeArtists=false&IncludeItemTypes=Movie",
            headers=authheader
        ).text

        val result = tryParseJson<searchList>(response)?.items?.first { item ->
            item.ProviderIds.tmdbId == tmdbId.toString()
        }

        result?.MediaSources?.forEach {
            callback.invoke(
                ExtractorLink(
                    jellyfinName,
                    it.name + " " + it.Size,
                    "$jellyfinMainUrl/Items/$result/Download?api_key=$apiKey",
                    jellyfinMainUrl,
                    quality = Qualities.P2160.value
                )
            )
        }
        suspend fun getAuthHeader(username: String, password: String): Map<String, String?>? {
            val url = mainUrl

            val requestBody = mapOf("Raph" to username, "Pw" to password)
            val response = app.post("$url/Users/AuthenticateByName", requestBody = (requestBody + everyrequestheader).toString().toRequestBody("application/json;charset=UTF-8".toMediaTypeOrNull())).text
            //println("response $response ")
            val test = tryParseJson<authResponse>(response)
            return if (test != null) {
                mapOf("X-Emby-Token" to test.AccessToken)
            } else {
                null
            }
        }



    }

}



/* Smashy stream*/

fun processSubtitle(subtitles: String, languageCode: String, displayedSubtitleTrackName: String, subtitleCallback: (SubtitleFile) -> Unit): Boolean {

    val subtitleUrl = subtitles.split(",")
        .firstOrNull { it.contains("[$languageCode]", ignoreCase = true) }
        ?.removePrefix("[$languageCode]")
        ?.removeSuffix(",") ?: return false

    subtitleCallback.invoke(
        SubtitleFile(
            "$displayedSubtitleTrackName SmashyStream",
            subtitleUrl
        )
    )
    return true


}


suspend fun invokeSmashyOne( // taken from sora utils
    name: String,
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,

    ) {
    val script =
        app.get(url).document.selectFirst("script:containsData(player =)")?.data() ?: return

    val source =
        Regex("file:\\s['\"](\\S+?)['|\"]").find(script)?.groupValues?.get(
            1
        ) ?: return

    val subtitles =
        Regex("subtitle:\\s['\"](.+?)['|\"]").find(script)?.groupValues?.get(
            1
        ) ?: return

    val hasVfSub = processSubtitle(subtitles, "French", "Français", subtitleCallback)
    val hasVoSub = processSubtitle(subtitles, "English", "Anglais", subtitleCallback)

    if(hasVfSub || hasVoSub) {
        source.split(",").map { links ->
            val quality = Regex("\\[(\\d+)]").find(links)?.groupValues?.getOrNull(1)?.trim()
            val link = links.removePrefix("[$quality]").trim()
            callback.invoke(
                ExtractorLink(
                    "Smashy [$name]",
                    "Smashy VO [$name]",
                    link,
                    smashyStreamAPI,
                    quality?.toIntOrNull() ?: return@map,
                    isM3u8 = link.contains(".m3u8"),
                )
            )
        }
    }

}


suspend fun invokeSmashyFfix(
    name: String,
    url: String,
    ref: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    val script =
        app.get(url, referer = ref).document.selectFirst("script:containsData(player =)")?.data()
            ?: return

    //println(script)
    val source =
        Regex("['\"]?file['\"]?:\\s*\"([^\"]+)").find(script)?.groupValues?.get(
            1
        ) ?: return

    val subtitles =
        Regex("['\"]?subtitle['\"]?:\\s*\"([^\"]+)").find(script)?.groupValues?.get(
            1
        ) ?: return

    val hasVfSub = processSubtitle(subtitles, "French", "Français", subtitleCallback)
    val hasVoSub = processSubtitle(subtitles, "English", "Anglais", subtitleCallback)
    if(hasVfSub || hasVoSub) {
        source.split(",").map { links ->
            val quality = Regex("\\[(\\d+)]").find(links)?.groupValues?.getOrNull(1)?.trim()
            val link = links.removePrefix("[$quality]").trim()
            callback.invoke(
                ExtractorLink(
                    "Smashy [$name]",
                    "Smashy [$name]",
                    link,
                    smashyStreamAPI,
                    quality?.toIntOrNull() ?: return@map,
                    isM3u8 = link.contains(".m3u8"),
                )
            )
        }
    }

}

suspend fun invokeSmashyGtop(
    name: String,
    url: String,
    callback: (ExtractorLink) -> Unit
) {
    val doc = app.get(url).document
    val script = doc.selectFirst("script:containsData(var secret)")?.data() ?: return
    val secret =
        script.substringAfter("secret = \"").substringBefore("\";").let { base64Decode(it) }
    val key = script.substringAfter("token = \"").substringBefore("\";")
    val source = app.get(
        "$secret$key",
        headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest"
        )
    ).parsedSafe<Smashy1Source>() ?: return

    val videoUrl = base64Decode(source.file ?: return)
    if (videoUrl.contains("/bug")) return
    val quality =
        Regex("(\\d{3,4})[Pp]").find(videoUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P720.value
    callback.invoke(
        ExtractorLink(
            "Smashy [$name]",
            "Smashy [$name]",
            videoUrl,
            "",
            quality,
            videoUrl.contains(".m3u8")
        )
    )
}

suspend fun invokeSmashyDude(
    name: String,
    url: String,
    callback: (ExtractorLink) -> Unit
) {
    val script =
        app.get(url).document.selectFirst("script:containsData(player =)")?.data() ?: return

    val source = Regex("file:\\s*(\\[.*]),").find(script)?.groupValues?.get(1) ?: return

    tryParseJson<ArrayList<DudetvSources>>(source)?.filter { it.title == "English" }?.map {
        M3u8Helper.generateM3u8(
            "Smashy [Player 2]",
            it.file ?: return@map,
            ""
        ).forEach(callback)
    }

}

suspend fun invokeSmashyRip(
    name: String,
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    val script =
        app.get(url).document.selectFirst("script:containsData(player =)")?.data() ?: return

    val source = Regex("file:\\s*\"([^\"]+)").find(script)?.groupValues?.get(1)
    val subtitle = Regex("subtitle:\\s*\"([^\"]+)").find(script)?.groupValues?.get(1)

    source?.split(",")?.map { links ->
        val quality = Regex("\\[(\\d+)]").find(links)?.groupValues?.getOrNull(1)?.trim()
        val link = links.removePrefix("[$quality]").substringAfter("dev/").trim()
        if (link.isEmpty()) return@map
        callback.invoke(
            ExtractorLink(
                "Smashy [$name]",
                "Smashy [$name]",
                link,
                "",
                quality?.toIntOrNull() ?: return@map,
                isM3u8 = true,
            )
        )
    }

    subtitle?.replace("<br>", "")?.split(",")?.map { sub ->
        val lang = Regex("\\[(.*?)]").find(sub)?.groupValues?.getOrNull(1)?.trim()
        val link = sub.removePrefix("[$lang]")
        subtitleCallback.invoke(
            SubtitleFile(
                lang.orEmpty().ifEmpty { return@map },
                link
            )
        )
    }

}


/* End smashy stream */


data class authResponse (
    @JsonProperty("User") var User : User? = User(),
    @JsonProperty("AccessToken") var AccessToken : String? = null,
    @JsonProperty("ServerId") var ServerId : String? = null
)

data class User (
    @JsonProperty("Name") var Name : String?  = null,
    @JsonProperty("ServerId") var ServerId : String? = null,
    @JsonProperty("ServerName") var ServerName : String? = null,
    @JsonProperty("Id") var Id : String? = null,

    )
data class searchList (
    @JsonProperty("Items") val items: List<Item>,

    //@JsonProperty("TotalRecordCount",) val totalRecordCount: Long,

    //@JsonProperty("StartIndex") val startIndex: Long ? = null
)

data class Item (
    @JsonProperty("Name") val name: String,
    @JsonProperty("Id") val id: String,
    @JsonProperty("ProviderIds") val ProviderIds: ProviderIds,
    @JsonProperty("MediaSources") val MediaSources: List<MediaSource>,
)

data class MediaSource (
    @JsonProperty("Name") val name: String,
    @JsonProperty("Size") val Size: String,
)

data class ProviderIds(
    @JsonProperty("Tmdb") val tmdbId: String,
    @JsonProperty("Imdb") val imdbId: String,
)



data class FDMovieIFrame(
    val link: String,
    val quality: String,
    val size: String,
    val type: String,
)

data class BaymoviesConfig(
    val country: String,
    val downloadTime: String,
    val workers: List<String>
)

data class Movie123Media(
    @JsonProperty("url") val url: String? = null,
)

data class Movie123Data(
    @JsonProperty("t") val t: String? = null,
    @JsonProperty("s") val s: String? = null,
)

data class Movie123Search(
    @JsonProperty("data") val data: ArrayList<Movie123Data>? = arrayListOf(),
)

data class GomoviesSources(
    @JsonProperty("src") val src: String,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: Int? = null,
    @JsonProperty("max") val max: String,
    @JsonProperty("size") val size: String,
)

data class UHDBackupUrl(
    @JsonProperty("url") val url: String? = null,
)

data class MoviesbayValues(
    @JsonProperty("values") val values: List<List<String>>? = arrayListOf(),
)

data class HdMovieBoxTracks(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)

data class HdMovieBoxSource(
    @JsonProperty("videoUrl") val videoUrl: String? = null,
    @JsonProperty("videoServer") val videoServer: String? = null,
    @JsonProperty("videoDisk") val videoDisk: Any? = null,
    @JsonProperty("tracks") val tracks: ArrayList<HdMovieBoxTracks>? = arrayListOf(),
)

data class HdMovieBoxIframe(
    @JsonProperty("api_iframe") val apiIframe: String? = null,
)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("type") val type: String?,
)

data class SubtitlingList(
    @JsonProperty("languageAbbr") val languageAbbr: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("subtitlingUrl") val subtitlingUrl: String? = null,
)

data class DefinitionList(
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("description") val description: String? = null,
)

data class EpisodeVo(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("seriesNo") val seriesNo: Int? = null,
    @JsonProperty("definitionList") val definitionList: ArrayList<DefinitionList>? = arrayListOf(),
    @JsonProperty("subtitlingList") val subtitlingList: ArrayList<SubtitlingList>? = arrayListOf(),
)

data class MediaDetail(
    @JsonProperty("episodeVo") val episodeVo: ArrayList<EpisodeVo>? = arrayListOf(),
)

data class Load(
    @JsonProperty("data") val data: MediaDetail? = null,
)

data class ConsumetHeaders(
    @JsonProperty("Referer") val referer: String? = null,
)

data class ConsumetSubtitles(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("lang") val lang: String? = null,
)

data class ConsumetSources(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("isM3U8") val isM3U8: Boolean? = null,
)

data class ConsumetSourcesResponse(
    @JsonProperty("headers") val headers: ConsumetHeaders? = null,
    @JsonProperty("sources") val sources: ArrayList<ConsumetSources>? = arrayListOf(),
    @JsonProperty("subtitles") val subtitles: ArrayList<ConsumetSubtitles>? = arrayListOf(),
)

data class ConsumetEpisodes(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("number") val number: Int? = null,
    @JsonProperty("season") val season: Int? = null,
)

data class ConsumetTitle(
    @JsonProperty("romaji") val romaji: String? = null,
    @JsonProperty("english") val english: String? = null
)

data class ConsumetDetails(
    @JsonProperty("episodes") val episodes: ArrayList<ConsumetEpisodes>? = arrayListOf(),
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("cover") val cover: String? = null,
    @JsonProperty("title") val title: ConsumetTitle? = null,
    @JsonProperty("releaseDate") val releaseDate: Int? = null
)

data class CrunchyrollEpisodes(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("episode_number") val episode_number: Int? = null,
    @JsonProperty("season_number") val season_number: Int? = null,
)

data class CrunchyrollDetails(
    @JsonProperty("episodes") val episodes: HashMap<String, List<CrunchyrollEpisodes>>? = hashMapOf(),
)

data class ConsumetResults(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("seasons") val seasons: Int? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class ConsumetSearchResponse(
    @JsonProperty("results") val results: ArrayList<ConsumetResults>? = arrayListOf(),
)

data class KisskhSources(
    @JsonProperty("Video") val video: String?,
    @JsonProperty("ThirdParty") val thirdParty: String?,
)

data class KisskhSubtitle(
    @JsonProperty("src") val src: String?,
    @JsonProperty("label") val label: String?,
)

data class KisskhEpisodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Int?,
)

data class KisskhDetail(
    @JsonProperty("episodes") val episodes: ArrayList<KisskhEpisodes>? = arrayListOf(),
)

data class KisskhResults(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

data class EpisodesFwatayako(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("download") val download: HashMap<String, String>? = hashMapOf(),
)

data class SeasonFwatayako(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("folder") val folder: ArrayList<EpisodesFwatayako>? = arrayListOf(),
)

data class SourcesFwatayako(
    @JsonProperty("movie") val sourcesMovie: String? = null,
    @JsonProperty("tv") val sourcesTv: ArrayList<SeasonFwatayako>? = arrayListOf(),
    @JsonProperty("movie_dl") val movie_dl: HashMap<String, String>? = hashMapOf(),
    @JsonProperty("tv_dl") val tv_dl: ArrayList<SeasonFwatayako>? = arrayListOf(),
)

data class DriveBotLink(
    @JsonProperty("url") val url: String? = null,
)

data class DirectDl(
    @JsonProperty("download_url") val download_url: String? = null,
)

data class Safelink(
    @JsonProperty("safelink") val safelink: String? = null,
)

data class FDAds(
    @JsonProperty("linkr") val linkr: String? = null,
)

data class DataMal(
    @JsonProperty("mal_id") val mal_id: String? = null,
)

data class JikanResponse(
    @JsonProperty("data") val data: ArrayList<DataMal>? = arrayListOf(),
)

data class IdAni(
    @JsonProperty("id") val id: String? = null,
)

data class MediaAni(
    @JsonProperty("Media") val media: IdAni? = null,
)

data class DataAni(
    @JsonProperty("data") val data: MediaAni? = null,
)

data class Smashy1Tracks(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
)

data class Smashy1Source(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("tracks") val tracks: ArrayList<Smashy1Tracks>? = arrayListOf(),
)

data class WatchsomuchTorrents(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("movieId") val movieId: Int? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
)

data class WatchsomuchMovies(
    @JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf(),
)

data class WatchsomuchResponses(
    @JsonProperty("movie") val movie: WatchsomuchMovies? = null,
)

data class WatchsomuchSubtitles(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("label") val label: String? = null,
)

data class WatchsomuchSubResponses(
    @JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf(),
)

data class IndexMedia(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("driveId") val driveId: String? = null,
    @JsonProperty("mimeType") val mimeType: String? = null,
    @JsonProperty("size") val size: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("modifiedTime") val modifiedTime: String? = null,
)

data class IndexData(
    @JsonProperty("files") val files: ArrayList<IndexMedia>? = arrayListOf(),
)

data class IndexSearch(
    @JsonProperty("data") val data: IndexData? = null,
)

data class TgarMedia(
    @JsonProperty("_id") val _id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("size") val size: Double? = null,
    @JsonProperty("file_unique_id") val file_unique_id: String? = null,
    @JsonProperty("mime_type") val mime_type: String? = null,
)

data class TgarData(
    @JsonProperty("documents") val documents: ArrayList<TgarMedia>? = arrayListOf(),
)

data class SorastreamResponse(
    @JsonProperty("data") val data: SorastreamVideos? = null,
)

data class SorastreamVideos(
    @JsonProperty("mediaUrl") val mediaUrl: String? = null,
    @JsonProperty("currentDefinition") val currentDefinition: String? = null,
)

data class SapphireSubtitles(
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class SapphireStreams(
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("audio_lang") val audio_lang: String? = null,
    @JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class SapphireSources(
    @JsonProperty("streams") val streams: ArrayList<SapphireStreams>? = arrayListOf(),
    @JsonProperty("subtitles") val subtitles: ArrayList<SapphireSubtitles>? = arrayListOf(),
)

data class BiliBiliEpisodes(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("sourceId") val sourceId: String? = null,
    @JsonProperty("sourceEpisodeId") val sourceEpisodeId: String? = null,
    @JsonProperty("sourceMediaId") val sourceMediaId: String? = null,
    @JsonProperty("episodeNumber") val episodeNumber: Int? = null,
)

data class BiliBiliDetails(
    @JsonProperty("episodes") val episodes: ArrayList<BiliBiliEpisodes>? = arrayListOf(),
)

data class BiliBiliSubtitles(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("language") val language: String? = null,
)

data class BiliBiliSources(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class BiliBiliSourcesResponse(
    @JsonProperty("sources") val sources: ArrayList<BiliBiliSources>? = arrayListOf(),
    @JsonProperty("subtitles") val subtitles: ArrayList<BiliBiliSubtitles>? = arrayListOf(),
)

data class WatchOnlineItems(
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("tmdb_id") val tmdb_id: Int? = null,
    @JsonProperty("imdb_id") val imdb_id: String? = null,
)

data class WatchOnlineSearch(
    @JsonProperty("items") val items: ArrayList<WatchOnlineItems>? = arrayListOf(),
)

data class WatchOnlineResponse(
    @JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @JsonProperty("subtitles") val subtitles: Any? = null,
)

data class PutlockerEpisodes(
    @JsonProperty("html") val html: String? = null,
)

data class PutlockerEmbed(
    @JsonProperty("src") val src: String? = null,
)

data class PutlockerSources(
    @JsonProperty("file") val file: String,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class PutlockerResponses(
    @JsonProperty("sources") val sources: ArrayList<PutlockerSources>? = arrayListOf(),
    @JsonProperty("backupLink") val backupLink: String? = null,
)

data class AllanimeStreams(
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("audio_lang") val audio_lang: String? = null,
    @JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
)

data class AllanimePortData(
    @JsonProperty("streams") val streams: ArrayList<AllanimeStreams>? = arrayListOf(),
)

data class AllanimeLink(
    @JsonProperty("portData") val portData: AllanimePortData? = null,
    @JsonProperty("resolutionStr") val resolutionStr: String? = null,
    @JsonProperty("src") val src: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("hls") val hls: Boolean? = null,
)

data class AllanimeLinks(
    @JsonProperty("links") val links: ArrayList<AllanimeLink>? = arrayListOf(),
)

data class AllanimeSourceUrls(
    @JsonProperty("sourceUrl") val sourceUrl: String? = null,
    @JsonProperty("sourceName") val sourceName: String? = null,
)

data class AllanimeEpisode(
    @JsonProperty("sourceUrls") val sourceUrls: ArrayList<AllanimeSourceUrls>? = arrayListOf(),
)

data class AllanimeAvailableEpisodesDetail(
    @JsonProperty("sub") val sub: ArrayList<String>? = arrayListOf(),
    @JsonProperty("dub") val dub: ArrayList<String>? = arrayListOf(),
)

data class AllanimeDetailShow(
    @JsonProperty("availableEpisodesDetail") val availableEpisodesDetail: AllanimeAvailableEpisodesDetail? = null,
)

data class AllanimeAiredStart(
    @JsonProperty("year") val year: Int? = null,
)

data class AllanimeEdges(
    @JsonProperty("_id") val _id: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("englishName") val englishName: String? = null,
    @JsonProperty("thumbnail") val thumbnail: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("airedStart") val airedStart: AllanimeAiredStart? = null,
)

data class AllanimeShows(
    @JsonProperty("edges") val edges: ArrayList<AllanimeEdges>? = arrayListOf(),
)

data class AllanimeData(
    @JsonProperty("shows") val shows: AllanimeShows? = null,
    @JsonProperty("show") val show: AllanimeDetailShow? = null,
    @JsonProperty("episode") val episode: AllanimeEpisode? = null,
)

data class AllanimeResponses(
    @JsonProperty("data") val data: AllanimeData? = null,
)

data class ShivamhwSources(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("stream_link") val stream_link: String? = null,
    @JsonProperty("process_link") val process_link: String? = null,
    @JsonProperty("name") val name: String,
    @JsonProperty("size") val size: String,
)

data class CryMoviesProxyHeaders(
    @JsonProperty("request") val request: Map<String, String>?,
)

data class CryMoviesBehaviorHints(
    @JsonProperty("proxyHeaders") val proxyHeaders: CryMoviesProxyHeaders?,
)

data class CryMoviesStream(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("behaviorHints") val behaviorHints: CryMoviesBehaviorHints? = null,
)

data class CryMoviesResponse(
    @JsonProperty("streams") val streams: List<CryMoviesStream>? = null,
)

data class DudetvSources(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("title") val title: String? = null,
)

data class FmoviesResponses(
    @JsonProperty("html") val html: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class FmoviesSubtitles(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)

data class VizcloudSources(
    @JsonProperty("file") val file: String? = null,
)

data class VizcloudMedia(
    @JsonProperty("sources") val sources: ArrayList<VizcloudSources>? = arrayListOf(),
)

data class VizcloudData(
    @JsonProperty("media") val media: VizcloudMedia? = null,
)

data class VizcloudResponses(
    @JsonProperty("data") val data: VizcloudData? = null,
)