package com.github.said1296.mqttcontroller.controller


import com.google.gson.Gson
import com.github.said1296.mqttcontroller.annotations.Payload
import com.github.said1296.mqttcontroller.annotations.Publish
import com.github.said1296.mqttcontroller.annotations.Subscribe
import com.github.said1296.mqttcontroller.annotations.TopicParameter
import com.github.said1296.mqttcontroller.configurations.MqttControllerProperties
import com.github.said1296.mqttcontroller.constants.MatchType
import com.github.said1296.mqttcontroller.constants.PayloadManagingStrategy
import com.github.said1296.mqttcontroller.exceptions.CastException
import com.github.said1296.mqttcontroller.exceptions.ParameterException
import com.github.said1296.mqttcontroller.exceptions.PayloadException
import com.github.said1296.mqttcontroller.exceptions.TopicParameterException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.nio.charset.StandardCharsets
import javax.annotation.PostConstruct

@Component
class MqttController(val baseTopic: String, val matchType: MatchType = MatchType.MULTI_LEVEL) {
    lateinit var client: ClientMqtt

    @Autowired
    lateinit var gson: Gson
    @Autowired
    lateinit var mqttControllerProperties: MqttControllerProperties

    /**
     * Wait for lateinits to Autowire before creating client
     */
    @PostConstruct
    private fun postConstruct(){
        client = ClientMqtt(baseTopic, matchType, mqttControllerProperties)
        reflectSubtopics()
        client.subscribe()
    }

    /**
     * Publishes to the subtopic selected using the [PayloadManagingStrategy] specified in the [Publish] annotation.
     * @param method The method annotated with [Publish] intercepted by AOP
     * @param parameterValues Parameter values intercepted by AOP when [method] was called
     */
    fun publish(method: Method, parameterValues: Array<Any>) {
        val relativeTopic = method.getAnnotation(Publish::class.java).relativeTopic
        val topicParameters: MutableMap<String, String> = mutableMapOf()
        var payloadAsString = ""
        val payloadAsMap: MutableMap<String, Any> = mutableMapOf()

        val payloadManagingStrategy = method.getAnnotation(Publish::class.java).payloadManagement

        for ((index, parameter) in method.parameters.withIndex()) {
            if (doesParameterHaveAnnotation(parameter, Payload::class.java)) {
                when (payloadManagingStrategy) {
                    PayloadManagingStrategy.COMMA_SEPARATED -> {
                        payloadAsString += "${parameterValues[index]},"
                    }
                    PayloadManagingStrategy.SIMPLE -> {
                        if (payloadAsString != "") throw PayloadException("There can only be one @Payload annotated parameter with the current managing strategy: ${payloadManagingStrategy.name}")
                        payloadAsString = if (parameter.type.isPrimitive) {
                            parameterValues[index].toString()
                        } else {
                            gson.toJson(parameterValues[index])
                        }
                    }
                    PayloadManagingStrategy.JSON_DECONSTRUCT -> {
                        payloadAsMap[parameter.name] = parameterValues[index]
                    }
                }
            } else if (doesParameterHaveAnnotation(parameter, TopicParameter::class.java)) {
                topicParameters[parameter.name] = parameterValues[index].toString()
            } else {
                throw ParameterException("The parameter ${parameter.name} must be annotated by a parameter annotation")
            }
        }

        when (payloadManagingStrategy) {
            PayloadManagingStrategy.JSON_DECONSTRUCT -> {
                payloadAsString = gson.toJson(payloadAsMap)
            }
            PayloadManagingStrategy.COMMA_SEPARATED -> {
                // Remove last comma
                payloadAsString = payloadAsString.substring(0, payloadAsString.length - 1)
            }
            else -> {
            }
        }

        client.publish(relativeTopic, topicParameters, payloadAsString)
    }

    private fun doesParameterHaveAnnotation(parameter: Parameter, annotationClass: Class<out Annotation>): Boolean {
        return parameter.getAnnotation(annotationClass) != null
    }

    /**
     * Reflects class methods looking for [Subscribe] annotations to register them in the [client].
     */
    private fun reflectSubtopics() {
        for (method in this.javaClass.declaredMethods) {
            if (method.getAnnotation(Subscribe::class.java) != null) {
                val subtopicAnnotation = method.getAnnotation(Subscribe::class.java)
                client.registerSubtopic(subtopicAnnotation.relativeTopic, subtopicAnnotation.matchType)
                { payloadAsByteArray: ByteArray, topicParameters: Map<String, String> ->
                    subtopicCallbackInvocation(payloadAsByteArray, topicParameters, method)
                }
            }
        }
    }

