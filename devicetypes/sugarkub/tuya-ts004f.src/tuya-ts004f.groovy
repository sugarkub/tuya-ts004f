/**
 *	Tuya TS004F in Scene Switch mode DHT for SmartThings
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 * 
 *  Test DH based on "Zemismart Button", namespace: "SangBoy", author: "YooSangBeom"
 * 
 * rev 1.0 2021-05-08 kkossev - inital test version
 * rev 1.1 2021-06-06 kkossev - added 'held' and 'up_hold' (release) events decoding for the 2 right buttons (3 and 4)
 * rev 1.2 2021-06-10 kkossev - changed the buttons numbers to match other similar Scene switches ( T S 0 0 4 4 for example):
 * 								Button 1 is the lower left key that must be pressed ~10 seconds to start the zigbee pairing process
 * rev 2.0 2021-10-31 kkossev - initialize TS004F in Scene mode during zigbee pairing. Process both Dimmer and Scene mode keypresses!; added Preferencies:	logEnable, txtEnable, reverseButton
 * rev 2.1 2021-11-06 kkossev - optimized configuration; removed reverseButton settings; debug logging is now true by default
 * rev 2.2 2021-11-06 kkossev - ... and one more (initialization) for luck!
 * rev 2.3 2021-11-06 kkossev - ... and initialize again on every Dimmer Mode event! (hopefully happens just once)
 * rev 2.4 2021-11-16 kkossev - EP1 binding bug fix; even more optimized configuration!
 * rev 2.5 2021-11-19 kkossev - fixed bug in createChildButtonDevices();  removed preferences section
 *     If pressing a button does nothing and you don't see any debug logs on SmartThings Groovy IDE 'Live Logging' page, try the following: 
 *         Press simultaneously the two buttons on the right row (some TS004F switches have 2 dots and 4 dots engraved on these buttons) for about 5-6 seconds
 *         until the led of the bottom left key (3-dot-button) lights up for a split second. This key sequence circles between 'dimmer' and 'scene control' modes.
 *
 * rev 2.6 2021-11-19 kkossev - added 'Reverse button order' setting back (off by default)
 * ---------                    ---------
 * ! 4 ! 3 !                    ! 1 ! 2 !
 * ---------                    ---------
 * ! 1 ! 2 !                    ! 3 ! 4 !
 * --------- : default          --------- : 'Reverse button order' setting ON 
 *
 */

import groovy.json.JsonOutput
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType

