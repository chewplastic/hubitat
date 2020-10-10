definition(
    name: "Inovelli Switches",
    namespace: "chewplastic",
    author: "Jesse Jordan",
    singleInstance: true,
    description: "Use an Inovelli Switch to Control Smart Lights",
    category: "General",        
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)   

preferences {
    section ("") {
        paragraph title: "Inovelli Switches", "Create a new controller for each switch"
    }
    section {
        app(name: "childApps", appName: "Inovelli Switch Child", namespace: "chewplastic", title: "Create New Switch", multiple: true)
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug "Initializing; there are ${childApps.size()} child apps installed"
    childApps.each {child ->
        log.debug "  child app: ${child.label}"
    }
}
