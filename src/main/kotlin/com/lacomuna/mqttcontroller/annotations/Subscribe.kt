package com.lacomuna.mqttcontroller.annotations

import com.lacomuna.mqttcontroller.constants.MatchType
import com.lacomuna.mqttcontroller.constants.PayloadManagingStrategy

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe(val relativeTopic: String, val payloadManagement: PayloadManagingStrategy = PayloadManagingStrategy.SIMPLE, val matchType: MatchType = MatchType.MULTI_LEVEL)
