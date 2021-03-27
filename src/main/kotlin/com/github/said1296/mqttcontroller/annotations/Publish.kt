package com.github.said1296.mqttcontroller.annotations

import com.github.said1296.mqttcontroller.constants.PayloadManagingStrategy

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Publish(val relativeTopic: String, val payloadManagement: PayloadManagingStrategy = PayloadManagingStrategy.SIMPLE)
