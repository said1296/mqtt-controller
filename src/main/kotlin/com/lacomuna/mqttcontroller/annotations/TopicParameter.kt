package com.lacomuna.mqttcontroller.annotations

/**
 * Annotation for receiving a parameter contained in a topic.
 * For example: /sensor/{idSensor} will map  whatever comes as idSensor to a parameter
 * annotated as: @TopicParameter idSensor.
 *
 * Type of parameter must be a primitive.
 *
 * If the value received can't be casted to the type of the parameter, an exception will be thrown and the method won't
 * be executed.
  */

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class TopicParameter()
