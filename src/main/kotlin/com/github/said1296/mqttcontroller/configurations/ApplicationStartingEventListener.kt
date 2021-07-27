package com.github.said1296.mqttcontroller.configurations

import de.invesdwin.instrument.DynamicInstrumentationLoader
import org.springframework.boot.context.event.ApplicationStartingEvent
import org.springframework.context.ApplicationListener

class ApplicationStartingEventListener: ApplicationListener<ApplicationStartingEvent> {
    override fun onApplicationEvent(event: ApplicationStartingEvent) {
        DynamicInstrumentationLoader.waitForInitialized()
        DynamicInstrumentationLoader.initLoadTimeWeavingContext()
    }
}
