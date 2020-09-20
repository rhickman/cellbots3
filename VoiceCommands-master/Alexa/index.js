'use strict';

var https = require('https');
var Alexa = require('alexa-sdk');
// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin');

// Amazon Developer Security Profile for logging in  with Amazon
var ALEXA_CLIENT_ID = "amzn1.application-oa2-client.9438b4f97aa8411eac8e91e441b43542";
var ALEXA_CLIENT_SECRET = "3e024a9f0fe0aa030002b0f567696518d49840ba77637a0186d709763fb8a0f5";
var ALEXA_REDIRECT_URL = "https://layla.amazon.com/api/skill/link/M39J7UCFY41IML";
var ALEXA_API_ENDPOINT = "embeddedassistant.googleapis.com";

// Log in to developer.amazon.com and navigate to the Alexa section by clicking Apps & Services and then clicking Alexa in the top navigation.
// Find the skill in the list and click "View Skill ID".
var APPLICATION_ID = "amzn1.ask.skill.c01bfa95-2935-48e4-97a4-7de73d66ae17";

// Robot Actions
const ROBOT_ACTION_START = "ROBOT_ACTION_START";
const ROBOT_ACTION_STOP = "ROBOT_ACTION_STOP";
const ROBOT_ACTION_GOTO = "ROBOT_ACTION_GOTO";
const ROBOT_ACTION_COME_HERE = "ROBOT_ACTION_COME_HERE";
const ROBOT_ACTION_ANIMATION = "ROBOT_ACTION_ANIMATION";

// User Information
let userId = null;

// Variables for storing data parameters
let currentRobotName = null;
let currentDestinationName = null;
let currentDuration = null;
let currentAnimation = null;
// For message generation
let firebaseSequenceID = 1;

////////////////////////////////////////////////////////////////////////////
// Authorization functions

/**
 * Find Firebase user with Access Token
 */
function findFirebaseUser(token) {
  return getAmazonEmail(token).then(email => {
    return admin.auth().getUserByEmail(email);
  }).catch(e => {
    return Promise.reject(e);
  });
}

/**
 * Find user email with Access Token
 */
