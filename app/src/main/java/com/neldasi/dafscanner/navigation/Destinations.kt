package com.neldasi.dafscanner.navigation

import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
data class CameraRoute(
    val isVerifyMode: Boolean = false
)

@Serializable
object SettingsRoute

@Serializable
object SearchListRoute

@Serializable
object ConverterRoute

@Serializable
data class DetailRoute(
    val fullCode: String,
    val timestamp: Long,
)
