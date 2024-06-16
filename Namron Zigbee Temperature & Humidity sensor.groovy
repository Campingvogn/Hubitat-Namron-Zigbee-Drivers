/**
    Hubitat driver for Namron Zigbee Temperature & Humidity sensor / Namron Zigbee Temperatur og fuktighet sensor
    Author: Tjomp
*/

static String version() { "0.01" }
static String releasedate() { "16.june.2024" }

metadata {
   definition (name: "Namron Zigbee Temperature & Humidity sensor", namespace: "Tjomp", author: "Tjomp") {
      capability "Initialize"
      capability "Configuration"
      capability "Refresh"
      capability "Battery"
      capability "VoltageMeasurement"
   }

   preferences {
        input name: "verbose",   type: "bool", title: "<b>Enable verbose logging?</b>",   description: "<br>", defaultValue: true 
        input name: "debug",   type: "bool", title: "<b>Enable debug logging?</b>",   description: "<br>", defaultValue: false
        input name: "pollRate", type: "number", title: "Poll Rate", description: "Number of seconds between 20 and 3600. Sets how often sensors readings are updated", range: "15..3600", defaultValue: 240
   }
}

// Run when driver is installed
def installed() {
   log.debug "installed()"
   def verbose = true
   def debug = false
   def pollRate = 240
    
}

//Run when HUB reboots
def initialize() {
    log.debug "initialize()"
    configure()
}

def configure() {
     
    def cmds = []
    cmds = "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0001 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0402 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0405 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x020 {${device.zigbeeId}} {}" //POLL rate
    
    //List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)   
    cmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, 10, pollRate.intValue(),1) //Battery Voltage
    cmds += zigbee.configureReporting(0x0001, 0x0021, 0x20, 10, pollRate.intValue(),1) //Battery percentage remaining
    cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 10, pollRate.intValue(),1) //Temperature
    cmds += zigbee.configureReporting(0x0405, 0x0000, 0x29, 10, pollRate.intValue(),1) //Humidity
    cmds += zigbee.configureReporting(0x0020, 0x0004, 0x23, 10, pollRate.intValue(),1) //Min poll   
    if (verbose==true){log.info "Configuring Namron Temperature & Humidity sensor - Driver version : "+version()}
    if (debug==true) {log.debug (cmds)}
    return cmds + refresh()
}

def updated() {
    configure()
}

def refresh() {
    if (verbose==true){log.info "refreshed"}
    
    def cmds = []  
    
    cmds = zigbee.readAttribute(0x0001, 0x0020, [destEndpoint: 0x01],2000) //Read Battery Voltage 
    cmds += zigbee.readAttribute(0x0001, 0x0021, [destEndpoint: 0x01],2000) //Read Battery percentage remaining 
    cmds += zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x01],2000) //Read Temperature  
    cmds += zigbee.readAttribute(0x0405, 0x0000, [destEndpoint: 0x01],2000) //Read Humidity

    cmds += zigbee.readAttribute(0x0020, 0x0004, [destEndpoint: 0x01],2000) //POLL STUFF   

    runIn( pollRate, refresh, [overwrite: true])
    return cmds
}  

def parse(String description) {
    if (debug==true) {log.debug ("Parsing")}
    def map = [:]
    if (description?.startsWith('zone status')) {
        if (verbose==true){log.info "Zone status changed. Refreshing sensors."}
        runIn( 3, refresh, [overwrite: true])
    }
    else if (description?.startsWith("catchall") || description?.startsWith("read attr"))
    {
        def descMap = zigbee.parseDescriptionAsMap(description)
        if (debug==true) {log.debug (descMap)}
        
        if (descMap.cluster == "0001" && descMap.attrId == "0021")
        {
            map.name = "battery"
            map.value = Integer.parseInt(descMap.value, 16)/2
            sendEvent(name:"battery", value:map.value)
            if (verbose==true){log.info "Received Battery state: $map.value percent"}
        }
        else if (descMap.cluster == "0001" && descMap.attrId == "0020") {
            map.name = "voltage"
            map.value = Integer.parseInt(descMap.value, 16)/10
            sendEvent(name:"voltage", value:map.value)
            if (verbose==true){log.info "Received Voltage state: $map.value volt"}
        } 
        else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            map.name = "temperature"
            map.value = Integer.parseInt(descMap.value, 16)/100
            sendEvent(name:"temperature", value:map.value)
            if (verbose==true){log.info "Received Temperature: $map.value C"}
        } 
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            map.name = "humidity"
            map.value = Integer.parseInt(descMap.value, 16)/100
            sendEvent(name:"humidity", value:map.value)
            if (verbose==true){log.info "Received Humidity: $map.value percent"}
        } 
        else if (descMap.cluster == "0020" && descMap.attrId == "0004") {
            map.name = "min poll"
            map.value = Integer.parseInt(descMap.value, 16)
            sendEvent(name:"min poll", value:map.value)
            if (verbose==true){log.info "Received min poll: $map.value "}
        } 

    }

    if (map != [:]) {
		return createEvent(map)
	} else
		return [:]
}
