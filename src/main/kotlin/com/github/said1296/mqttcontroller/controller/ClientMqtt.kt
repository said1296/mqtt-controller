package com.github.said1296.mqttcontroller.controller

import com.github.said1296.mqttcontroller.configurations.MqttControllerProperties
import com.github.said1296.mqttcontroller.constants.MatchType
import com.github.said1296.mqttcontroller.exceptions.TopicParameterException
import com.github.said1296.mqttcontroller.models.SubtopicUserInput
import com.github.said1296.mqttcontroller.models.TopicMatchResult
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence


class ClientMqtt(private var baseTopic: String, private val matchType: MatchType = MatchType.MULTI_LEVEL, mqttControllerProperties: MqttControllerProperties, keepAliveInterval: Int, clientId: String = "") {

    var client: MqttClient = MqttClient(mqttControllerProperties.broker + ":" + mqttControllerProperties.port, clientId, MqttDefaultFilePersistence(".mqtt"))
    private var subtopicUserInputs: MutableList<SubtopicUserInput> = mutableListOf()
    private val topicLevelsRegex = Regex("(?<=/|^)(.*?)(?=/)")
    private val topicParametersRegex = Regex("(?<=\\{)(.*?)(?=\\})")
    private var baseTopicFormatted: String

    init {
        baseTopicFormatted = addMatchTypeToTopic(baseTopic, matchType)

        client.setCallback(object: MqttCallbackExtended {
            override fun connectionLost(cause: Throwable) {
                println("MQTT ERROR: Connection lost to ${mqttControllerProperties.broker}:${mqttControllerProperties.port}. Cause: ${cause.message}")
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) {
                    subscribe()
                }
                println("MQTT: Connection to ${mqttControllerProperties.broker}:${mqttControllerProperties.port} completed. Is a reconnect attempt: $reconnect")
            }

        })
        val options = defaultOptions
        options.keepAliveInterval = keepAliveInterval
        baseTopic = baseTopic.trim()
        client.connect(options)
    }

    // Communication

    /**
     * Subscribes to an MQTT broker's topic. It's recommended to subscribe after all subtopics have been registered.
     */
    fun subscribe() {
        client.unsubscribe(baseTopicFormatted)
        client.subscribe(baseTopicFormatted) { topic, message -> handleTopicAndMessage(topic, message) }
    }

    /**
     * Routes messages to its respective subtopic's callback
     * @param topic Topic in which the message was received
     * @param message [MqttMessage] returned by [IMqttMessageListener]
     */
    fun handleTopicAndMessage(topic: String, message: MqttMessage) {
        // Topic without baseTopic
        val subtopicReceived = topic.substring(baseTopic.length)

        for (subtopicUserInput in subtopicUserInputs) {
            val topicMatchResult = matchTopics(subtopicUserInput.subtopic, subtopicReceived)
            if (topicMatchResult.match) {
                try {
                    subtopicUserInput.callback.invoke(message.payload, topicMatchResult.topicParameters)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    }

    /**
     * Publishes a message.
     * @param userTopic Topic as set by the user with the [Subscribe] annotation
     * @param topicParameters Map with values for each topic parameter present in the topic
     * @param payloadAsString [String] to send
     */
    fun publish(userTopic: String, topicParameters: Map<String, String>, payloadAsString: String) {
        val topicToPublish = baseTopic + setTopicParameters(userTopic, topicParameters)
        client.publish(topicToPublish, MqttMessage(payloadAsString.toByteArray()))
    }

    /**
     * Sets topic parameters to a topic.
     *
     * Example:
     *     topic: /sensor/{idSensor}
     *     topicParameters: {idSensor: 234}
     *     return: /sensor/234
     *
     * @param topic Topic that will have its generic topic parameters replaced by values
     * @param topicParameters Map with topic parameter names as keys
     * @return [String] with topic parameters replaced by the values in [topicParameters]
     */
    private fun setTopicParameters(topic: String, topicParameters: Map<String, String>): String {
        val hasLeadingSlash = topic.takeLast(1) == "/"
        var topicWithTopicParameters = ""
        val topicLevels = getTopicLevels(topic)
        for ((index, topicLevel) in topicLevels.withIndex()) {
            val topicParameterName = getTopicParameterName(topicLevel)
            topicWithTopicParameters +=
                    if (topicParameterName == null) {
                        if(!hasLeadingSlash && index == topicLevels.size - 1) topicLevel
                        else "$topicLevel/"
                    } else {
                        val topicParameterValue = topicParameters[topicParameterName]
                                ?: throw TopicParameterException("Cannot create topic for publish since there is no value for $topicParameterName topic parameter passed")
                        if(!hasLeadingSlash && index == topicLevels.size - 1) topicParameterValue
                        else "$topicParameterValue/"
                    }
        }
        return topicWithTopicParameters
    }

    // Topic handling

    /**
     * Registers a subtopic with its respective callback, MatchType and subtopic parameters.
     * @param subtopic Subtopic, string relative to baseTopic
     * @param callback The callback for when the subtopic is matched, it passes a String
     * @param matchType The MatchType to follow for this subtopic
     */
    fun registerSubtopic(subtopic: String,
                         matchType: MatchType = MatchType.NONE,
                         callback: (payloadAsByteArray: ByteArray, topicParameters: Map<String, String>) -> Unit) {
        subtopicUserInputs.add(
                SubtopicUserInput(
                        formatTopic(subtopic),
                        callback,
                        matchType)
        )
    }

    /**
     * Compares two topics and extracts the topic parameters. Returns a [TopicMatchResult] with information of whether
     * it matched and the topic parameter names and values as a Map.
     *
     * @param subtopicUser The subtopic inputted by the user with the [Subscribe] annotation]
     * @param subtopicReceived Topic received from the MQTT client when message arrives but stripped of [baseTopic]
     * @return [TopicMatchResult]
     */
    fun matchTopics(subtopicUser: String, subtopicReceived: String): TopicMatchResult {
        val topicMatchResult = TopicMatchResult()
        val subtopicOriginalLevels: List<String> = getTopicLevels(subtopicUser)
        val subtopicReceivedLevels: List<String> = getTopicLevels(formatTopic(subtopicReceived))

        // If topics don't have same number of levels, they don't match
        if (subtopicOriginalLevels.size != subtopicReceivedLevels.size) {
            topicMatchResult.match = false
            return topicMatchResult
        }

        // Compare all levels
        for ((index, subtopicOriginalLevel) in subtopicOriginalLevels.withIndex()) {

            /**
             * If topic level is a topic parameter, then it matches since whatever the
             * received topic level is, it is treated as the topic parameter value
             */
            val topicParameterName = getTopicParameterName(subtopicOriginalLevel)
            if (topicParameterName != null) {
                topicMatchResult.topicParameters[topicParameterName] = subtopicReceivedLevels[index]
                continue
            }

            if (subtopicReceivedLevels[index] == subtopicOriginalLevel) continue
            else {
                topicMatchResult.match = false
                break
            }
        }


        return topicMatchResult
    }

    private fun getTopicLevels(topic: String): List<String> {
        val formattedTopic = formatTopic(topic)
        return topicLevelsRegex.findAll(formattedTopic).map { topicLevelMatch -> topicLevelMatch.value }.toCollection(mutableListOf())
    }

    private fun getTopicParameterName(topicLevel: String): String? {
        val topicParameters = getTopicParametersNames(topicLevel)
        return if (topicParameters.isNotEmpty()) topicParameters[0]
        else null
    }

    private fun getTopicParametersNames(topic: String): List<String> {
        return topicParametersRegex.findAll(topic).map { topicParameterMatch -> topicParameterMatch.value }.toCollection(mutableListOf())
    }


    // Topic format handling

    /**
     * Formats topic so it can be interpreted by the broker
     * @param topic Topic to format
     * @param matchType [MatchType] to use for formatting
     */
    fun formatTopic(topic: String): String {
        var topicFormatted: String = topic.trim()
        if (topic.startsWith("/")) topicFormatted = topicFormatted.substring(1)
        if (!topic.endsWith("/")) topicFormatted = "$topicFormatted/"
        return topicFormatted
    }

    /**
     * Formats topic with [formatTopic] and converts [MatchType] into a leading char interpretable by an MQTT broker.
     * @param topic Topic to format
     * @param matchType [MatchType] to use for formatting
     */
    fun addMatchTypeToTopic(topic: String, matchType: MatchType): String {
        var topicWithMatch: String = formatTopic(topic)
        val charToAdd = when (matchType) {
            MatchType.SINGLE_LEVEL -> "+"
            MatchType.MULTI_LEVEL -> "#"
            else -> ""
        }

        topicWithMatch = "$topicWithMatch$charToAdd"

        return topicWithMatch
    }

    companion object {
        val defaultOptions = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            connectionTimeout = 10
        }
    }
}
