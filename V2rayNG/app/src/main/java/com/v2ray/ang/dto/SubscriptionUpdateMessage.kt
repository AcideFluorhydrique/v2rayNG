package com.v2ray.ang.dto

import java.io.Serializable

data class SubscriptionUpdateMessage(
    val key: Int,
    val forcedUpdate: Boolean,
    val subIds: List<String> = listOf(),
    // Started by the user rather than by the schedule: may go direct when not connected
    // (FetchRoutePolicy). False for anything that does not say so.
    val interactive: Boolean = false
) : Serializable
