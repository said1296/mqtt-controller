package com.github.said1296.mqttcontroller.annotations

import com.github.said1296.mqttcontroller.constants.MatchType
import com.github.said1296.mqttcontroller.constants.PayloadManagingStrategy

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe(val relativeTopic: String, val payloadManagement: PayloadManagingStrategy = PayloadManagingStrategy.SIMPLE, val matchType: MatchType = MatchType.MULTI_LEVEL)
