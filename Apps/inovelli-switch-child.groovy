definition(
        name: "Inovelli Switch Child",
        namespace: "chewplastic",
        parent: "chewplastic:Inovelli Switches",
        author: "Jesse Jordan",
        description: "Sync and control switches with light bulbs",
        category: "General",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: ""
)

preferences {
    page(name: "pageMain", content: "pageMain")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Inovelli Switch/Light Sync", install: true, uninstall: true) {
        section {
            label title: "Assign a name", required: false
            input "enableDebug", "bool", title: "Enable debugging", required: false
        }

        section("Switch/Light Settings") {
            input "myswitch", "capability.switch", title: "Inovelli Switch to Sync", multiple: false, required: true
            input "switchLed", "capability.light", title: "Inovelli Switch LED to Sync to", multiple: false, required: true
            input "lights", "capability.light", title: "Switch controls these light(s)", multiple: true, required: true
            input "lightsLed", "capability.light", title: "Use these light(s) to determine switch LED colors", multiple: true, required: false
        }

        Map buttonActions = [
            pressConfig: 'Tap Config',
            pressUpX1: 'Tap ▲',
            pressUpX2: 'Tap ▲▲',
            pressUpX3: 'Tap ▲▲▲',
            pressUpX4: 'Tap ▲▲▲▲',
            pressUpX5: 'Tap ▲▲▲▲▲',
            pressDownX1: 'Tap ▼',
            pressDownX2: 'Tap ▼▼',
            pressDownX3: 'Tap ▼▼▼',
            pressDownX4: 'Tap ▼▼▼▼',
            pressDownX5: 'Tap ▼▼▼▼▼',
            holdUp: 'Hold ▲',
            holdDown: 'Hold ▼',
        ]

        /**
         * The indicator section will only appear if one of the on/off switch actions is set
         */
        Boolean showIndicators
        buttonActions.each { action, desc ->
            section{
                paragraph("<hr style=\"background-color: black; border: 0; height: 2px;\"/>")
            }

            section("${desc} Settings") {
                input "${action}off", "capability.switch", title: "${desc}: Turn OFF switches", description: "Optional switches to turn off for ${desc}", multiple: true, required: false, submitOnChange: true
                input "${action}", "capability.switch", title: "${desc}: Turn ON switches", description: "Optional switches to turn on for ${desc}", multiple: true, required: false, submitOnChange: true
                showIndicators = false
                if(binding.hasVariable("${action}") || binding.hasVariable("${action}off")) {
                    showIndicators = true
                    input (name: "${action}i", type: "bool", title: "${desc}: Show Indicator", defaultValue: true, required: true, submitOnChange: true)
                }
            }

            if(showIndicators) {
                section("${desc}: Indicator Settings", hideable: true, hidden: true) {
                    input "${action}i0", "enum", title: "${desc}: Color", description: "Tap to set", defaultValue: 234, required: true, options: [
                        0:"Red",
                        21:"Orange",
                        42:"Yellow",
                        85:"Green",
                        127:"Cyan",
                        170:"Blue",
                        212:"Violet",
                        234:"Pink",
                        255:"White",
                    ]
                    input "${action}i1", "enum", title: "${desc}: Brightness", description: "Tap to set", defaultValue: 10, required: true, options: [
                        0:"0%",
                        1:"10%",
                        2:"20%",
                        3:"30%",
                        4:"40%",
                        5:"50%",
                        6:"60%",
                        7:"70%",
                        8:"80%",
                        9:"90%",
                        10:"100%",
                    ]
                    input "${action}i2", "enum", title: "${desc}: Duration", description: "Tap to set", defaultValue: 1, required: true, options: [
                        255:"Indefinitely",
                        1:"1 Second",
                        2:"2 Seconds",
                        3:"3 Seconds",
                        4:"4 Seconds",
                        5:"5 Seconds",
                        6:"6 Seconds",
                        7:"7 Seconds",
                        8:"8 Seconds",
                        9:"9 Seconds",
                        10:"10 Seconds",
                    ]
                    input "${action}i3", "enum", title: "${desc}: Effect Type", description: "Tap to set", defaultValue: 2, required: true, options: [
                        0:"Off",
                        1:"Solid",
                        2:"Chase",
                        3:"Fast Blink",
                        4:"Slow Blink",
                        5:"Pulse",
                    ]
                }
            }
        }
    }
}

// Use @Field trick instead of "state" to avoid race conditions with simultaneous events by using synchronized{..}
import groovy.transform.Field
@Field static List eventsIgnore = []

def installed() {
    log.trace "App installed"
    initialize()
}

def updated() {
    log.trace "App updated"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    eventsIgnore = []
    switchSubscribe()
    bulbSubscribe()
    log.trace "App initialized"
}

def switchSubscribe() {
    //Map options = [filterEvents: false]
    Map options = [:]
    subscribe(myswitch, "switch", switchHandler, options)
    subscribe(myswitch, "level", switchHandler, options)
    subscribe(myswitch, "pushed", switchHandler, options)
    subscribe(myswitch, "held", switchHandler, options)
}

def bulbSubscribe() {
    //Map options = [filterEvents: false]
    Map options = [:]
    subscribe(lightsLed, "switch", lightHandler, options)
    subscribe(lightsLed, "level", lightHandler, options)
    subscribe(lightsLed, "colorMode", lightHandler, options)
    subscribe(lightsLed, "hue", lightHandler, options)
}

