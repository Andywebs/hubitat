/*
	Philips Hue Tap Dial
	2022-11-11 A.Webster
	    -First WIP
        - 1.02 - fixed configure/registration
*/

metadata {
    definition(name: "Philips Hue Tap Dial", namespace: "boundry", author: "Andrew Webster", component: true) {
        capability "Refresh"
        capability "Actuator"
        capability "Configuration"
        capability "PushableButton"
        capability "HoldableButton"
        capability "SwitchLevel"        
        capability "ChangeLevel"
        
        command "push", ["NUMBER"]
        command "hold", ["NUMBER"]
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FC00,1000", outClusters:"0019,0000,0003,0004,0006,0008,0005,1000", model:"RDM002", manufacturer:"Signify Netherlands B.V."
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true        
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(List description) { log.warn "parse(List description) not implemented" }

// raw, clusterId, sourceEndpoint, destinationEndpoint, options, messageType, dni, isClusterSpecific, 
// isManufacturerSpecific, manufacturerId, command, direction, data
def parse(String description) {
    if (logEnable) log.debug "parse description: ${description}" 

    def descMap = zigbee.parseDescriptionAsMap(description)
    def descriptionText
    def rawValue = descMap.raw
    def value = descMap.command
    def data = descMap.data
    switch (descMap.clusterInt){
        case 5:
            //if (logEnable) log.debug "Case 5: ${descMap.clusterInt}:${descMap.attrInt}${descMap.attrId} Value:${value}  Data:${data}"
            switch(data[2]) {
                case "00":
                    push(2)
                    //if (logEnable) log.debug "Button 2"
                break
                case "01":
                    push(1)
                    //if (logEnable) log.debug  "Button 1"                        
                break
                case "04":
                    push(4)
                    //if (logEnable) log.debug  "Button 4"                    
                break
                case "05":
                    push(3)
                    //if (logEnable) log.debug  "Button 3"                    
                break
                default:
                    if (logEnable) log.debug "Cluster 5: Unknown Button.   Value:${value}  Data:${data}"
                break
            }
        break
        case 8:
            if (logEnable) log.debug "Case 8: ${descMap.clusterInt}: Value:${value}  Data:${data}"
            if(data != null && data[1] != null)
            {
                if (data[1] == "FF")
                {
                    // For now, any held button sends out this command
                    hold(5)
                    return
                }
                else if(data[2] != null)
                {                
                    speed1 = Integer.parseInt(data[2],16)
                    speed2 = Integer.parseInt(data[1],16)
                    //speed3 = ((speed1 -4) * 16 + speed2) / 8
                    speed3 = speed1 * 16 + speed2
        
                    int speed4 = ((speed3 - 72) / 6) + 1
                    if(speed4 > 1)
                    {    
                        speed = speed4 * (speed4 - 1)
                    }            
                    else
                    {
                        speed = 1
                    }

                    // Dial
                    switch(data[0]) {
                        case "00":
                            // Increase
                            dialClockwise(speed)
                            return
                            //if (logEnable) log.debug "Increase ${speed} ( ${speed3}   ${speed1} )"
                        break
                        case "01":
                            // Decrease
                            dialCounterclockwise(speed)
                            return
                            //if (logEnable) log.debug  "Decrease ${speed} ( ${speed3}   ${speed1} )"                        
                        break
                    }
                }
            }
        break
        
        default:
            if (logEnable) log.debug "${descMap.clusterInt}:${descMap.attrInt}:${descMap.attrId}${rawValue}"
        break
    }
    
    if (descriptionText){
        if (txtEnable) log.info "${descriptionText}"
        sendEvent(name:"Undefined",value:value,descriptionText:descriptionText)
    }
}

void push(button){
    sendButtonEvent("pushed", button, "physical")
}

void hold(button){
    sendButtonEvent("held", button, "physical")
}

// Possibly do this programatically
//void doubleTap(button){
//    sendButtonEvent("doubleTapped", button, "digital")
//}

void dialClockwise(number){
    String descriptionText = "${device.displayName} [${number}, Clockwise]"
    if (txtEnable) log.info descriptionText

    dial(number)
}

void dialCounterclockwise(number){
    String descriptionText = "${device.displayName} [${number}, CounterClockwise]"
    if (txtEnable) log.info descriptionText

    dial(-number)    
}

void dial(number){    
    int dimmer = device.currentValue("level")
    if (logEnable) log.debug "Dial Value from:  ${dimmer}  by ${number}"

    setLevel( (dimmer + number) )
}

void sendButtonEvent(action, button, type){
    String descriptionText = "${device.displayName} button ${button} was ${action} [${type}]"
    if (txtEnable) log.info descriptionText
    sendEvent(name:action, value:button, descriptionText:descriptionText, isStateChange:true, type:type)
}

def setLevel(value, rate = null) {
    if (value == null) return
    Integer level = limitIntegerRange(value,0,100)
    if (level == 0) {
        sendEvent(name:"level", value:level, descriptionText:verb, isStateChange:true)
        off()        
        return
    }
    if (device.currentValue("switch") != "on") on()
    
    sendEvent(name:"level", value:level, descriptionText:"set to", isStateChange:true)
}

def configure(){
    log.warn "configure..."
    //runIn(1800,logsOff)
    sendEvent(name: "numberOfButtons", value: 5)
    sendEvent(name: "pushed", value: 1)
    sendEvent(name: "level", value: 0)
    sendEvent(name: "held", value: 5)

    state."${1}" = 0
    state."${2}" = 0
    runIn(5, "refresh")
    def cmds = [
            //bindings
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 200",
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}", "delay 200",
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0005 {${device.zigbeeId}} {}", "delay 200",

            //reporting
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 0x10 0 0xFFFF {}","delay 200",
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0 0x20 0 0xFFFF {}", "delay 200",
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0005 0 0x30 0 0xFFFF {}", "delay 200",        
    ] + refresh()    
    return cmds
}

void on() {
    parent?.componentOn(this.device)
}

void off() {
    parent?.componentOff(this.device)
}

void refresh() {
    parent?.componentRefresh(this.device)
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

Integer limitIntegerRange(value,min,max) {
    Integer limit = value.toInteger()
    return (limit < min) ? min : (limit > max) ? max : limit
}
