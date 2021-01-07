package com.lacomuna.mqttcontroller.models

import com.lacomuna.mqttcontroller.constants.MatchType

/**
 * Holds the values inputted by the User with the [Subscribe] annotation
 */

data class SubtopicUserInput(
        val subtopic: String,
        val callback: (payloadAsByteArray: ByteArray, topicParameters: Map<String, String>) -> Unit,
        val matchType: MatchType = MatchType.NONE
)
