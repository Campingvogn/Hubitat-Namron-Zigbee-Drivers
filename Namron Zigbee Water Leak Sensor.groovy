/**
    Hubitat driver for Namron Zigbee Water leak sensor / Namron Zigbee vannlekasjesensor
    Author: Tjomp
*/

static String version() { "0.17" }
static String releasedate() { "16.june.2024" }

metadata {
   definition (name: "Namron Zigbee Water Leak Sensor", namespace: "Tjomp", author: "Tjomp") {
      capability "Initialize"
      capability "Configuration"
      capability "Refresh"
      capability "Battery"
      capability "VoltageMeasurement"
      capability "WaterSensor"
   }

   preferences {
        input name: "verbose",   type: "bool", title: "<b>Enable verbose logging?</b>",   description: "<br>", defaultValue: true 
        input name: "debug",   type: "bool", title: "<b>Enable debug logging?</b>",   description: "<br>", defaultValue: false
        input name: "pollRate", type: "number", title: "Poll Rate", description: "Number of seconds between 20 and 3600. Sets how often sensors readings are updated", range: "15..3600", defaultValue: 120
   }
}

// Run when driver is installed
def installed() {
   log.debug "installed()"
   def verbose = true
   def debug = false
   def pollRate = 120
    
}

//Run when HUB reboots
def initialize() {
    log.debug "initialize()"
    configure()
}

def configure() {
     
    def cmds = []
    cmds = "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0001 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0500 {${device.zigbeeId}} {}"
    
    //List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)   
    cmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, 10, pollRate.intValue()) //Battery Voltage
    cmds += zigbee.configureReporting(0x0001, 0x0021, 0x20, 10, pollRate.intValue()) //Battery percentage remaining
    cmds += zigbee.configureReporting(0x0500, 0x0002, 0x19, 5, pollRate.intValue()) //Water sensor

    if (verbose==true){log.info "Configuring Namron Waterleak sensor - Driver version : "+version()}
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
    cmds += zigbee.readAttribute(0x0500, 0x0002, [destEndpoint: 0x01],2000) //Read Water Sensor  

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
        else if (descMap.cluster == "0500" && descMap.attrId == "0002")
        {
            map.name = "water"
            def state = (Integer.parseInt(descMap.value, 16) >> 0 ) & 1
            if (state == 1) {
                map.value = "wet"
            }
            else {
                map.value = "dry"
            }             
            sendEvent(name:"water", value:map.value)
            //runIn( 20, isWet, [overwrite: true])
            if (verbose==true) {log.info "Received Water state: $map.value"}
        }
 
    }

    if (map != [:]) {
		return createEvent(map)
	} else
		return [:]
}

/*
def isWet()
{
    if (debug==true) {log.debug ("Inside isWet()-loop")}
    
    def cmds = []  
    if (water=="wet"){
        if (verbose==true){log.info "Is still Wet"}
        cmds = zigbee.readAttribute(0x0500, 0x0002, [destEndpoint: 0x01],2000) //Read Water Sensor 
        runIn( 20, isWet, [overwrite: true])
    }
    return cmds
}
*/