// Make a HTTP request
function getAmazonEmail(accessToken) {
  var httpData = "";
  var url = 'https://api.amazon.com/user/profile?access_token=' + accessToken;
          
  return new Promise((resolve, reject) => {
    
    var request = https.request(url, function(response) {
      response.setEncoding('utf8');
      
      response.on('data', function receiveData(httpChunk) {
          httpData += httpChunk;
      });
      
      response.on('end', function gotData() {
          
          if(httpData == undefined) {
              reject("Retrieval of JSON failed");
          }
          else {
              var profile = JSON.parse(httpData);
              console.log("email: " + profile.email);
              resolve(profile.email);
          }
      });
    });
    request.end();
  });
}

  ////////////////////////////////////////////////////////////////////////////
  // Other functions
  
  /**
   * Generate UID
   */
  function guid() {
    function s4() {
      return Math.floor((1 + Math.random()) * 0x10000)
        .toString(16)
        .substring(1);
    }
    return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' +
        s4() + s4() + s4();
  }

  function lookUpCurrentRobot(userID, callback) {
    console.log("lookUpCurrentRobot()");
    console.log("User ID: " + userID);

    admin.database().ref('robots').child(userID).once('value', function (snapshot){
      var robots = snapshot.val();
      var robotName;
      var latestUpdateTime = 0;
      
      // Pick and store the name of the robot with latest update time.      
      // Case 1: User has only 1 robot
      // Case 2: User has multiple robots
      for (var robotID in robots) {
        if (robots[robotID].name && robots[robotID].last_update_time) {
          if (robots[robotID].last_update_time >= latestUpdateTime) {
            robotName = robots[robotID].name;
            latestUpdateTime = robots[robotID].last_update_time;
            console.log("Found robot named " + robotName + " with latest update time " + latestUpdateTime);
          }
        }
      }
      console.log("Using latest updated robot: " + robotName);
      callback(robotName);
    })
  }

  /**
   * Check if the robot exists for the current Firebase user
   */
  function lookUpRobotByName(userID, robotName, cb, that) {

    if (!robotName) {
      generateErrorMessage("Sorry, I couldn't find a robot with that name. Try repeating your command with the robot's name or register one on Cellbots's companion apps.", that);
      return;
    };

    admin.database().ref('robots').child(userID)
    .once('value', function (snapshot){
    
    var robots = snapshot.val();
    for (var robotID in robots) {
      if (robots[robotID].name) {
        if (robots[robotID].name.toLowerCase() == 
            robotName.toLowerCase()) {
            cb(robots[robotID]);
            return; // incase 2 robots with the same name
          }
      }
    }
    // Robot name not found
    generateErrorMessage("Sorry, no robot named " + robotName + " was found.", that);

    },function(errorObject){
      generateErrorMessage("Something went wrong! Looking up the robot's ID failed.", that);
    });
  }

  /**
   * Check if the POI exists for the current Firebase user
   */
  function lookUpPlaceIDByName(userID, mapID, placeName, cb, that) {

    if (!userID) {
      generateErrorMessage("It seems we're having some trouble trying to authenticate your user ID. Try signing out and in or try again later.", that);
      return;
    } else if (!mapID) {
      // This usually happens when the robot is not correctly configured on a map
      generateErrorMessage("It seems like there is a problem with the robot's map. Check to see if the map is valid and if the Cellbots robot is properly localized in it.", that);
      return;
    } else if (!placeName) {
      // The code fires this message when the user doesn't mention a place
      generateErrorMessage("An unexpected error has occurred when trying to load the specified place. Check and see if the place name is correct, then try again.", that);
      return;
    };

    admin.database().ref('objects').child(userID).child(mapID)
    .once('value', function(snapshot){
      var objects = snapshot.val();
      for (var objectID in objects) {
        if (objects[objectID].type == 'point_of_interest') {
          if ('variables' in objects[objectID]) {
            if ('name' in objects[objectID].variables) {
              if (objects[objectID].variables.name.toLowerCase() 
                                      == placeName.toLowerCase()) {
                cb(objects[objectID]);
                return; // in case of 2 robots with the same name  
              }
            }
          }
        }
      }
      // Place not found
      generateErrorMessage("I couldn't find the place " + placeName + ". Make sure you added it in Cellbots's Companion app.", that);

    },function(errorObject) {
      generateErrorMessage("An unexpected error has occurred while searching for the map on our database.", that);
    });
  }

  /*
   * Merge header with an error message
   */
  function generateErrorMessage(errorMessage, that) {
    console.error(errorMessage);
    // If there is an error let the user know
    that.emit(':tell', errorMessage);
    that.context.fail(errorMessage);
  }

////////////////////////////////////////////////////////////////////////////
// Handlers