    /**
     * Callback for when subtopic defined by the [Subscribe] annotation receives a message.
     *
     * It invokes the method annotated by [Subscribe] and handles its parameters annotated by
     * [TopicParameter] and [Payload].
     *
     * It handles the payload following the [PayloadManagingStrategy] present in the [Subscribe] annotation.
     *
     * @throws TopicParameterException If a parameter of [method] annotated with [TopicParameter] doesn't exist in
     * [topicParameters] returned by [client]
     * @param payloadAsByteArray Payload as received from the broker
     * @param topicParameters Parameters received in the topic
     * @param method Method to invoke
     */
    private fun subtopicCallbackInvocation(payloadAsByteArray: ByteArray, topicParameters: Map<String, String>, method: Method) {
        val parameterValues: MutableList<Any> = mutableListOf()
        val payloadManagingStrategy = method.getAnnotation(Subscribe::class.java).payloadManagement
        val payloadAsString = String(payloadAsByteArray, StandardCharsets.UTF_8)

        val payloadSplitAtComma: List<String>? =
                if (payloadManagingStrategy == PayloadManagingStrategy.COMMA_SEPARATED) {
                    payloadAsString.split(",")
                } else null

        val payloadAsMappedJson: Map<*, *>? =
                if (payloadManagingStrategy == PayloadManagingStrategy.JSON_DECONSTRUCT) {
                    gson.fromJson(payloadAsString, Map::class.java)
                } else null

        var payloadParameterCount = 0

        for (parameter in method.parameters) {
            if (parameter.getAnnotation(Payload::class.java) != null) {
                when (payloadManagingStrategy) {
                    PayloadManagingStrategy.COMMA_SEPARATED -> {
                        if (payloadSplitAtComma!!.size - 1 < payloadParameterCount) throw PayloadException("Not enough comma separated values are present. The payload was: $payloadAsString")
                        val parameterValueDeserialized = castPayload(payloadSplitAtComma[payloadParameterCount], parameter.type)
                        parameterValues.add(parameterValueDeserialized)
                    }
                    PayloadManagingStrategy.SIMPLE -> {
                        if (payloadParameterCount > 0) throw PayloadException("There can only be one @Payload annotated parameter with the current managing strategy: ${payloadManagingStrategy.name}")
                        val parameterValueDeserialized = castPayload(payloadAsString, parameter.type)
                        parameterValues.add(parameterValueDeserialized)
                    }
                    PayloadManagingStrategy.JSON_DECONSTRUCT -> {
                        val parameterValueAsString = payloadAsMappedJson!![parameter.name]
                                ?: throw PayloadException("The JSON received doesn't contain the property ${parameter.name}")
                        val parameterValueDeserialized = castPayload(parameterValueAsString.toString(), parameter.type)
                        parameterValues.add(parameterValueDeserialized)
                    }
                }
                payloadParameterCount += 1
            } else if (parameter.getAnnotation(TopicParameter::class.java) != null) {
                val topicParameterValueAsString = topicParameters[parameter.name]
                        ?: throw TopicParameterException("Topic parameter not available in Map returned from ClientMqtt. Make sure topic parameter names are exclusively alphanumeric")
                val topicParameterValue = castPayload(topicParameterValueAsString, parameter.type)
                parameterValues.add(topicParameterValue)
            } else {
                throw ParameterException("The parameter ${parameter.name} must be annotated by a parameter annotation")
            }
        }
        method.invoke(this, *parameterValues.toTypedArray())
    }

    /**
     * Casts payload to a primitive or an object class.
     * @param payloadAsString Payload as received from the broker.
     * @param typeOfPayload Type of the payload defined with the [Payload] annotation. Can be a primitive or a class.
     */
    private fun castPayload(payloadAsString: String, typeOfPayload: Class<*>): Any {
        if (typeOfPayload == String::class.java) return payloadAsString
        return if (typeOfPayload.isPrimitive) {
            castPrimitive(payloadAsString, typeOfPayload)
        } else {
            gson.fromJson(payloadAsString, typeOfPayload)
        }
    }

    /**
     * Casts a string to a primitive. If [type] is [Boolean] it considers 0 and 1 as true and false.
     * @param valueAsString String to cast.
     * @param type Type to cast the string to.
     * @return [valueAsString] casted to [type]
     */
    private fun castPrimitive(valueAsString: String, type: Class<*>): Any {
        return when (type) {
            Boolean::class.java -> {
                when (valueAsString) {
                    "0" -> false
                    "1" -> true
                    else -> valueAsString.toBoolean()
                }
            }
            Int::class.java -> {
                val valueAsStringSplitAtDot = valueAsString.split(".")
                // Check if number has decimals
                if (valueAsStringSplitAtDot.size == 2) {
                    // Make sure decimals are 0
                    valueAsStringSplitAtDot[1].forEach { char ->
                        if (char != "0"[0]) throw CastException("Cannot cast number $valueAsString with decimals bigger than 0")
                    }
                    valueAsStringSplitAtDot[0].toInt()
                } else {
                    valueAsString.toInt()
                }
            }
            Double::class.java -> {
                valueAsString.toDouble()
            }
            Long::class.java -> {
                valueAsString.toInt()
            }
            Char::class.java -> {
                if (valueAsString.length > 1 || valueAsString.isBlank()) throw CastException("Cannot cast String of length ${valueAsString.length} to Char")
                valueAsString[0]
            }
            Short::class.java -> {
                valueAsString.toShort()
            }
            Byte::class.java -> {
                valueAsString.toByte()
            }
            Float::class.java -> {
                valueAsString.toFloat()
            }
            else -> {
                throw CastException("Cast to ${type.name} type primitive is not supported")
            }
        }
    }
}
