package com.vknxworks.talkctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object UnitRoute
@Serializable data object AudioRoute
@Serializable data object RadioRoute
@Serializable data object IntercomRoute

// Opened from the Unit tab
@Serializable data object UpdateRoute
@Serializable data object RidingRoute

// Opened from the profile picture: the You page, and Settings from there
@Serializable data object YouRoute
@Serializable data object SettingsRoute