var handlers = {
    
    'LaunchRequest': function () {
        // Check for required environment variables and throw spoken error if not present
        if (!ALEXA_CLIENT_ID){
          this.emit(':tell','ERROR! Client ID is not set in Lambda Environment Variables','ERROR! Client ID is not set in Lambda Environment Variables')
        }
        if (!ALEXA_CLIENT_SECRET){
          this.emit(':tell','ERROR! Client Secret is not set in Lambda Environment Variables','ERROR! Client Secret is not set in Lambda Environment Variables')
        }
        if (!ALEXA_REDIRECT_URL){
          this.emit(':tell','ERROR! Redirect URL is not set in Lambda Environment Variables','ERROR! Redirect URL is not set in Lambda Environment Variables')
        }
        if (!ALEXA_API_ENDPOINT){            
          this.emit(':tell','ERROR! API endpoint is not set in Lambda Environment Variables','ERROR! API Endpoint is not set in Lambda Environment Variables')
        }
                
        if (!this.event.session.user.accessToken) {
                      
          this.emit(':tellWithLinkAccountCard', "You must link your Amazon account to use this skill. Please log in at alexa dot amazon dot com and link your account there.");
        } 
        else {
          this.emit(':ask', "Hi, Cellbots here! I can give commands to your robot such as start, stop, go to, and come here. What would you like your robot to do?");      
        };
    },

    'SearchIntent': function (action) {
        
        console.log('Starting Search Intent')
            
        // Check for required environment variables and throw spoken error if not present
        
        if (!ALEXA_CLIENT_ID){
            this.emit(':tell','ERROR! Client ID is not set in Lambda Environment Variables','ERROR! Client ID is not set in Lambda Environment Variables')
        }
        if (!ALEXA_CLIENT_SECRET){
            this.emit(':tell','ERROR! Client Secret is not set in Lambda Environment Variables','ERROR! Client Secret is not set in Lambda Environment Variables')
        }
        if (!ALEXA_REDIRECT_URL){
            this.emit(':tell','ERROR! Redirect URL is not set in Lambda Environment Variables','ERROR! Redirect URL is not set in Lambda Environment Variables')
        }
        if (!ALEXA_API_ENDPOINT){
            this.emit(':tell','ERROR! API endpoint is not set in Lambda Environment Variables','ERROR! API Endpoint is not set in Lambda Environment Variables')
        }
        
        // Check for optional environment variables otherwise set to defaults
        var DEBUG_MODE = process.env.DEBUG_MODE;
        var ACCESS_TOKEN = this.event.session.user.accessToken;

        // Check whether we have a valid authentication token
        if (!ACCESS_TOKEN) { 
            // We haven't so create an account link card
            console.log("Asking for access token");
            this.emit(':tellWithLinkAccountCard', "You must link your Google account to use this skill. Please link your account in the Alexa app.");
        
        } else {

            var that = this;  // Maintain context reference

            findFirebaseUser(that.event.session.user.accessToken)
            .then(user => {

              userId = user.uid;
              console.log("User Id: " + userId);   

              if (userId) {

                  var slotsPath = that.event.request.intent.slots;
                  if (slotsPath.robotnameslot) currentRobotName = slotsPath.robotnameslot.value;
                  if (slotsPath.placeslot) currentDestinationName = slotsPath.placeslot.value;
                  if (slotsPath.durationslot) currentDuration = slotsPath.durationslot.value;
                  if (slotsPath.animationslot) currentAnimation = slotsPath.animationslot.value;

                  if (currentRobotName) {
                    console.log("Robot Name was valid. Executing action.");
                    executeAction(that, action);
                  } else {
                    console.log("Robot name was invalid. Looking up current robot.");
                    lookUpCurrentRobot(userId, function(robotName) {
                      if (robotName) {
                        currentRobotName = robotName;
                        executeAction(that, action);
                      } else {
                        generateErrorMessage("No working robots were found on your account. Try adding one in the RobotApp and try again later.", that);
                        return;
                      }
                    });
                  }
              }

            }).catch(e => {
                generateErrorMessage("It seems like the sign in process failed. Please sign out and sign back in.", that);
            });
        };
    },
    
    'Unhandled': function() {
        console.log('Unhandled event');
        var speechOutput = "Sorry, I didn't quite get that. Could you repeat that?";
        var repromptSpeech = "Sorry, I didn't quite get that either. Currently, I can give commands to your Cellbots robot such as start, stop, go to, and come here. What would you like your robot to do?";

        this.emit(':ask', speechOutput, repromptSpeech);
    },
    
    'AMAZON.StopIntent' : function() {
        // State Automatically Saved with :tell
        console.log('Stop Intent')
        this.emit(':tell', `Goodbye.`); 
    },
    'AMAZON.CancelIntent' : function() {
        // State Automatically Saved with :tell
        console.log('Cancel Intent')
        this.emit(':tell', `Goodbye.`);
    },
        
    'SessionEndedRequest': function() {   
        console.log(`Session has ended with reason ${this.event.request.reason}`)
        this.emit(':tell', `Goodbye.`);
    },

    'AMAZON.HelpIntent': function() {
        console.log('Help requested');
        var helpOutput = "Thanks for using the Cellbots robot! To get started, try calling the robot by its name and giving it voice commands such as start, stop, go to, and come here. For example, if giving a command after launching Cellbots, try saying Alexa, tell Robot to go to the kitchen, where Robot is the name of your robot. To launch the skill and give a command in one go, try saying something like Alexa, ask Cellbots to ask Robot to go to the kitchen. What command would you like to give to your Cellbots robot?";
        var repromptSpeech = "Sorry, I didn't quite get that. Did you try including your robot's name and command in the same phrase?";
        this.emit(':ask', helpOutput, repromptSpeech);
    },

    'StartRobotIntent': function() {
        console.log("Calling SearchIntent with " + ROBOT_ACTION_START);
        this.emit('SearchIntent', 'ROBOT_ACTION_START');
    },

    'StopRobotIntent': function() {
        console.log("Calling SearchIntent with " + ROBOT_ACTION_STOP);
        this.emit('SearchIntent', 'ROBOT_ACTION_STOP');
    },

    'GotoRobotIntent': function() {
        console.log("Calling SearchIntent with " + ROBOT_ACTION_GOTO);
        this.emit('SearchIntent', 'ROBOT_ACTION_GOTO');
    },

    'ComeHereIntent': function() {
        console.log("Calling SearchIntent with " + ROBOT_ACTION_COME_HERE);
        this.emit('SearchIntent', 'ROBOT_ACTION_COME_HERE');
    },

    'AnimationIntent': function() {
        console.log("Calling SearchIntent with " + ROBOT_ACTION_ANIMATION);
        this.emit('SearchIntent', 'ROBOT_ACTION_ANIMATION');
    }
};

