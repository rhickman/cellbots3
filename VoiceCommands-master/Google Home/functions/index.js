'use strict';

// Comment out the following instruction to see more debugging logs
//process.env.DEBUG = 'actions-on-google:*';

const App = require('actions-on-google').DialogflowApp;
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const requestLib = require('request');
const cors = require('cors')();

// Authentication Client Information (from Auth0)
//
// Note: This project's Google client information from the Google Cloud Console was added to Auth0 to 
// connect the two providers. By using Auth0's client information, this allows us to use Auth0 for handling 
// the authentication process and providing the approved user access to Google's APIs.
// 
// Reference: https://auth0.com/docs/connections/social/google#3-enable-the-connection-in-auth0
const DOMAIN = "cellbots-ai.auth0.com";
const CLIENT_ID = "ZYqLm1MwEvYhQ5OJpqKWeZoQpRhSPux0";
const CLIENT_SECRET = "WPFjM9qbjyFOTwc77WZH7h4ptKGLFiqYaXcR30hllY096XuSLmHRAPAKlo7TTFLq";
const REDIRECT_URL = "";

// Auth0 Client
var AuthenticationClient = require('auth0').AuthenticationClient;
var auth0Client = new AuthenticationClient({
  domain: DOMAIN,
  clientId: CLIENT_ID
});

// Robot Actions
const ROBOT_ACTION_START = 'Robot.START';
const ROBOT_ACTION_STOP = 'Robot.STOP';
const ROBOT_ACTION_GOTO = 'Robot.GOTO';
const ROBOT_ACTION_COME_HERE = 'Robot.COME_HERE';
const ROBOT_ACTION_ANIMATION = 'Robot.ANIMATION';

// Variables for storing data parameters
let currentRobotName = null;
let currentDestinationName = null;
let currentDuration = null;
let currentAnimation = null;
// For message generation
let firebaseSequenceID = 1;

// initialize the SDK to talk to firebase
admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  apiKey: "AIzaSyBuaO6FX8Vg-baTbtZrQfSjY04Bc5FyAus",
  authDomain: "cellbots-robot-app.firebaseapp.com",
  databaseURL: "https://cellbots-robot-app.firebaseio.com",
  storageBucket: "cellbots-robot-app.appspot.com",
});

// Twilio
const TWILIO_ACCOUNT_SID = "AC0e267eb539e2fb2b44d8a455b6b3b030";
const TWILIO_AUTH_TOKEN = "3dc8c7a8d619df1e760b6971ed74ed00";

const validateFirebaseIdToken = (req, res, next) => {
    cors(req, res, () => {
	if (!req.headers.authorization || req.headers.authorization.split('Bearer ').length <= 1) {
	    console.log("Unauthenticated request: " + req.headers.authorization);
	    res.status(403).send('Unauthorized');
	    return;
	}
	const idToken = req.headers.authorization.split('Bearer ')[1];
	admin.auth().verifyIdToken(idToken).then(decodedIdToken => {
	    console.log('ID Token correctly decoded', decodedIdToken);
	    req.user = decodedIdToken;
	    next();
	}).catch(error => {
	    console.error('Error while verifying Firebase ID token:', error);
	    res.status(403).send('Unauthorized');
	});
    });
};

exports.getWebRTCIceServers = functions.https.onRequest((request, response) => {
    validateFirebaseIdToken(request, response, () => {
	var post_data = "";
	
	var post_options = {
	    url: 'https://api.twilio.com/2010-04-01/Accounts/' + TWILIO_ACCOUNT_SID + '/Tokens.json',
	    body: '',
	    headers: {
		'Content-Type': 'application/x-www-form-urlencoded',
	    'Content-Length': Buffer.byteLength(post_data),
		'Authorization': 'Basic ' + new Buffer(TWILIO_ACCOUNT_SID + ':' + TWILIO_AUTH_TOKEN).toString("base64")
	    }
	};
	
	var post_req = requestLib.post(post_options, function (error, response2, body) {
	    response.setHeader('Content-Type', 'application/json');
	    console.log('Response: ' + body);
	    response.status(200);
	    response.send(body);
	});
	post_req.write(post_data);
	post_req.end();
    });
});

