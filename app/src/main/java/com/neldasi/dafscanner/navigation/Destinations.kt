package com.neldasi.dafscanner.navigation

import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
object CameraRoute

@Serializable
object SettingsRoute

@Serializable
object SearchListRoute

@Serializable
data class DetailRoute(
    val fullCode: String,
    val timestamp: Long,
)
