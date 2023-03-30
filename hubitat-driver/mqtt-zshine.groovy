/*
 * Copyright 2023 Josh Harding
 * Licensed under the terms of the MIT licese, see LICENSE file
 */

import groovy.json.JsonSlurper

metadata {
    definition (name: "MQTT Zshine blinds", namespace: "amigo", author: "Josh Harding", importUrl: "https://raw.githubusercontent.com/TheAmigo/rfsend/hubitat-driver/mqtt-zshine.groovy") {
        capability "WindowShade"
    }

    preferences {
        input(name: "brokerIP", type: "string", title: "MQTT Broker", description: "Hostname or IP address of broker", defaultValue: "mqtt-broker", required: true, displayDuringSetup: true)
        input(name: "brokerPort", type: "string", title: "MQTT Broker's port", description: "TCP port number of broker", defaultValue: "1883", required: true, displayDuringSetup: true)
        input(name: "topic", type: "string", title:"MQTT topic", description: "This topic may contain slashes and will be sandwiched as cmd/{topic}/req", defaultValue: "rfsend", required: true, displayDuringSetup: true)
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true, required: false)
        input(name: "channel", type: "number", title: "Channel number", description: "For multiple blinds, each needs its own channel (use 0 for none)", range: "1..8", defaultValue: "0", required: false, displayDuringSetup: true)
    }
}

def sendCmd(String cmd) {
    topic = "cmd/${settings?.topic}/req"
 
    file_name = ""
    if (settings.channel > 0) {
        file_name = "${settings?.channel}_"
    }
    file_name = file_name + cmd
    payload = '{"button": "' + file_name + '"}'
    
    if (!interfaces.mqtt.isConnected()) {
        writeLog("Warning: MQTT not connected, retrying...")
        initialize()
    }
    try {
        writeLog("Info: publishing to ${topic}: ${payload}")
        interfaces.mqtt.publish(topic, payload, qos=2, false)
    } catch (Exception e) {
        writeLog("ERROR: while trying to publish: ${e}")
    }
}

def open() {
    writeLog("open()")
    sendCmd('up')
}

def close() {
    writeLog("close()")
    sendCmd('down')
}

def setPosition(Integer pos) {
    writeLog("setPosition(${pos})")
    // Only have 0, 25, 50, 75, and 100 as presets, so round to the nearest one.
    Integer target = (int)((12.5 + pos) / 25) * 25
    String cmd = "${target}pct"
    if (target == 0) {
        cmd = "up"
    } else if (target == 100) {
        cmd = "down"
    }
    sendCmd(cmd)
}

def startPositionChange(String dir) {
    writeLog("startPositionChange(${dir})")
    if (dir == "open") {
        sendCmd("up")
    } else if (dir == "close") {
        sendCmd("down")
    }
}

def stopPositionChange() {
    writeLog("stopPositionChange()")
    sendCmd("stop")
}

void initialize() {
    try {
        writeLog("Attempting connection to tcp://${settings?.brokerIp}:${settings?.brokerPort}")
        interfaces.mqtt.connect(
            "tcp://${settings?.brokerIP}:${settings?.brokerPort}",
            location.hubs[0].name.replaceAll("[^a-zA-Z0-9]","-") + ":" + location.hubs[0].hardwareID + ":" + device.name,
            null, null
        )
        
        // Listen for results
        interfaces.mqtt.subscribe("cmd/${settings?.topic}/resp", 2)
    } catch (Exception e) {
        writeLog("Exception when connecting MQTT: ${e}")
    }
}

def parse(String data) {
    payload = interfaces.mqtt.parseMessage(data).payload
    writeLog("Received: ${payload}")
    js = new JsonSlurper()
    parsed = js.parseText(payload)
    isSC = true // default to true if it's not specified
    if (parsed['isStateChange'] == false) {
        isSC = false
    }
    parsed.remove('isStateChange')
    parsed.each { entry -> 
        sendEvent(name: entry.key, value: entry.value, isStateChange: isSC)
    }
}

def mqttClientStatus(String msg) {
    writeLog("MQTT ClientStatus: ${msg}")
}

private writeLog(String msg) {
    if (logEnable) log.debug(device.name + ": " + msg)
}

// vim: et:ts=4:ai:smartindent
