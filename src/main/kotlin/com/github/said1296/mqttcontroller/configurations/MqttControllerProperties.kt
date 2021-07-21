package com.github.said1296.mqttcontroller.configurations

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "mqtt")
data class MqttControllerProperties(
        var broker: String = "",
        var port: String = "",
        var clientId: String = "",
        var keepAliveInterval: Int = 60
)