metadata {
  definition (name: "Powered by Tuya TS004F", namespace: "smartthings", author: "kkossev", ocfDeviceType: "x.com.st.d.remotecontroller", mcdSync: true, runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Zigbee") {
  	capability "Refresh"
	  capability "Button"
      capability "Momentary"
	  capability "Health Check"
      
      command "configure"
      command "refresh"
      command "initialize"
      
 	  fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_xabckq1v", model: "TS004F", deviceJoinName: "Powered by Tuya 4 Button TS004F", mnmn: "SmartThings", vid: "generic-4-button" 
	}

	tiles(scale: 2)
	{  
      multiAttributeTile(name: "button", type: "generic", width: 2, height: 2) 
      {
         tileAttribute("device.button", key: "PRIMARY_CONTROL") 
         {
            attributeState "pushed", label: "Pressed", icon:"st.Weather.weather14", backgroundColor:"#53a7c0"
            attributeState "double", label: "Pressed Twice", icon:"st.Weather.weather11", backgroundColor:"#53a7c0"
            attributeState "held", label: "Held", icon:"st.Weather.weather13", backgroundColor:"#53a7c0"
         }
      }
      valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) 
      {
         state "battery", label: '${currentValue}% battery', unit: ""
      }
      standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) 
      {
         state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
      }

      main(["button"])
      details(["button","battery", "refresh"])
	}    
    
    preferences {
        input (name: "reverseButton", type: "bool", title: "Reverse button order", defaultValue: false)
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.debug "description is $description"
	def event = zigbee.getEvent(description)
	def result = []
	def buttonNumber = 0
	if (event) {
		sendEvent(event)
        log.debug "sendEvent $event"
        return
	}
	if (description?.startsWith("catchall:")) {
    	def descMap = zigbee.parseDescriptionAsMap(description)            
    	log.debug "catchall descMap: $descMap"
    	def buttonState = "unknown"
    	// Scene mode command "FD"
    	if (descMap.clusterInt == 0x0006 && descMap.command == "FD") {
			if (descMap.sourceEndpoint == "03") {
                buttonNumber = reverseButton==true ? 3 : 1
        	}
        	else if (descMap.sourceEndpoint == "04") {
                buttonNumber = reverseButton==true  ? 4 : 2
        	}
        	else if (descMap.sourceEndpoint == "02") {
                buttonNumber = reverseButton==true  ? 2 : 3
        	}
        	else if (descMap.sourceEndpoint == "01") {
            	buttonNumber = reverseButton==true  ? 1 : 4
        	}    
			state.lastButtonNumber = buttonNumber
       		if (descMap.data[0] == "00")
           		buttonState = "pushed"
       		else if (descMap.data[0] == "01")
           		buttonState = "double"
       		else if (descMap.data[0] == "02")
           		buttonState = "held"
       		else {
           		log.warn "unkknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"
 	       		return null 
        	}
    	}
    	// TS004F in Dimmer mode
    	else {
    		if (descMap.clusterInt == 0x0008 && descMap.command == "01" && descMap.data[0] == "00") {
                buttonNumber = reverseButton==true  ? 2 : 3
      			buttonState = "held"
    		}
    		else if (descMap.clusterInt == 0x0008 && descMap.command == "01" && descMap.data[0] == "01") {
                buttonNumber = reverseButton==true  ? 4 : 2
      			buttonState = "held"
    		}
    		else if (descMap.clusterInt == 0x0006 && descMap.command == "00" ) {
                buttonNumber = reverseButton==true ? 3 : 1
    			buttonState = "pushed"
    		}
    		else if (descMap.clusterInt == 0x0006 && descMap.command == "01") {
            	buttonNumber = reverseButton==true  ? 1 : 4
    			buttonState = "pushed"
    		}
    		else if (descMap.clusterInt == 0x0008 && descMap.data[0] == "00") {
                buttonNumber = reverseButton==true  ? 2 : 3
    			buttonState = "pushed"
    		}
    		else if (descMap.clusterInt == 0x0008 && descMap.data[0] == "01") {
                buttonNumber = reverseButton==true  ? 4 : 2
    			buttonState = "pushed"
    		}
    		else if (descMap.clusterInt == 0x0008 && descMap.command == "03") {
      			buttonNumber = state.lastButtonNumber
      			buttonState = "up_hold"			// was "up_hold"
			}
	    	else {
				log.warn "DID NOT PARSE MESSAGE for description : $description"
				log.debug zigbee.parseDescriptionAsMap(description)
       			return null
  			}
            if (descMap.clusterInt == 0x0008 || descMap.clusterInt == 0x0006 && descMap.command != "FD") {
                    switchToSceneMode();	// try again to switch into Scene mode!
            }
		}
    	//
   		state.lastButtonNumber = buttonNumber
		if (buttonState in ["pushed","double","held","up_hold"] &&  buttonNumber != 0) {
	   		def descriptionText = "button $buttonNumber was $buttonState"
	   		event = [name: "button", value: buttonState, data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, displayed: true]
            sendButtonEvent(buttonNumber, buttonState)
   		}	
    } // if catchall
	if (event) {
	    log.info "Creating event: ${event}"
		result = createEvent(event)
	} 
  	return result
}


private sendButtonEvent(buttonNumber, buttonState) 
{
   def child = childDevices?.find { channelNumber(it.deviceNetworkId) == buttonNumber }
   if (child)
   {
      def descriptionText = "$child.displayName was $buttonState" // TODO: Verify if this is needed, and if capability template already has it handled
      log.debug "child $child"
      child?.sendEvent([name: "button", value: buttonState, data: [buttonNumber: 1], descriptionText: descriptionText, isStateChange: true])
   } 
   else 
   {
      log.debug "Child device $buttonNumber not found!"
      log.warn "Creating child devices again ..."
      installed()
   }
}


