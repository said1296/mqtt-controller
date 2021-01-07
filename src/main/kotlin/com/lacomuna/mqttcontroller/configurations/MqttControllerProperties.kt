package com.lacomuna.mqttcontroller.configurations

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "mqtt")
data class MqttControllerProperties(
        var broker: String = "",
        var port: String = ""
)
