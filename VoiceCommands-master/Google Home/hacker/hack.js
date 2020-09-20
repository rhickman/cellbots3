
OLD_WARN = console.warn;
console.warn = function (input) {
    if (input && input.startsWith("FIREBASE WARNING: ")) {
	return;
    }
    OLD_WARN(input);
}

var config = {
    apiKey: "AIzaSyBuaO6FX8Vg-baTbtZrQfSjY04Bc5FyAus",
    authDomain: "cellbots-robot-app.firebaseapp.com",
    databaseURL: "https://cellbots-robot-app.firebaseio.com",
    storageBucket: "cellbots-robot-app.appspot.com",
    messagingSenderId: "79733467808",
};
firebase.initializeApp(config);

var EVIL = "MALICIOUSDELICIOUS";
var EVIL_VALUE = "BADF00D";

var email = "robots@cellbots.ai";
var password = "cellbots";
var valid_map = "0189cda0-1d52-2fd6-87a5-1c6d985177e4";

var target_user = "oYlORyykBDNTCniZUNRz3n2SzwD2";
var valid_target = "00bedba3-f927-2541-813d-09784f70c36d";

function create_evil_child(ref, text) {
    console.log("Creating a " + text + "...");
    ref.set(EVIL_VALUE)
	.then(function () {
	    console.error("BAD: We were able to set a " + text); 
	    ref.remove().then(function () {
		console.error("BAD: We were able to remove a " + text);
	    }).catch(function () {});
	}).catch(function () {});
}

function read_target_user(ref, text) {
    console.log("Try to read target user " + text + "...");
    ref.once('value', function(data) {
	if (data && data.val()) {
	    console.error("BAD: was able to read user " + text);
	}
    }).catch(function () {});
}

function write_to_storage(ref, text) {
    var bytes = new Uint8Array([0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x2c, 0x20, 0x77, 0x6f, 0x72, 0x6c, 0x64, 0x21]);
    ref.put(bytes).then(function(snapshot) {
	console.warn("BAD: Wrote to storage: " + text);
    }).catch(function() { });
}

function read_from_storage(ref, text) {
    ref.getDownloadURL().then(function(url) {
	console.warn("BAD: Read from storage: " + text);
    }).catch(function() { });
}

firebase.auth()
    .signInWithEmailAndPassword(email, password)
    .then(function() {
	var uid = firebase.auth().currentUser.uid;
	
	console.log("The hacker will attempt a number of invalid operations"
		    + " against the database. If one of these invalid "
		    + " operations succeeds, a yellow warning will be printed."
		    + " Please ignore any 403 error, this means it is working.");
	console.warn("An example warning, if printed, a hack succeeded.");

	console.log("Starting hacking...");

	create_evil_child(firebase.database().ref(EVIL), "bad root key");
	create_evil_child(firebase.database().ref("maps").child(EVIL), "bad map user key");
	create_evil_child(firebase.database().ref("objects").child(EVIL), "bad object user key");
	create_evil_child(firebase.database().ref("robots").child(EVIL), "bad robot user key");
	create_evil_child(firebase.database().ref("robot_goals").child(EVIL), "bad robot_goals user key");

	create_evil_child(firebase.database().ref("objects")
			  .child(uid).child(EVIL + "ER"), "bad objects map");

	read_target_user(firebase.database().ref("maps"), "maps");
	read_target_user(firebase.database().ref("objects"), "objects");
	read_target_user(firebase.database().ref("robots"), "robots");
	read_target_user(firebase.database().ref("robot_goals"), "robot_goals");

	write_to_storage(firebase.storage().ref(EVIL), "storage invalid root file");
	write_to_storage(firebase.storage().ref('maps/' + EVIL), "storage invalid user");
	write_to_storage(firebase.storage().ref('maps/' + EVIL + '/' + EVIL), "storage invalid user map");
	write_to_storage(firebase.storage().ref('maps/' + target_user), "target_user");
	write_to_storage(firebase.storage().ref('maps/' + target_user + '/' + EVIL), "target_user map");
	write_to_storage(firebase.storage().ref('maps/' + target_user + '/' + EVIL + '/adf'), "target_user map adf");
	write_to_storage(firebase.storage().ref('maps/' + target_user + '/' + EVIL + '/dat'), "target_user map dat");
	write_to_storage(firebase.storage().ref('maps/' + uid + '/' + valid_map + '/bad'), "target_user map bad file");

	read_from_storage(firebase.storage().ref('maps/' + target_user + '/' + valid_target + '/adf'), "target_user map adf file");
	read_from_storage(firebase.storage().ref('maps/' + target_user + '/' + valid_target + '/dat'), "target_user map dat file");
	
    });