def eventCheckDup(String device, evt) {
    long nowTime = now()
    long timeoutMs = 25000
    Map found = [:]

    synchronized (eventsIgnore) {
        // See if we were expected to receive this event from the device in the allotted time
        eventsIgnore.each { item ->
            if(item.time > nowTime - timeoutMs) {
                if( item.device == device
                && item.name == evt.name
                && item.value == evt.value ) {
                    found = item
                }
            }
        }

        // Remove any events that have expired
        eventsIgnore.removeAll { item ->
            item.time <= nowTime - timeoutMs
        }

        // Remove the duplicate event that we matched, if any
        if(found) {
            logDebug("IGNORE ${device}: ${found.name}=${found.value}")
            eventsIgnore.remove(found)
            return true
        }
    }

    logDebug("NOT ignoring ${device}: ${evt.name}=${evt.value}")
    return false
}

def eventExpect(String device, String name, String value) {
    Map eventMap = [
        device: device,
        name: name,
        value: value,
        time: now(),
    ]

    logDebug("Will ignore: ${device}: ${name} = ${value}")
    synchronized (eventsIgnore) {
        eventsIgnore.add(eventMap)
    }
}

def lightsLedRefresh() {
    try {
        lightsLed.refresh()
    } catch (e) { /* ignore in case the light doesn't have a refresh method */ }
}

/**
 * Process events from the lightsLed to sync the light -> switch
 */
def lightHandler(evt) {
    // Check if this is an expected event from a previous action
    if(eventCheckDup("lights", evt)) {
        return
    }

//    /**
//     * This works around an apparent hubitat bug where the current "level" state of
//     * the lightsLed sending events here doesn't always update to the correct value
//     * until refresh() is called on the light.  Calling this will update the state
//     * and trigger another event.
//     */
//    runIn(5, 'lightsLedRefresh')

    switch(evt.name) {
        case "switch":
            if(evt.value == "on") {
                eventExpect("switch", "switch", "on")
                myswitch.on()
            } else {
                eventExpect("switch", "switch", "off")
                myswitch.off()
            }
            break

        case "level":
            eventExpect("switch", "level", evt.value == "100" ? "99" : evt.value)
            myswitch.setLevel(evt.value.toInteger(), 1)
            break

        case "colorMode":
            if(evt.value == "CT") {
                switchLed.setColorTemperature(1)
            }
            break

        case "hue":
            Map colorSetting = [
                hue: evt.value.toInteger(),
                saturation: 100,
                level: 100,
            ]
            switchLed.setColor(colorSetting)
            break
    }
}

def switchIndicator(String action) {
    logDebug("ACTION: ${action}")

    // Only set the indicator if at least one on/off switch is defined, and the indicator bool is on
    if((evaluate("${action}") || evaluate("${action + "off"}")) && evaluate("${action}i")) {
        Integer packedValue = ((evaluate("${action}i3").toInteger() & 0xFF) << 24) \
                            | ((evaluate("${action}i2").toInteger() & 0xFF) << 16) \
                            | ((evaluate("${action}i1").toInteger() & 0xFF) << 8) \
                            | (evaluate("${action}i0").toInteger() & 0xFF)

        myswitch.setIndicator(packedValue)
    }
}

def switchPress(String action) {
    def onSwitches = evaluate("${action}")
    def offSwitches = evaluate("${action + "off"}")

    switchIndicator(action)

    if(offSwitches) {
        offSwitches.off()
    }

    if(onSwitches) {
        onSwitches.on()
    }
}

/**
 * Process events from the switch to sync myswitch -> lights
 */
def switchHandler(evt) {
    // Check if this is an expected event from a previous action
    if(eventCheckDup("switch", evt)) {
        return
    }

//    /**
//     * This works around an apparent hubitat bug where the current "level" state of
//     * the lightsLed sending events here doesn't always update to the correct value
//     * until refresh() is called on the light.  Calling this will update the state
//     * and trigger another event.
//     */
//    runIn(5, 'lightsLedRefresh')

    switch(evt.name) {
        case 'switch':
            switch(evt.value) {
                case "on":
                    eventExpect("lights", "switch", "on")
                    lights.on()
                    break

                case "off":
                    eventExpect("lights", "switch", "off")
                    lights.off()
                    break
            }
            break

        case 'level':
            eventExpect("lights", "level", evt.value)
            lights.setLevel(evt.value.toInteger(), 1)
            break

        case 'pushed':
            switch(evt.value) {
                case "1": switchPress("pressUpX1"); break
                case "2": switchPress("pressUpX2"); break
                case "3": switchPress("pressUpX3"); break
                case "4": switchPress("pressUpX4"); break
                case "5": switchPress("pressUpX5"); break
                case "6": switchPress("holdUp"); break
                case "7": switchPress("pressConfig"); break
                default:
                    logDebug("WARNING: Got unexpected '${evt.name}=${evt.value}', ignoring")
                    break
            }
            break

        case 'held':
            switch(evt.value) {
                case "1": switchPress("pressDownX1"); break
                case "2": switchPress("pressDownX2"); break
                case "3": switchPress("pressDownX3"); break
                case "4": switchPress("pressDownX4"); break
                case "5": switchPress("pressDownX5"); break
                case "6": switchPress("holdDown"); break
                default:
                    logDebug("WARNING: Got unexpected '${evt.name}=${evt.value}', ignoring")
                    break
            }
            break

        default:
            logDebug("WHAT? ${evt.name} ${evt.value}")
            break
    }
}

def logDebug(txt) {
    if (enableDebug) {
        log.debug(txt)
    }
}
