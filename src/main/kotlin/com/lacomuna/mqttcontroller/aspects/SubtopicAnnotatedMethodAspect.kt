package com.lacomuna.mqttcontroller.aspects


import com.lacomuna.mqttcontroller.controller.MqttController
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature

@Aspect
class SubtopicAnnotatedMethodAspect {
    @AfterReturning("@annotation(com.lacomuna.mqttcontroller.annotations.Publish)")
    fun afterReturn(joinPoint: JoinPoint) {
        val signature: MethodSignature = joinPoint.signature as MethodSignature
        val method = signature.method
        try{
            (joinPoint.`this` as MqttController).publish(method, joinPoint.args)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }
}
