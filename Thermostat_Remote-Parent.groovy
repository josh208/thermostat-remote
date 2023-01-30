/*
 *  Advanced vThermostat Parent App
 *  Project URL: https://github.com/NelsonClark/Hubitat/tree/main/Apps/Advanced_vThermostat
 *  Copyright 2020 Nelson Clark
 *
 *  This app requires it's child app and device driver to function, please go to the project page for more information.
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
	name: "Thermostat Remote Manager",
	namespace: "josh208",
	author: "Josh McAllister",
	description: "Remotely control any thermostat.",
	category: "Green Living",
	iconUrl: "https://raw.githubusercontent.com/josh208/thermostat-remote/main/thermostat_remote.png",
	iconX2Url: "https://raw.githubusercontent.com/josh208/thermostat-remote/main/thermostat_remote.png",
	importUrl: "https://raw.githubusercontent.com/josh208/thermostat-remote/main/Thermostat_Remote-Parent.groovy",
	singleInstance: true
)

preferences {
	page(name: "Install", title: "Thermostat Remote Manager", install: true, uninstall: true) {
		section("Devices") {
		}
		section {
			app(name: "thermostats", appName: "Thermostat Remote Control", namespace: "josh208", title: "Add Thermostat Remote", multiple: true)
		}
	}
}

def installed() {
	log.debug "Installed"
	initialize()
}

def updated() {
	log.debug "Updated"
	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "Initializing; there are ${childApps.size()} child apps installed"
	childApps.each {child -> 
		log.debug "  child app: ${child.label}"
	}
}