exports.googleHome = functions.https.onRequest((request, response) => {
  // To manage all the intents we create an object which has the
  // collection of intents and it’s callback functions.
  const ACTIONS = {
      'Robot.START': {callback: makeRobotActionHandler(startIntentHandler)},
      'Robot.STOP': {callback: makeRobotActionHandler(stopIntentHandler)},
      'Robot.GOTO': {callback: makeRobotActionHandler(moveRobot)},      
      'Robot.COME_HERE': {callback: makeRobotActionHandler(comeHereHandler)},
      'Robot.ANIMATION': {callback: makeRobotActionHandler(animationIntentHandler)},      
      'input.unknown': {callback: defaultFallbackIntentHandler},
      'input.welcome': {callback: defaultWelcomeIntentHandler}
  };

  // Assign agent parameters
  let robotGivenName = request.body.result.parameters.RobotGivenName;
  let robotLastName = request.body.result.parameters.RobotLastName;
  console.log("Given Name: " + robotGivenName + ", Last Name: " + robotLastName);

  if (robotLastName) {
    // Full Name (given + last)
    currentRobotName = robotGivenName + " " + robotLastName;    
  } else {
    // Invalid last name. So, use Given Name only.
    currentRobotName = robotGivenName;
  }
  console.log("Full Name: " + currentRobotName);

  currentDestinationName = request.body.result.parameters.Place;
  currentDuration = request.body.result.parameters.duration;
  currentAnimation = request.body.result.parameters.Animation;

  // What the user says...
  console.log('HEARD > ' + request.body.result.resolvedQuery);

  // Maps the incoming intent to its corresponding handler function.
  let app = new App({request: request, response: response}); 
  let intent = app.getIntent();
  
  try {
    // In order to return a response we’ll get the data and build the statement.
    app.handleRequest(ACTIONS[intent].callback);
  } catch(e) {
    generateErrorHeader("Uh oh! Something broke trying to handle the request. Please try again later.");
  }

  ////////////////////////////////////////////////////////////////////////////
  // Authorization functions

  /**
   * Find Firebase user with Access Token
   */
  function findFirebaseUser(token) {
    console.log("Access Token: " + token);
    return findUserEmail(token).then(email => {
      return admin.auth().getUserByEmail(email);
    }).catch(e => {
      return Promise.reject(e);
    });
  }

  /**
   * Find user email with Access Token
   */
  function findUserEmail(token) {
    return new Promise((resolve, reject) => {
      auth0Client.getProfile(token, function(error, userInfo) {
        if (error) {
          console.log("Something broke trying to retrieve the user\'s email using auth0.");
        } else {
          console.log("Success! User Info: " + JSON.stringify(userInfo, null, 4));
          resolve(userInfo.email);
        }
      });
    });
  }

  ////////////////////////////////////////////////////////////////////////////

  /**
   * Handler for execution the actions
   */
  function makeRobotActionHandler(action) {
    return function(app) {
      
      // If the Companion app fires up this Cloud Function, it will not stop.
      if (app.getUser() == null) {
        console.log("User could not be identified. Closing app.");
        generateSpeechHeader("Sorry, I could not identify your user account. Either sign in again or try again later.");
        generateErrorHeader("Sorry, I could not identify your user account. Either sign in again or try again later.");
        return;
      };

      // Log user's info for debugging purposes.
      console.log("User: " + JSON.stringify(app.getUser(), null, 4));

      // Accessing user information (UID)
      findFirebaseUser(app.getUser().accessToken).then(user => {
        admin.database().ref().child('robots').child(user.uid)
          .once('value', function(snapshot) {
            var ac = request.body.result.action;
            console.log("ACTION: " + ac);

            // Execute action using the user's current robot
            // Length 1 is due to the possible case of <empty given name> + " " + <empty last name>.
            if (currentRobotName && currentRobotName.length > 1) {
              executeAction(ac, action, user, generateErrorHeader);
            } else {
              lookUpCurrentRobot(user.uid, function(robotName) {
                if (robotName) {
                  currentRobotName = robotName;
                  executeAction(ac, action, user, generateErrorHeader);
                } else {
                  generateErrorHeader("No working robots were found on your account. Try adding one in the RobotApp and try again later.");
                  return;
                }
              });
            };
        }, function (errorObject) {
          generateErrorHeader("An unexpected error occured when trying to process the user's information.");
        });

      }).catch(e => {
        generateErrorHeader("It seems like you didn't complete the sign in process correctly. Try signing in with the correct account and password, or create a new account.");
      });
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
  function startIntentHandler(userID, robotName) {
    console.log('Start intent');
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
      const robotID = robotData.uuid;
      sendRobotMovementCommand(userID, robotID, "start_command");
      generateSpeechHeader(robotName + " is starting");
    });
  }

  /**
   * Stop Intent Handler
   */
  function stopIntentHandler(userID, robotName) {
    console.log('Stop intent');
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
      const robotID = robotData.uuid;
      sendRobotMovementCommand(userID, robotID, "stop_command");
      generateSpeechHeader(robotName + " is stopping");
    });
  }

  ////////////////////////////////////////////////////////////////////////////
  // Go to

  /**
   * Go To action
   */
  function moveRobot(userID, robotName, destinationName) {
    console.log("moveRobot()");
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
        generateSpeechHeader("The robot " + robotName + " is going to the " + destinationName);
      });
    });
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
  function comeHereHandler(userID, robotName, destinationName) {
    console.log("comeHereHandler()");
    console.log("userID = " + userID);
    console.log("robotName = " + robotName);
    console.log("destinationName = " + destinationName);
    // Looks for robot name
    lookUpRobotByName(userID, robotName, function(robotData) {
      const robotID = robotData.uuid;
      const mapID = robotData.map;
      // Looks for POI name
      lookUpPlaceIDByName(userID, mapID, destinationName, function(destinationID) {
        // Come here actually goes to a POI named "google home"
        sendRobotToPlaceCommand(userID, robotID, "drivePOI", destinationID.uuid);
        generateSpeechHeader(robotName + " is going to Google Home's position");
      });
    });
  }

  ////////////////////////////////////////////////////////////////////////////
  // Animations

  /**
   * Animation action
   */
  function animationIntentHandler(userID, currentRobotName, animationName) {
      lookUpRobotByName(userID, currentRobotName, function(robotData) {
          const robotID = robotData.uuid;
          sendAnimationCommand(userID, robotID, "animation", animationName);
      }) 
   }

   /**
   * Send "Animation" command to Firebase
   */
  function sendAnimationCommand(userID, robotID, animation) {
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

    generateSpeechHeader(currentRobotName + " is executing the " + currentDestinationName + " animation");
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
    console.log("Requested robot is null or invalid. Looking up user's current robot");
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
  function lookUpRobotByName(userID, robotName, callback) {
    console.log("lookUpRobotByName()");
    console.log("User ID: " + userID);
    console.log("Robot Name: " + robotName);

    admin.database().ref('robots').child(userID)
    .once('value', function (snapshot) {
	
    var robots = snapshot.val();
  	for (var robotID in robots) {
      if (robots[robotID].name) {
        if (robots[robotID].name.toLowerCase() == 
            robotName.toLowerCase()) {
          console.log("Found robot " + JSON.stringify(robots[robotID], null, 4));
  		    callback(robots[robotID]);
  		    return; // incase 2 robots with the same name
  		  }
      }
  	} 
    // Robot name not found
    generateErrorHeader("No robot named " + robotName + " was found. Check if the robot name is correct and try again later.");

    },function(errorObject){
      generateErrorHeader("An unexpected error has occured while looking up the robot in the database. Check if the robot name is correct and try again later.");
    });
  }

  /**
   * Check if the POI exists for the current Firebase user
   */
  function lookUpPlaceIDByName(userID, mapID, placeName, callback) {
    console.log("lookUpPlaceIDByName()");
    console.log("User ID: " + userID);
    console.log("Map ID: " + mapID);
    console.log("Place Name: " + placeName);

    admin.database().ref('objects').child(userID).child(mapID)
    .once('value', function(snapshot){
      var objects = snapshot.val();
      for (var objectID in objects) {
        if (objects[objectID].type == 'point_of_interest') {
          if ('variables' in objects[objectID]) {
            if ('name' in objects[objectID].variables) {
              if (objects[objectID].variables.name.toLowerCase() 
                                      == placeName.toLowerCase()) {
                console.log("Found place " + objects[objectID]);
                callback(objects[objectID]);
                return; // in case of 2 robots with the same name  
              }
            }
          }
        }
      }
      // Place not found
      generateErrorHeader("I could not find the place " + placeName + ". Verify the place name and try again later.");

    },function(errorObject) {
      generateErrorHeader("An unexpected error has occured while trying to find the map in the database. Check the database or try again later.");
    });
  }

  /**
   * Executes the appropriate robot action based on action type.
   */
  function executeAction(actionType, action, user, generateErrorHeader) {
    switch(actionType) {
      case ROBOT_ACTION_START:
      case ROBOT_ACTION_STOP:
        action(user.uid, currentRobotName);
        break;
      case ROBOT_ACTION_GOTO:
        // Executes action if the robot has a name assigned
        action(user.uid, currentRobotName, currentDestinationName);
        break;
      case ROBOT_ACTION_COME_HERE:
        var location = "google home";
        action(user.uid, currentRobotName, location);
        break;
      case ROBOT_ACTION_ANIMATION:
        action(user.uid, currentRobotName, currentAnimation);
        break;
      default:
        generateErrorHeader("An unexpected error has occurred while trying to execute the action " + action.toString + ". Please, try again later.");
    }
  }

  /*
   * Merge header with an error message
   */
  function generateErrorHeader(errorMessage) {
    console.log(errorMessage);
    // If there is an error let the user know
    response.setHeader('Content-Type', 'application/json');
    response.send(JSON.stringify({ 'speech': errorMessage, 'displayText': errorMessage }));
  }

  /*
   * Merge header with the speech response message
   */
  function generateSpeechHeader(speechMessage) {
    console.log(speechMessage);
    response.setHeader('Content-Type', 'application/json');
    response.send(JSON.stringify({ 'speech': speechMessage, 'displayText': speechMessage }));
  }

  ////////////////////////////////////////////////////////////////////////////
  // Default fallback handlers

  /**
   * Default Fallback Intent Handler
   */
  function defaultFallbackIntentHandler(app) {
    console.log('Default Fallback Intent');
  }

  /**
   * Default Welcome Intent Handler
   */
  function defaultWelcomeIntentHandler(app) {
    console.log('Default Welcome intent');
  }

 });

