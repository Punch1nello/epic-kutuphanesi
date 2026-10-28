package com.epiclibrary.mobile.data

enum class LibraryItemKind { GAME, TOOL }

data class Game(
    val id: String,
    val title: String,
    val genre: String,
    val imageUrl: String,
    val addedLabel: String = "Kütüphanende",
    val developer: String = "",
    val publisher: String = "",
    val description: String = "",
    val namespace: String = "",
    val catalogItemId: String = "",
    val appName: String = "",
    val kind: LibraryItemKind = LibraryItemKind.GAME,
    val playtimeMinutes: Long? = null,
    val playerModes: Set<String> = emptySet()
)

data class GameCollection(
    val id: String,
    val name: String,
    val gameIds: Set<String> = emptySet()
)