/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return refresh()
}

def refresh() {
	log.debug "refresh..."
	//zigbee.onOffRefresh() + zigbee.onOffConfig()
}

def switchToSceneMode()
{
	log.trace "switchToSceneMode..."
	List cmd  = zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01)
    sendHubCommand(cmd, 200)
}

def switchToDimmerMode()
{
	 log.trace "switchToDimmerMode..."
     List cmd  = zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00)  
     sendHubCommand(cmd, 200)
}


def configure() {
[
	"raw 0x0000  {10 00 00 04 00 00 00 01 00 05 00 07 00 FE FF}", "send 0x${device.deviceNetworkId} 1 1", "delay 50",
	"st rattr 0x${device.deviceNetworkId} 1 0x0006 0x8004","delay 20",
	"st rattr 0x${device.deviceNetworkId} 1 0xE001 0xD011","delay 20",
	"raw 0x0000  {10 00 00 04 00 20 00 21}", "send 0x${device.deviceNetworkId} 1 1", "delay 25",
	"st wattr 0x${device.deviceNetworkId} 1 0x0006 0x8004 0x30 {01}","delay 20",
	"st rattr 0x${device.deviceNetworkId} 1 0x0006 0x8004","delay 20",
	"zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}","delay 20",
	"zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}","delay 20",
	"zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0006 {${device.zigbeeId}} {}","delay 20",
	"zdo bind 0x${device.deviceNetworkId} 0x04 0x01 0x0006 {${device.zigbeeId}} {}","delay 20"
]
}

    
private channelNumber(String dni) 
{
   dni.split(":")[-1] as Integer
}
    

private getButtonName(buttonNum) {
	return "${device.displayName} " + buttonNum
}


private void createChildButtonDevices(numberOfButtons) {
	log.debug "Creating $numberOfButtons child buttons"
	for (i in 1..numberOfButtons) {
		def child = childDevices?.find { it.deviceNetworkId == "${device.deviceNetworkId}:${i}" }
		if (child == null) {
			log.debug "..Creating child $i"
			child = addChildDevice("smartthings", "Child Button", "${device.deviceNetworkId}:${i}", device.hubId,
				[completedSetup: true, label: getButtonName(i),
				 isComponent: true, componentName: "button$i", componentLabel: "Button ${i}"])
		}
		child.sendEvent(name: "supportedButtonValues", value: ["pushed", "double", "held", "up_hold"].encodeAsJSON(), displayed: false)
		child.sendEvent(name: "numberOfButtons", value: 1, displayed: false)
		child.sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], displayed: false)
	}
	state.oldLabel = device.label
}


def installed() 
{
	initialize()
    log.info "installed ..."
    
    def numberOfButtons = 4
    createChildButtonDevices(numberOfButtons)
    
    sendEvent(name: "supportedButtonValues", value: ["pushed", "double", "held", "up_hold"].encodeAsJSON(), displayed: false)
    sendEvent(name: "numberOfButtons", value: numberOfButtons , displayed: false)
    
    // Initialize default states
    numberOfButtons.times 
    {
        sendEvent(name: "button", value: "pushed", data: [buttonNumber: it+1], displayed: false)
    }
    // These devices don't report regularly so they should only go OFFLINE when Hub is OFFLINE
    sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "zigbee", scheme:"untracked"]), displayed: false)
    
}

def initialize() {
	//state.lastButtonNumber = 0
 	configure()
    // ... and one more for luck! 
    
    def now = new Date()
	def runTime = new Date(now.getTime() + (5 * 1000))
	runOnce(runTime, configure)
    log.debug "scheduled runOnce()..."

}

def updated() 
{
   log.debug "updated() childDevices $childDevices"
   if (childDevices && device.label != state.oldLabel) 
   {
      childDevices.each 
      {
         def newLabel = getButtonName(channelNumber(it.deviceNetworkId))
	 	 it.setLabel(newLabel)
      }
      state.oldLabel = device.label
    }   
    log.debug "updated() reverseButton is ${reverseButton}"
}
