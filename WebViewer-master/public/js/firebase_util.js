

var config = {
    apiKey: "AIzaSyBuaO6FX8Vg-baTbtZrQfSjY04Bc5FyAus",
    authDomain: "cellbots-robot-app.firebaseapp.com",
    databaseURL: "https://cellbots-robot-app.firebaseio.com",
    storageBucket: "cellbots-robot-app.appspot.com",
    messagingSenderId: "79733467808",
};
firebase.initializeApp(config);

var temporal_offset = 0;
var temporal_update = 0;
function update_time_offset() {
    temporal_update = Date.now();
    getDatabase(['.info', "serverTimeOffset"]).once("value")
	.then(function(data) {
	    var nv = data.val();
	    if (nv) {
		temporal_offset = nv;
	    }
	});
}
update_time_offset();
function get_current_time() {
    // Every minute, reset the time offset.
    if (Date.now() - temporal_update > 60 * 1000) {
	update_time_offset();
    }
    return Date.now() + temporal_offset;
}

var ROBOT_UPDATE_TIMEOUT = 10000; // ms

function blockFirebaseInjection(str, err) {
    console.assert(!str.includes("/"), err);
}

function getDatabase(array) {
    console.assert(array instanceof Array, "Not an array in getDatabase");
    console.assert(array.length > 0, "Array is empty");
    blockFirebaseInjection(array[0], "Array element 0 is injection: " + array[0]);
    var r = firebase.database().ref(array[0]);
    for (var i = 1; i < array.length; i++) {
	blockFirebaseInjection(array[i], "Array element " + i + " is injection: " + array[i]);
	r = r.child(array[i]);
    }
    return r;
}

function getStorage(array) {
    console.assert(array instanceof Array, "Not an array in getStorage");
    console.assert(array.length > 0, "Array is empty");
    blockFirebaseInjection(array[0], "Array element 0 is injection: " + array[0]);
    var r = firebase.storage().ref(array[0]);
    for (var i = 1; i < array.length; i++) {
	blockFirebaseInjection(array[i], "Array element " + i + " is injection: " + array[i]);
	r = r.child(array[i]);
    }
    return r;
}


function WatchManager() {
    this.next_watch = 0;
    this.watch_map = {};
}

WatchManager.prototype.clearWatch = function() {
    for (w in this.watch_map) {
	var ww = this.watch_map[w];
	ww.ref.off(ww.event);
    }
    this.watch_map = {}
}

WatchManager.prototype.addWatch = function(ref, event, func) {
    var w = this.next_watch;
    this.next_watch++;
    this.watch_map[w] = {'ref': ref, 'event': event};
    ref.on(event, func);
    return [w];
}

WatchManager.prototype.addWatchChildren = function(ref, callback) {
    var w1 = this.addWatch(ref, "child_added", callback(false));
    var w2 = this.addWatch(ref, "child_changed", callback(false));
    var w3 = this.addWatch(ref, "child_removed", callback(true));
    return [].concat(w1).concat(w2).concat(w3);
}

WatchManager.prototype.removeWatch = function(watches) {
    for (var wi in watches) {
	var w = watches[wi];
	if (w in this.watch_map) {
	    this.watch_map[w].ref.off(this.watch_map[w].event);
	    delete this.watch_map[w];
	}
    }
}



