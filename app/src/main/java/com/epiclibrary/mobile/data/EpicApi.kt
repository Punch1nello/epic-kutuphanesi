package com.epiclibrary.mobile.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

interface EpicApi {
    suspend fun loadLibrary(accessToken: String, localeTag: String): List<Game>
    suspend fun loadCollections(accessToken: String, accountId: String): List<GameCollection>
}

class RealEpicApi(
    private val client: OkHttpClient = OkHttpClient()
) : EpicApi {
    companion object {
        private const val LIBRARY_URL =
            "https://library-service.live.use1a.on.epicgames.com/library/api/public/items"
        private const val CATALOG_HOST =
            "https://catalog-public-service-prod06.ol.epicgames.com"
        private val GRAPHQL_URLS = listOf(
            "https://launcher.store.epicgames.com/graphql",
            "https://store.epicgames.com/graphql",
            "https://graphql.epicgames.com/graphql"
        )
        private const val USER_AGENT = "EpicLibraryMobile/0.5"
        private const val COUNTRY = "TR"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val GET_COLLECTIONS_QUERY = """
            query getCollections(${'$'}accountId: String!) {
              Library {
                collection {
                  collections(accountId: ${'$'}accountId) {
                    name
                    collectionId
                    libraryItemContentVersion
                  }
                }
              }
            }
        """

        private const val GET_COLLECTION_ITEMS_QUERY = """
            query getCollectionItems(${'$'}accountId: String!, ${'$'}collectionId: String!, ${'$'}cursor: String) {
              Library {
                collection {
                  items(accountId: ${'$'}accountId, collectionId: ${'$'}collectionId, cursor: ${'$'}cursor) {
                    records {
                      relationshipId
                      artifactId
                      catalogId
                      sandbox
                    }
                    responseMetadata {
                      nextCursor
                    }
                  }
                }
              }
            }
        """
    }

    override suspend fun loadLibrary(accessToken: String, localeTag: String): List<Game> = coroutineScope {
        val records = mutableListOf<JSONObject>()
        var cursor: String? = null

        do {
            val page = requestLibraryPage(accessToken, cursor)
            val pageRecords = page.optJSONArray("records") ?: JSONArray()
            for (i in 0 until pageRecords.length()) {
                val record = pageRecords.optJSONObject(i) ?: continue
                if (shouldKeep(record)) records += record
            }
            val next = page.optJSONObject("responseMetadata")?.optString("nextCursor")
                ?.takeIf { it.isNotBlank() && it != "null" }
            if (next == cursor) break
            cursor = next
        } while (cursor != null)

        records.chunked(6).flatMap { batch ->
            batch.map { record ->
                async {
                    runCatching { catalogToGame(accessToken, record, localeTag) }
                        .getOrElse { recordToFallbackGame(record) }
                }
            }.awaitAll()
        }.distinctBy { game ->
            game.id.ifBlank { "${game.namespace}:${game.catalogItemId}:${game.appName}:${game.title}" }
        }
    }

    /**
     * Epic's web store exposes a GraphQL Library surface for account collections.
     * Collections are best-effort here: if the surface changes or is unavailable,
     * the main library still loads normally.
     */
    override suspend fun loadCollections(accessToken: String, accountId: String): List<GameCollection> {
        return runCatching {
            val collections = requestGraphQl(accessToken, GET_COLLECTIONS_QUERY, mapOf("accountId" to accountId))
                .optJSONObject("data")
                ?.optJSONObject("Library")
                ?.optJSONObject("collection")
                ?.optJSONArray("collections")
                ?: JSONArray()

            val result = mutableListOf<GameCollection>()
            for (i in 0 until collections.length()) {
                val item = collections.optJSONObject(i) ?: continue
                val id = item.optString("collectionId")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                val gameIds = loadCollectionGameIds(accessToken, accountId, id)
                result += GameCollection(id = id, name = name, gameIds = gameIds)
            }
            result.distinctBy { it.id }
        }.getOrElse { emptyList() }
    }

    private fun loadCollectionGameIds(
        accessToken: String,
        accountId: String,
        collectionId: String
    ): Set<String> {
        val ids = linkedSetOf<String>()
        var cursor: String? = null

        do {
            val response = requestGraphQl(
                accessToken,
                GET_COLLECTION_ITEMS_QUERY,
                mapOf(
                    "accountId" to accountId,
                    "collectionId" to collectionId,
                    "cursor" to cursor
                )
            )
            val items = response.optJSONObject("data")
                ?.optJSONObject("Library")
                ?.optJSONObject("collection")
                ?.optJSONObject("items")
            val records = items?.optJSONArray("records") ?: JSONArray()
            for (i in 0 until records.length()) {
                val record = records.optJSONObject(i) ?: continue
                val sandbox = record.optString("sandbox")
                val catalogId = record.optString("catalogId")
                val artifactId = record.optString("artifactId")
                if (sandbox.isNotBlank() && catalogId.isNotBlank()) ids += "$sandbox:$catalogId"
                if (catalogId.isNotBlank()) ids += catalogId
                if (artifactId.isNotBlank()) ids += artifactId
                val relationshipId = record.optString("relationshipId")
                if (relationshipId.isNotBlank()) ids += relationshipId
            }
            val next = items?.optJSONObject("responseMetadata")?.optString("nextCursor")
                ?.takeIf { it.isNotBlank() && it != "null" }
            if (next == cursor) break
            cursor = next
        } while (cursor != null)

        return ids
    }

    private fun requestGraphQl(
        accessToken: String,
        query: String,
        variables: Map<String, String?>
    ): JSONObject {
        val variablesObject = JSONObject()
        variables.forEach { (key, value) ->
            if (value == null) variablesObject.put(key, JSONObject.NULL)
            else variablesObject.put(key, value)
        }
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variablesObject)
            .toString()

        var lastError: Throwable? = null
        for (graphqlUrl in GRAPHQL_URLS) {
            try {
                val request = Request.Builder()
                    .url(graphqlUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://store.epicgames.com")
                    .header("Referer", "https://store.epicgames.com/")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = IOException("Epic GraphQL request failed (${response.code}) at $graphqlUrl")
                    continue
                }
                val json = JSONObject(body)
                if (json.has("errors")) {
                    val errors = json.optJSONArray("errors")
                    val message = errors?.optJSONObject(0)?.optString("message").orEmpty()
                    lastError = IOException(message.ifBlank { "Epic collections request failed" })
                    continue
                }
                return json
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IOException("Epic collections service is unavailable")
    }

    private fun shouldKeep(record: JSONObject): Boolean {
        val namespace = record.optString("namespace")
        if (namespace == "89efe5924d3d467c839449ab6ab52e7f") return false
        if (record.optString("appName") == "1") return false
        val platform = record.optJSONArray("platform")
        if (platform != null && platform.length() > 0) {
            var windows = false
            for (i in 0 until platform.length()) {
                val value = platform.optString(i)
                if (value.equals("Windows", true) || value.equals("Win32", true)) {
                    windows = true
                    break
                }
            }
            if (!windows) return false
        }
        return record.optString("appName").isNotBlank() && namespace.isNotBlank()
    }

    private fun requestLibraryPage(accessToken: String, cursor: String?): JSONObject {
        val url = LIBRARY_URL.toHttpUrl().newBuilder()
            .addQueryParameter("includeMetadata", "true")
            .apply { cursor?.let { addQueryParameter("cursor", it) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("Library request failed (${response.code}): $body")
        return JSONObject(body)
    }

    private fun catalogToGame(accessToken: String, record: JSONObject, localeTag: String): Game {
        val namespace = record.getString("namespace")
        val catalogId = record.getString("catalogItemId")
        val appName = record.getString("appName")
        val url = "$CATALOG_HOST/catalog/api/shared/namespace/$namespace/bulk/items"
            .toHttpUrl().newBuilder()
            .addQueryParameter("id", catalogId)
            .addQueryParameter("includeDLCDetails", "true")
            .addQueryParameter("includeMainGameDetails", "true")
            .addQueryParameter("country", COUNTRY)
            .addQueryParameter("locale", localeTag)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("Catalog request failed (${response.code})")
        val item = JSONObject(body).optJSONObject(catalogId) ?: throw IOException("Catalog item missing")

        val image = pickImage(item.optJSONArray("keyImages"))
        val categories = item.optJSONArray("categories")
        val genre = pickGenre(categories)
        return Game(
            id = "$namespace:$catalogId",
            title = item.optString("title").ifBlank { appName },
            genre = genre,
            imageUrl = image,
            addedLabel = "Epic kütüphanende",
            developer = item.optString("developerDisplayName"),
            publisher = item.optString("publisherDisplayName"),
            description = item.optString("description"),
            namespace = namespace,
            catalogItemId = catalogId,
            appName = appName,
            kind = classifyItem(record, item),
            playtimeMinutes = readPlaytimeMinutes(record),
            playerModes = detectPlayerModes(categories)
        )
    }

    private fun recordToFallbackGame(record: JSONObject): Game = Game(
        id = "${record.optString("namespace")}:${record.optString("catalogItemId")}",
        title = record.optString("appName"),
        genre = "other",
        imageUrl = "",
        addedLabel = "Epic kütüphanende",
        namespace = record.optString("namespace"),
        catalogItemId = record.optString("catalogItemId"),
        appName = record.optString("appName"),
        kind = classifyItem(record, null),
        playtimeMinutes = readPlaytimeMinutes(record),
        playerModes = detectPlayerModes(record.optJSONArray("categories"))
    )

    private fun classifyItem(record: JSONObject, catalog: JSONObject?): LibraryItemKind {
        // Catalog categories and generic "application" metadata also occur on games.
        // Only recognize known Epic creative/developer products as tools; default to game.
        if (record.optString("namespace").equals("ue", ignoreCase = true)) return LibraryItemKind.TOOL

        fun normalize(value: String): String = value
            .lowercase()
            .replace('&', ' ')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        val names = listOf(
            record.optString("appName"),
            record.optString("title"),
            catalog?.optString("title").orEmpty(),
            catalog?.optString("developerDisplayName").orEmpty()
        ).map(::normalize)
        val knownToolNames = setOf(
            "unreal engine", "unreal editor", "unreal editor for fortnite", "uefn",
            "twinmotion", "realityscan", "reality scan", "realitycapture", "reality capture",
            "metahuman creator", "quixel bridge", "quixel mixer"
        )
        val knownToolPattern = Regex("^(unreal engine|twinmotion)( [0-9]+( [0-9]+)*)?$")
        val isKnownTool = names.any { it in knownToolNames || knownToolPattern.matches(it) }
        return if (isKnownTool) LibraryItemKind.TOOL else LibraryItemKind.GAME
    }

    /** Playtime is optional: Epic's public library response may not include it. */
    private fun readPlaytimeMinutes(record: JSONObject): Long? {
        val sources = buildList {
            add(record)
            record.optJSONObject("metadata")?.let(::add)
            record.optJSONObject("playtime")?.let(::add)
            record.optJSONObject("stats")?.let(::add)
        }
        val minuteKeys = listOf("playtimeMinutes", "playTimeMinutes", "minutesPlayed", "totalPlaytimeMinutes")
        for (source in sources) {
            minuteKeys.forEach { key ->
                if (source.has(key)) source.optLong(key, -1L).takeIf { it >= 0L }?.let { return it }
            }
            listOf("playtimeSeconds", "playTimeSeconds", "secondsPlayed", "totalPlaytimeSeconds").forEach { key ->
                if (source.has(key)) source.optLong(key, -1L).takeIf { it >= 0L }?.let { return it / 60L }
            }
            source.optJSONObject("playtime")?.let { playtime ->
                playtime.optLong("minutes", -1L).takeIf { it >= 0L }?.let { return it }
                playtime.optLong("seconds", -1L).takeIf { it >= 0L }?.let { return it / 60L }
            }
        }
        return null
    }

    private fun detectPlayerModes(categories: JSONArray?): Set<String> {
        if (categories == null) return emptySet()
        val values = buildList {
            for (i in 0 until categories.length()) {
                categories.optJSONObject(i)?.optString("path")?.lowercase()?.let(::add)
            }
        }.joinToString(" ").replace('_', ' ').replace('-', ' ')
        return buildSet {
            if (listOf("single player", "singleplayer", "single player", "single-player").any(values::contains)) {
                add("single")
            }
            if (listOf("multiplayer", "multi player", "co op", "co-op", "online pvp", "local pvp").any(values::contains)) {
                add("multi")
            }
        }
    }

    private fun pickImage(images: JSONArray?): String {
        if (images == null) return ""
        val priority = listOf("DieselGameBoxTall", "DieselGameBox", "DieselStoreFrontWide", "Thumbnail")
        for (type in priority) {
            for (i in 0 until images.length()) {
                val image = images.optJSONObject(i) ?: continue
                if (image.optString("type") == type) return image.optString("url")
            }
        }
        for (i in 0 until images.length()) {
            val url = images.optJSONObject(i)?.optString("url").orEmpty()
            if (url.isNotBlank()) return url
        }
        return ""
    }

    private fun pickGenre(categories: JSONArray?): String {
        if (categories == null || categories.length() == 0) return "other"
        val values = buildList {
            for (i in 0 until categories.length()) {
                val path = categories.optJSONObject(i)?.optString("path").orEmpty()
                if (path.isBlank()) continue
                add(path.substringAfterLast('/').substringAfterLast('>').trim())
            }
        }.filter { it.isNotBlank() }

        // Choose one stable primary genre, independent of the API's category order.
        val priority = listOf(
            "rpg", "strategy", "simulation", "sports", "racing", "fps", "tps", "shooter",
            "action_adventure", "action", "adventure", "horror", "survival", "fighting",
            "platformer", "puzzle", "open_world", "city_builder", "tower_defense", "stealth",
            "card", "mmorpg", "mmo", "metroidvania", "roguelike", "roguelite", "indie",
            "casual", "family", "free_to_play", "sandbox", "soulslike"
        )
        return values.map(::canonicalGenre)
            .filter { it != "other" }
            .minByOrNull { priority.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }
            ?: "other"
    }

    private fun normalizeGenre(value: String): String = value
        .trim()
        .lowercase()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")

    private fun canonicalGenre(value: String): String {
        val normalized = normalizeGenre(value)
        return when (normalized) {
            "action", "actions" -> "action"
            "action adventure", "action adventure games" -> "action_adventure"
            "adventure", "adventures" -> "adventure"
            "rpg", "role playing", "role playing game", "role playing games", "jrpg", "crpg" -> "rpg"
            "strategy", "strategies", "real time strategy", "turn based strategy" -> "strategy"
            "simulation", "simulations", "simulator", "simulators" -> "simulation"
            "sports", "sport" -> "sports"
            "racing", "race" -> "racing"
            "shooter", "shooters" -> "shooter"
            "first person shooter", "first person shooters", "fps" -> "fps"
            "third person shooter", "third person shooters", "tps" -> "tps"
            "platformer", "platform", "platforming" -> "platformer"
            "puzzle", "puzzles" -> "puzzle"
            "horror" -> "horror"
            "survival" -> "survival"
            "indie", "independent" -> "indie"
            "open world" -> "open_world"
            "fighting", "fighter" -> "fighting"
            "family" -> "family"
            "casual" -> "casual"
            "card game", "card games", "cards" -> "card"
            "mmo" -> "mmo"
            "mmorpg" -> "mmorpg"
            "free to play", "free to play games" -> "free_to_play"
            "metroidvania" -> "metroidvania"
            "roguelike", "rogue like" -> "roguelike"
            "roguelite", "rogue lite" -> "roguelite"
            "turn based" -> "turn_based"
            "tower defense", "tower defence" -> "tower_defense"
            "city builder", "city building" -> "city_builder"
            "sandbox" -> "sandbox"
            "stealth", "sneaking" -> "stealth"
            "soulslike", "souls like" -> "soulslike"
            else -> "other"
        }
    }

}