////////////////////////////////////////////////////////////////////////////
// Execute the Robot Action

/**
 * Executes the given action on the robot.
 */
function executeAction(context, action) {
  console.log("Action: " + action);
  switch(action) {
    case ROBOT_ACTION_START:
      startIntentHandler(userId, currentRobotName, context);
      break;
    case ROBOT_ACTION_STOP:
      stopIntentHandler(userId, currentRobotName, context);
      break;
    case ROBOT_ACTION_GOTO:
      goToIntentHandler(userId, currentRobotName, currentDestinationName, context);
      break;
    case ROBOT_ACTION_COME_HERE:
      var location = "alexa";
      comeHereIntentHandler(userId, currentRobotName, location, context);
      break;
    case ROBOT_ACTION_ANIMATION:
      animationIntentHandler(userId, currentRobotName, currentAnimation, context);
      break;
    default:
      generateErrorMessage("Sorry, the robot could not execute the given action.", context);
  }
}

////////////////////////////////////////////////////////////////////////////
// Start & stop

  /**
   * Send "Start" or "Stop" command to Firebase
   */
  function sendRobotMovementCommand(userID, robotID, action) {
    console.log("sendRobotMovementCommand()");
    // get time stamp from server
    const dbTime = admin.database.ServerValue.TIMESTAMP;
    var writeObject = {
      'uuid': guid(),
      'timestamp': dbTime,
      'sequence': firebaseSequenceID++,
      // Command payload
      'value': action,
      'was_executed': false,
      'sNextSequenceNumber': firebaseSequenceID
    };

    var refString = "robot_goals/" + userID + "/" + robotID + "/curret_command";
    admin.database().ref(refString).child(writeObject.uuid).set(writeObject);

    console.log('Start/stop order executed correctly');
  }

  /**
   * Start Intent Handler
   */
  function startIntentHandler(userID, robotName, that) {
    console.log("Executing START intent.");
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
      const robotID = robotData.uuid;
      sendRobotMovementCommand(userID, robotID, "start_command");
      that.emit(':tell', "The robot " + robotName + " is starting");
    }, that);
  }

  /**
   * Stop Intent Handler
   */
  function stopIntentHandler(userID, robotName, that) {
    console.log("Executing STOP intent.");
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
        const robotID = robotData.uuid;
        sendRobotMovementCommand(userID, robotID, "stop_command");
        that.emit(':tell', "The robot " + robotName + " is stopping");
    }, that);
  }


  ////////////////////////////////////////////////////////////////////////////
  // Go to

  /**
   * Go To action
   */
  function goToIntentHandler(userID, robotName, destinationName, that) {
    console.log("Executing GOTO intent.");
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    console.log("destinationName = " + destinationName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
      const robotID = robotData.uuid;
      const mapID = robotData.map;
      // Looks for POI name
      lookUpPlaceIDByName(userID, mapID, destinationName, function(destinationID) {
        // Send command to Firebase
        sendRobotToPlaceCommand(userID, robotID, "drivePOI", destinationID.uuid);
        that.emit(':tell', "The robot " + robotName + " is going to the " + destinationName);
      }, that);
    }, that);
  }

  /**
   * Send "Go To" command to Firebase
   */
  function sendRobotToPlaceCommand(userID, robotID, action, destinationID) {
    console.log("sendRobotToPlaceCommand()");
    // get time stamp from server
    const dbTime = admin.database.ServerValue.TIMESTAMP;
    var writeObject = {
      'uuid': guid(),
      'sequence': firebaseSequenceID++,
      'timestamp': dbTime,
      'name': action,
      'version': "1.0.0",
      'parameters': {"target": destinationID}, // destination
      'priority': 100
    };

    var refString = "robot_goals/" + userID + "/" + robotID + "/goals";
    admin.database().ref(refString).child(writeObject.uuid).set(writeObject);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Come here

  /**
   * Come Here Intent Handler
   */
  function comeHereIntentHandler(userID, robotName, destinationName, that) {
    console.log("Executing COME HERE intent.");
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    console.log("destinationName = " + destinationName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
      const robotID = robotData.uuid;
      const mapID = robotData.map;
      // Looks for POI name
      lookUpPlaceIDByName(userID, mapID, destinationName, function(destinationID) {
        // Come here sends the robot to a POI named "echo"
        sendRobotToPlaceCommand(userID, robotID, "drivePOI", destinationID.uuid);

        that.emit(':tell', "The robot " + robotName + " is going to Echo's position");
      }, that);
    }, that);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Animations

  /**
   * Animation action
   */
  function animationIntentHandler(userID, currentRobotName, animationName, that) {

      if (!animationName) {
        generateErrorMessage("Sorry, this action is not available yet. You will receive news from Cellbots when it will be.", that);
      }

      lookUpRobotByName(userID, currentRobotName, function(robotData) {
          const robotID = robotData.uuid;
          sendAnimationCommand(userID, robotID, "animation", animationName, that);
      }, that); 
   }

   /**
   * Send "Animation" command to Firebase
   */
  function sendAnimationCommand(userID, robotID, animation, that) {
    // get time stamp from server
    const dbTime = admin.database.ServerValue.TIMESTAMP;
    var writeObject = {
      'name': "animation",
      'parameters': {"animation": animation}, // animation
      'timestamp': dbTime,
      'version': "1.0.0",
      'priority': 100
    };

    var refString = "robot_goals/" + userID + "/" + robotID + "/goals";
    admin.database().ref(refString).child(writeObject.uuid).set(writeObject);

    that.emit(':tell', currentRobotName + " is executing the " + currentDestinationName + " animation");
  }

////////////////////////////////////////////////////////////////////////////
// AWS Lambda function

exports.handler = function(event, context, callback){
    var alexa = Alexa.handler(event, context);
    alexa.APP_ID = APPLICATION_ID;
   
    context.callbackWaitsForEmptyEventLoop = false;  //<---Important

    if(admin.apps.length == 0) {   // <---Important!!! In lambda, it will cause double initialization.
        console.log("Initializing Firebase");
        // initialize the SDK to talk to firebase
        admin.initializeApp({
          credential: admin.credential.cert("config.json"),
          apiKey: "AIzaSyBuaO6FX8Vg-baTbtZrQfSjY04Bc5FyAus",
          authDomain: "cellbots-robot-app.firebaseapp.com",
          databaseURL: "https://cellbots-robot-app.firebaseio.com",
          storageBucket: "cellbots-robot-app.appspot.com",
        });
    }

    alexa.registerHandlers(handlers);
    alexa.execute();
};

