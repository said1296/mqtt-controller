package com.lacomuna.mqttcontroller.annotations

import com.lacomuna.mqttcontroller.constants.PayloadManagingStrategy

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Publish(val relativeTopic: String, val payloadManagement: PayloadManagingStrategy = PayloadManagingStrategy.SIMPLE)
