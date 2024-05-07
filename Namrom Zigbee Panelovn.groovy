/**
    Hubitat driver for Namron Zigbee Thermostat and Electric radiator / Namron Zigbee Termostat og panelovn.
    Version: 0.27
    Date: 7.may.2024
    Author: Tjomp
*/

metadata {
   definition (name: "Namron Zigbee Thermostat", namespace: "Tjomp", author: "Tjomp") {
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

def installed() {
    def verbose=true
    def debug=fale
    def tempCalibration=0
    def pollRate=120
    log.info "installed()"
}

def configure() {
     
    def cmds = []
    cmds += zigbee.writeAttribute(0x201, 0x001c, 0x30, (byte) 0x04) //Set SystemMode=Heat
    cmds += zigbee.configureReporting(0x201, 0x0000, 0x29, 10, pollRate.intValue(),50) //LocalTemperature - int16S
    cmds += zigbee.configureReporting(0x201, 0x0010, 0x28, 10, pollRate.intValue(),10) //LocalTemperatureCalibration - int8S
    cmds += zigbee.configureReporting(0x201, 0x0012, 0x29, 10, pollRate.intValue(),100) //OccupiedHeatingSetpoint - int16S
    cmds += zigbee.configureReporting(0x201, 0x001c, 0x30, 10, pollRate.intValue()) //SystemMode - Enum8
    cmds += zigbee.configureReporting(0xB04, 0x050B, 0x29, 10, pollRate.intValue(),50) //ActivePower - int16S

    if (verbose==true) {log.info "Configuring thermostat - Driver version : 0.27"}
    if (debug==true) {log.debug ("PollRate: $pollRate")} 
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
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (debug==true) {log.debug (descMap)}
    def map = [:]
    if (description?.startsWith("read attr -")) {
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

    def result = null
    if (map) {
        result = createEvent(map)
    }
    return result
}

def refresh() {
    def cmds = []
    cmds += zigbee.readAttribute(0x201, 0x0000) //Read LocalTemperature Attribute
    cmds += zigbee.readAttribute(0x201, 0x0010) //Read LocalTemperatureCalibration
    cmds += zigbee.readAttribute(0x201, 0x0012) //Read OccupiedHeatingSetpoint 
    cmds += zigbee.readAttribute(0x201, 0x001c) //Read SystemMode
    cmds += zigbee.readAttribute(0x0b04, 0x050b) // Read ActivePower 
    if (verbose==true) {log.info "refreshed"}
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
