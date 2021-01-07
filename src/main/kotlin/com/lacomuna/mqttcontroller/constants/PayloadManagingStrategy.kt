package com.lacomuna.mqttcontroller.constants

enum class PayloadManagingStrategy(val value: Int) {
    /**
     * This is the default [PayloadManagingStrategy] used if there is no strategy defined in the [Subscribe]
     * annotation.
     *
     * Indicates that the payload will be casted to the type of the parameter annotated with the [Payload] annotation.
     * The type can be a primitive or a class (in which case the payload must be a JSON).
     */
    SIMPLE(0),

    /**
     * Indicates that the payload will be a JSON, and that the parameters annotated with [Payload] will correspond
     * to properties of the received JSON.
     *
     * Example:
     *
     *     Received payload as string:
     *         {"id": 123, "name": "Aaron"}
     *
     *     Declared method annotated with [Subtopic] and [PayloadJsonDeconstruct]:
     *         fun handleMessage(@Payload id: Int, @Payload name: String)
     *
     *     id expected value:
     *         123 (Int)
     *
     *     name expected value:
     *         Aaron (String)
     */
    JSON_DECONSTRUCT(1),

    /**
     * Indicates that the received payload will be comma separated. Values are mapped to [Payload] annotated parameters in order
     * of declaration.
     *
     * Example:
     *
     *     Received payload as string:
     *         123,Aaron
     *
     *     Declared method annotated with [Subtopic] and [CommaSeparatedPayload]:
     *         fun handleMessage(@Payload id: Int, @Payload name: String)
     *
     *     id expected value:
     *         123 (Int)
     *
     *     name expected value:
     *         Aaron (String)
     */
    COMMA_SEPARATED(2)
}
