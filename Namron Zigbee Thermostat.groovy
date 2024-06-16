/**
    Hubitat driver for Namron Zigbee Thermostat and Electric radiator / Namron Zigbee Termostat og panelovn.
    Author: Tjomp
*/

static String version() { "0.31" }
static String releasedate() { "16.june.2024" }

metadata {
   definition (name: "Namron Zigbee Thermostat", namespace: "Tjomp", author: "Tjomp") {
      capability "Initialize"
      capability "TemperatureMeasurement"
      capability "ThermostatHeatingSetpoint"
      capability "ThermostatOperatingState"
      capability "PowerMeter"
      capability "Refresh"
      capability "Configuration"
   }

   preferences {
        input name: "verbose",   type: "bool", title: "<b>Enable verbose logging?</b>",   description: "<br>", defaultValue: true 
        input name: "debug",   type: "bool", title: "<b>Enable debug logging?</b>",   description: "<br>", defaultValue: false
        input name: "tempCalibration", type: "number", title: "Temperature Calibration", description: "Number between -3 and 3 degrees to calibrate temperature sensor", range: "-3..3", defaultValue: 0
        input name: "pollRate", type: "number", title: "Poll Rate", description: "Number of seconds between 15 and 300. Sets how often sensors readings are updated", range: "15..300", defaultValue: 120
   }
}

// Run when driver is installed
def installed() {
    def verbose=true
    def debug=false
    def tempCalibration=0
    def pollRate=120
    log.info "installed()"
}

//Run when HUB reboots
def initialize() {
    log.debug "initialize()"
    configure()
}

def configure() {
     
    def cmds = []
    cmds = "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0201 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0B04 {${device.zigbeeId}} {}"
    
    cmds += zigbee.writeAttribute(0x201, 0x001c, 0x30, (byte) 0x04) //Set SystemMode=Heat
    cmds += zigbee.configureReporting(0x201, 0x0000, 0x29, 10, pollRate.intValue()) //LocalTemperature - int16S
    cmds += zigbee.configureReporting(0x201, 0x0010, 0x28, 10, pollRate.intValue()) //LocalTemperatureCalibration - int8S
    cmds += zigbee.configureReporting(0x201, 0x0012, 0x29, 10, pollRate.intValue()) //OccupiedHeatingSetpoint - int16S
    cmds += zigbee.configureReporting(0x201, 0x001c, 0x30, 10, pollRate.intValue()) //SystemMode - Enum8
    cmds += zigbee.configureReporting(0xB04, 0x050B, 0x29, 10, pollRate.intValue()) //ActivePower - int16S

    if (verbose==true) {log.info "Configuring thermostat - Driver version : "+version()}
    if (debug==true) {log.debug (cmds)}
    return cmds + refresh()
}

def updated() {
     def cmds = [""]
    
    if (tempCalibration != null) {
        tempCalibration = tempCalibration * 10
        cmds += zigbee.writeAttribute(0x201, 0x0010, 0x28, (byte) tempCalibration)
    }
    
    return cmds + configure()
}

def parse(String description) {
    if (debug==true) {log.debug ("Parsing")}
    def map = [:]
    
     if (description?.startsWith('zone status')) {
        if (verbose==true){log.info "Zone status changed. Refreshing sensors."}
        //runIn( 3, refresh, [overwrite: true])
    }
    else if (description?.startsWith("catchall") || description?.startsWith("read attr"))
    {
        def descMap = zigbee.parseDescriptionAsMap(description)
        if (debug==true) {log.debug (descMap)}
        if (descMap.cluster == "0201" && descMap.attrId == "0000")
        {
            map.name = "temperature"
            map.value = getTemperature(descMap.value)
            sendEvent(name:"temperature", value:map.value)
            if (verbose==true) {log.info "temperature: $map.value"}

        }
        else if (descMap.cluster == "0201" && descMap.attrId == "0010") {
            map.name = "temperatureCalibration"
            map.value = Integer.parseInt(descMap.value, 16)/10
            sendEvent(name:"temperatureCalibration", value:map.value)
            if (verbose==true) {log.info "temperatureCalibration: $map.value"}

        }
        else if (descMap.cluster == "0201" && descMap.attrId == "0012") {
            map.name = "heatingSetpoint"
            map.value = getTemperature(descMap.value)
            sendEvent(name:"heatingSetpoint", value:map.value)
            if (verbose==true) {log.info "heatingSetpoint: $map.value"}

        }
        else if (descMap.cluster == "0201" && descMap.attrId == "001C") {
            map.name = "SystemMode"
            map.value = descMap.value
            sendEvent(name:"SystemMode", value:map.value)
        }
        else if (descMap.cluster == "0B04" && descMap.attrId == "050B") {
            def power = Math.round(Integer.parseInt(descMap.value, 16)/10)
            map.value = power
            sendEvent(name:"power", value:map.value)
            if (verbose==true) {log.info "Power: $map.value"}

            map.name = "thermostatOperatingState"
            if (power < 10) {
                map.value = "idle"
            }
            else {
                map.value = "heating"
            }
             sendEvent(name:"thermostatOperatingState", value:map.value)
        }
    }

    if (map != [:]) {
		return createEvent(map)
	} else
		return [:]
}

def refresh() {
    if (verbose==true){log.info "refreshed"}
    def cmds = []

    cmds = zigbee.readAttribute(0x0201, 0x0000, [destEndpoint: 0x01],2000) //Read LocalTemperature Attribute
    cmds += zigbee.readAttribute(0x0201, 0x0010, [destEndpoint: 0x01],2000) //Read LocalTemperatureCalibration
    cmds += zigbee.readAttribute(0x0201, 0x0012, [destEndpoint: 0x01],2000) //Read OccupiedHeatingSetpoint 
    cmds += zigbee.readAttribute(0x0201, 0x001c, [destEndpoint: 0x01],2000) //Read SystemMode
    cmds += zigbee.readAttribute(0x0b04, 0x050b, [destEndpoint: 0x01],2000) //Read ActivePower 

    runIn( pollRate, refresh, [overwrite: true])
    return cmds
}   

def setHeatingSetpoint(temperature) {
    if (temperature != null) {
        def scale = getTemperatureScale()
        def degrees = new BigDecimal(temperature).setScale(1, BigDecimal.ROUND_HALF_UP)
        def celsius = (scale == "C") ? degrees as Float : (fahrenheitToCelsius(degrees) as Float).round(2)
        int celsius100 = Math.round(celsius * 100)
        if (verbose==true) {log.info "Setting temperature: $celsius100\\100 in $scale"}
        zigbee.writeAttribute(0x201, 0x0012, 0x29, celsius100)
    }
}

def getTemperature(value) {
    if (value != null) {
        def celsius = Integer.parseInt(value, 16) / 100
        if (getTemperatureScale() == "C") {
            return celsius
        }
        else {
            return Math.round(celsiusToFahrenheit(celsius))
        }
    }
}

def getTemperatureScale() {
    return "${location.temperatureScale}"
}
