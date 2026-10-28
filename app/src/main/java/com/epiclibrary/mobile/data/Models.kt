package com.epiclibrary.mobile.data

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
    val appName: String = ""
)

data class GameCollection(
    val id: String,
    val name: String,
    val gameIds: Set<String> = emptySet()
)
