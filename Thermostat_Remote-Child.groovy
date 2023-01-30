/*
 *  Thermostat Remote Control
 *  Project URL: https://github.com/josh208/thermostat-remote/new/main
 *  Copyright 2022 Josh McAllister
 *
 *  This app requires it's parent app and device driver to function, please go to the project page for more information.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 */

definition(
	name: "Thermostat Remote Control",
	namespace: "josh208",
	author: "Josh McAllister",
	description: "Enables a remote control for Thermostat devices. Intended to be used with Tasmota Thermostat Remote descibed on the project page.",
	category: "Green Living",
	iconUrl: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat/Advanced_vThermostat-logo-small.png",
	iconX2Url: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat/Advanced_vThermostat-logo.png",
	importUrl: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat/Advanced_vThermostat-Child.groovy",
	parent: "josh208:Thermostat Remote Manager"
)


preferences {
	page(name: "pageConfig") // Doing it this way elimiates the default app name/mode options.
}


def pageConfig() {
	// Let's just set a few things before starting
	def displayUnits = getDisplayUnits()
	def hubScale = getTemperatureScale()
	installed = false
  def hubIP = getLocation().getHub().localIP
	def remoteIP = hex2IP(state.deviceID)
        
      // Display all options for a new instance of the Advanced vThermostat
	dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) {
		section() {
			label title: "Name of new Thermostat Remote Control app/device:", required: true
		}
		
		section("Configure Controller"){
			input "thermostat", "capability.thermostat", title: "Thermostat to control", multiple: false, required: true
			input "hubIP", "string", title: "This Hub's IP Address", required: true, defaultValue: hubIP
			input "deviceIP", "decimal", title: "Remote's IP Address", required: true, defaultValue: remoteIP
		}

		section("Log Settings...") {
			input (name: "logLevel", type: "enum", title: "Live Logging Level: Messages with this level and higher will be logged", options: [[0: 'Disabled'], [1: 'Error'], [2: 'Warning'], [3: 'Info'], [4: 'Debug'], [5: 'Trace']], defaultValue: 3)
			input "logDropLevelTime", "decimal", title: "Drop down to Info Level Minutes", required: true, defaultValue: 5
		}

	}
}


def installed() {
    
	// Set log level as soon as it's installed to start logging what we do ASAP
	int loggingLevel
	if (settings.logLevel) {
		loggingLevel = settings.logLevel.toInteger()
	} else {
		loggingLevel = 3
	}
	
	logger("trace", "Installed Running Thermostat Remote: $app.label")
	
	// Set DeviceID to Remote's IP (in Hex)
  if (settings.remoteIP.length() > 8) {
	  state.deviceID = ip2Hex(settings.remoteIP)
  } else {
    state.deviceID = settings.remoteIP
  }

	//Create Thermostat Remote device
	def thermostat
	def label = app.getLabel()
	logger("info", "Creating Thermostat Remote : ${label} with device id: ${state.deviceID}")
	try {
		remote = addChildDevice("josh208", "Thermostat Remote Device", state.deviceID, null, [label: label, name: label, completedSetup: true]) 
	} catch(e) {
		logger("error", "Error adding Thermostat Remote child ${label}: ${e}") 
	}
	initialize(remote)
}


def updated() {
	// Set log level to new value
	int loggingLevel
	if (settings.logLevel) {
		loggingLevel = settings.logLevel.toInteger()
	} else {
		loggingLevel = 3
	}

  if (settings.remoteIP != state.deviceID) {
    if (settings.remoteIP.length() > 8) {
	    state.deviceID = ip2Hex(settings.remoteIP)
    } else {
      state.deviceID = settings.remoteIP
    } 
  }
	logger("trace", "Updated Running vThermostat: $app.label")

	initialize(getRemote())
}


def uninstalled() {
	logger("info", "Child Device " + state.deviceID + " removed") // This never shows in the logs, is it because of the way HE deals with the uninstalled method?
	deleteChildDevice(state.deviceID)
}


