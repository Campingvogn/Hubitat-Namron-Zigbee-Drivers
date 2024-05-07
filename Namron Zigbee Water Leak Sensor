/**
    Hubitat driver for Namron Zigbee Water leak sensor / Namron Zigbee vannlekkasjesensor
    Version: 0.10
    Date: 7.may.2024
    Author: Tjomp
*/

metadata {
   definition (name: "Namron Zigbee Water Leak Sensor", namespace: "Tjomp", author: "Tjomp") {
      capability "Configuration"
      capability "Refresh"
      capability "Battery"
      capability "VoltageMeasurement"
      capability "WaterSensor"
   }

   preferences {
        input name: "verbose",   type: "bool", title: "<b>Enable verbose logging?</b>",   description: "<br>", defaultValue: true 
        input name: "debug",   type: "bool", title: "<b>Enable debug logging?</b>",   description: "<br>", defaultValue: false
        input name: "pollRate", type: "number", title: "Poll Rate", description: "Number of seconds between 15 and 300. Sets how often sensors readings are updated", range: "15..300", defaultValue: 120
   }
}

def installed() {
   log.debug "installed()"
   def verbose = true
   def debug = false
   def pollRate = 120
    
}

def configure() {
     
    def cmds = []
    cmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, 10, pollRate.intValue()) //Battery Voltage
    cmds += zigbee.configureReporting(0x0001, 0x0021, 0x20, 10, pollRate.intValue()) //Battery percentage remaining
    cmds += zigbee.configureReporting(0x0500, 0x0002, 0x19, 10, pollRate.intValue()) //Water sensor

    if (verbose==true){log.info "Configuring Waterleak sensor - Driver version : 0.10"}
    if (debug==true) {log.debug (cmds)}
    return cmds + refresh()
}

def updated() {
    configure()
}

def parse(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (debug==true) {log.debug (descMap)}
    def map = [:]
    if (description?.startsWith("read attr -")) {
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
        else if (descMap.cluster == "0500" && descMap.attrId == "0002") {
            map.name = "water"
            def state = (Integer.parseInt(descMap.value, 16) >> 0 ) & 1
            if (state == 1) {
                map.value = "wet"
            }
            else {
                map.value = "dry"
            }             
            sendEvent(name:"water", value:map.value)
            if (verbose==true) {log.info "Received Water state: $map.value"}
        }
 
    }

    def result = null
    if (map) {
        result = createEvent(map)
    }
    return result
}

def refresh() {
    def cmds = []  
    cmds += zigbee.readAttribute(0x0001, 0x0020) //Read Battery Voltage
    cmds += zigbee.readAttribute(0x0001, 0x0021) //Read Battery percentage remaining
    cmds += zigbee.readAttribute(0x0500, 0x0002) //Read Water Sensor
    if (verbose==true){log.info "refreshed"}
    return cmds
}   
