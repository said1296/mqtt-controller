package com.github.said1296.mqttcontroller.models

data class TopicMatchResult(
        var match: Boolean = true,
        var topicParameters: MutableMap<String, String> = mutableMapOf()
)