//************************************************************
// initialize
//     Set preferences in the associated device and subscribe to the selected sensors and thermostat device
//     Also set logging preferences
//
// Signature(s)
//     initialize(thermostatInstance)
//
// Parameters
//     thermostatInstance : deviceWrapper
//
// Returns
//     None
//
//************************************************************
def initialize(remoteInstance) {
	logger("trace", "Initialize Running Thermostat Remote: $app.label")

	// First we need tu unsubscribe and unschedule any previous settings we had
	unsubscribe()
	unschedule()

	// Recheck Log level in case it was changed in the child app
	if (settings.logLevel) {
		loggingLevel = settings.logLevel.toInteger()
	} else {
		loggingLevel = 3
	}
	
	// Log level was set to a higher level than 3, drop level to 3 in x number of minutes
	if (loggingLevel > 3) {
		logger("trace", "Initialize runIn $settings.logDropLevelTime")
		runIn(settings.logDropLevelTime.toInteger() * 60, logsDropLevel)
	}

	logger("warn", "App logging level set to $loggingLevel")
	logger("trace", "Initialize LogDropLevelTime: $settings.logDropLevelTime")

  // Set device settings
	if (!installed) { remoteInstance.setHeatingSetpoint(heatingSetPoint) }
	if (!installed) { remoteInstance.setCoolingSetpoint(coolingSetPoint) }
	if (!installed) { remoteInstance.setThermostatThreshold(thermostatThreshold) }
	remoteInstance.setLogLevel(loggingLevel)
	remoteInstance.setThermostatMode(thermostatMode)

	// Subscribe to the new sensor(s) and device
	subscribe(remote, "TRUpdate", handleTRUpdate)
	subscribe(thermostat, handleThermostatUpdate, ["filterEvents": false])

	// Poll the remote
	//pollRemote()

	// Schedule every minute pollRemote
	runEvery1Minute(pollRemote())
}


//************************************************************
// getRemote
//     Gets current childDeviceWrapper from list of childs
//
// Signature(s)
//     getRemote()
//
// Parameters
//     None
//
// Returns
//     ChildDeviceWrapper
//
//************************************************************
def getRemote() {
	
	// Does this instance have a DeviceID
	if (!state.deviceID){
		
		//No DeviceID available what is going on, has the device been removed?
		logger("error", "getThermostat cannot access deviceID!")
	} else {
		
		//We have a deviceID, continue and return ChildDeviceWrapper
		logger("trace", "getRemote for device " + state.deviceID)
		def child = getChildDevices().find {
			d -> d.deviceNetworkId.startsWith(state.deviceID)
		}
		logger("trace","getRemote child is ${child}")
		return child
	}
}


//************************************************************
// logger
//     Wrapper function for all logging with level control via preferences
//
// Signature(s)
//     logger(String level, String msg)
//
// Parameters
//     level : Error level string
//     msg : Message to log
//
// Returns
//     None
//
//************************************************************
def logger(level, msg) {

	switch(level) {
		case "error":
			if (loggingLevel >= 1) log.error msg
			break

		case "warn":
			if (loggingLevel >= 2) log.warn msg
			break

		case "info":
			if (loggingLevel >= 3) log.info msg
			break

		case "debug":
			if (loggingLevel >= 4) log.debug msg
			break

		case "trace":
			if (loggingLevel >= 5) log.trace msg
			break

		default:
			log.debug msg
			break
	}
}


//************************************************************
// logsDropLevel
//     Turn down logLevel to 3 in this app/device and log the change
//
// Signature(s)
//     logsDropLevel()
//
// Parameters
//     None
//
// Returns
//     None
//
//************************************************************
def logsDropLevel() {
	def thermostat=getThermostat()
	
	app.updateSetting("logLevel",[type:"enum", value:"3"])
	thermostat.setLogLevel(3)
	
	loggingLevel = app.getSetting('logLevel').toInteger()
	logger("warn","App logging level set to $loggingLevel")
}


//************************************************************
// getTemperatureScale
//     Get the hubs temperature scale setting and return the result
// Signature(s)
//     getTemperatureScale()
// Parameters
//     None
// Returns
//     Temperature scale
//************************************************************
def getTemperatureScale() {
	return "${location.temperatureScale}"
	//return "F" //Temporary until we have all parts of it working in F
}


//************************************************************
// getDisplayUnits
//     Get the diplay units
// Signature(s)
//     getDisplayUnits()
// Parameters
//     None
// Returns
//     Formated Units String
//************************************************************
def getDisplayUnits() {
	if (getTemperatureScale() == "C") {
		return "°C"
	} else {
		return "°F"
	}
}

private String ip2Hex(String ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()
}

private String hex2IP(String hex)
{
    String ip= "";

    for (int j = 0; j < hex.length(); j+=2) {
        String sub = hex.substring(j, j+2);
        int num = Integer.parseInt(sub, 16);
        ip += num+".";
    }

    ip = ip.substring(0, ip.length()-1);
    return ip;
}
