
var ROBOT_UPDATE_TIMEOUT = 10000; // ms

var googleProvider = new firebase.auth.GoogleAuthProvider();

var SEQUENCE = 0;

CURRENT_USER = null;
NO_USER = true;
CURRENT_PAGE = "";

SELECTED_TYPE = "";
SELECTED_ITEM = "";

/////////////////////////////
OBJECT_TYPES = {
    "point_of_interest" : {
	"display" : {
	    "point" : {
		"color" : "red",
		"location" : "location",
		"offset" : "0,0",
		"type" : "point"
	    },
	    "text" : {
		"color" : "red",
		"location" : "location",
		"offset" : "0,-0.1",
		"type" : "text",
		"variable" : "name"
	    }
	},
	"name" : "point_of_interest",
	"variables" : {
	    "location" : "TRANSFORM",
	    "name" : "STRING"
	}
    }
};
/////////////////////////////


function logout() {
    firebase.auth().signOut();
}

function mainPage() {
    CURRENT_PAGE = "MAIN";
    location.hash = "";
    render();
}

function loginPage() {
    CURRENT_PAGE = "LOGIN";
    $('#login-row-invalid').css('display', 'none');
    
    render();
}
function loginSubmit() {
    var email = $('#login-row-email').val();
    var password = $('#login-row-password').val();
    firebase.auth().signInWithEmailAndPassword(email, password).catch(function(error) {
	// Handle Errors here.
	var errorCode = error.code;
	var errorMessage = error.message;
	$('#login-row-invalid').css('display', '');
	// ...
    });
}

function loginGoogle() {
    firebase.auth().signInWithRedirect(googleProvider);
}

function registerPage() {
    CURRENT_PAGE = "REGISTER";
    render();
}

function getMapUUID() {
    var s = location.hash.replace("#", "").trim().split("/");
    if (s[0] == "maps") {
	return s[1].trim().split(":")[0].trim();
    } else {
	return null;
    }
}

function getMapCentroid() {
    var s = location.hash.replace("#", "").trim().split("/");
    if (s[0] == "maps") {
	var ss = s[1].trim().split(":");
	if (ss.length > 3) {
	    return [parseFloat(ss[1]), parseFloat(ss[2]), parseFloat(ss[3])];
	}
    }
    return [0, 0, 0];
}

function projectPoint(tf, centroid) {
    if (!centroid) {
	centroid = getMapCentroid();
    }

    // The centroid is invalid, e.g. we are inside earth or it is unspecified
    if (vector_len(centroid) < 1.0) {
	centroid = [0, 0, 1];
    }
    
    // Compute the point and the plane center
    var pt = [tf.px, tf.py, tf.pz];
    var v = vector_sub(pt, centroid);
    
    // TODO: this is inaccurate at the poles or with altitude changes
    // TODO: use ECEF standard to compute normal vector
    var n = vector_normalize(centroid);
    
    // TODO: verify correct behavior at the poles
    var dist_vert = vector_dot([0, 0, 1], n);
    var vertvector = vector_sub([0, 0, 1], vector_scale(n, dist_vert));

    // If the vector is near the poles, then we simply set to z
    if (vector_len(vertvector) < 0.0001) {
	var r = 2.0* Math.atan2(tf.qw, tf.qz) + Math.PI / 2;
	return [tf.px, tf.py, r];
    }
    
    // Compute the vertical and horizontal components
    var vert_component = vector_normalize(vertvector);
    var horiz_component = vector_normalize(vector_cross_product(vert_component, n));

    var quat = quaterion_from_matrix([horiz_component, vert_component, n]);
    
    var target_quat = quaternion_multiply(quat, [tf.qx, tf.qy, tf.qz, tf.qw]);
        
    var d = Math.sqrt(1 - target_quat[3] * target_quat[3]);
    var r_drift = vector_len(vector_sub(n, [target_quat[0] / d, target_quat[1] / d, target_quat[2] / d]));
    
    var r_avg = vector_dot([target_quat[0], target_quat[1], target_quat[2]], [1 / n[0], 1 / n[1], 1 / n[2]]);
    var r = -Math.acos(target_quat[3]) * 2.0 - Math.PI / 2;
    
    var pproj = vector_sub(pt, centroid);
    
    return [vector_dot(pproj, horiz_component), vector_dot(pproj, vert_component), r];
}

function quaternion_multiply(q, r) {
    var newX = q[3] * r[0] + q[0] * r[3] + q[1] * r[2] - q[2] * r[1];
    var newY = q[3] * r[1] + q[1] * r[3] + q[2] * r[0] - q[0] * r[2];
    var newZ = q[3] * r[2] + q[2] * r[3] + q[0] * r[1] - q[1] * r[0];
    var newW = q[3] * r[3] - q[0] * r[0] - q[1] * r[1] - q[2] * r[2];
    return [newX, newY, newZ, newW];
}

function quaterion_from_matrix(matrix) {
    var xx = matrix[0][0], xy = matrix[0][1], xz = matrix[0][2];
    var yx = matrix[1][0], yy = matrix[1][1], yz = matrix[1][2];
    var zx = matrix[2][0], zy = matrix[2][1], zz = matrix[2][2];
    var t = xx + yy + zz;
    var x, y, z, w;

    // we protect the division by s by ensuring that s>=1
    if (t >= 0) { // |w| >= .5
        var s = Math.sqrt(t + 1); // |s|>=1 ...
        w = 0.5 * s;
        s = 0.5 / s; // so this division isn't bad
        x = (zy - yz) * s;
        y = (xz - zx) * s;
        z = (yx - xy) * s;
    } else if ((xx > yy) && (xx > zz)) {
        var s = Math.sqrt(1.0 + xx - yy - zz); // |s|>=1
        x = s * 0.5; // |x| >= .5
        s = 0.5 / s;
        y = (yx + xy) * s;
        z = (xz + zx) * s;
        w = (zy - yz) * s;
    } else if (yy > zz) {
        var s = Math.sqrt(1.0 + yy - xx - zz); // |s|>=1
        y = s * 0.5; // |y| >= .5
        s = 0.5 / s;
        x = (yx + xy) * s;
        z = (zy + yz) * s;
        w = (xz - zx) * s;
    } else {
        var s = Math.sqrt(1.0 + zz - xx - yy); // |s|>=1
        z = s * 0.5; // |z| >= .5
        s = 0.5 / s;
        x = (xz + zx) * s;
        y = (zy + yz) * s;
        w = (yx - xy) * s;
    }
    return [x, y, z, w];
}

function vector_normalize(v1) {
    return vector_scale(v1, 1.0 / vector_len(v1));
}

function vector_cross_product(v1, v2) {
    return [v1[1]*v2[2] - v1[2]*v2[1],
	    v1[2]*v2[0] - v1[0]*v2[2],
	    v1[0]*v2[1] - v1[1]*v2[0]];
}

function vector_sub(v1, v2) {
    var vo = [];
    for (var i = 0; i < v1.length && i < v2.length; i++) {
	vo[i] = v1[i] - v2[i];
    }
    return vo;
}

function vector_add(v1, v2) {
    var vo = [];
    for (var i = 0; i < v1.length && i < v2.length; i++) {
	vo[i] = v1[i] + v2[i];
    }
    return vo;
}

function vector_len(v1) {
    var vo = 0;
    for (var i = 0; i < v1.length; i++) {
	vo += v1[i] * v1[i];
    }
    return Math.sqrt(vo);
}

function vector_scale(v1, f) {
    var vo = [];
    for (var i = 0; i < v1.length; i++) {
	vo[i] = v1[i] * f;
    }
    return vo;
}

function vector_dot(v1, v2) {
    var vo = 0;
    for (var i = 0; i < v1.length && i < v2.length; i++) {
	vo += v1[i] * v2[i];
    }
    return vo;
}

GLOBAL_CUSTOM_TFS = [];
GLOBAL_TFS = [];
GLOBAL_FP = [];
GLOBAL_X_MAX = 0.0;
GLOBAL_X_MIN = 0.0;
GLOBAL_Y_MAX = 0.0;
GLOBAL_Y_MIN = 0.0;

POINT_SIZE = 0.01;
var GOAL_POINT_SIZE = 0.05;

function readTf(bytes, i, tfArray, tfNumber) {
    var posLen = bytes.getUint8(i++);
    var rotLen = bytes.getUint8(i++);
    if (posLen != 3) {
	console.log("Weird position length: " + posLen + " for " + tfNumber);
    }
    if (rotLen != 4) {
	console.log("Weird rotation length: " + rotLen + " for " + tfNumber);
    }
    var fp = {};
    fp.ts = bytes.getFloat64(i);
    i += 8;

    fp.px = bytes.getFloat64(i);
    i += 8;
    fp.py = bytes.getFloat64(i);
    i += 8;
    fp.pz = bytes.getFloat64(i);
    i += 8;
    for (var k = 3; k < posLen; k++)
	i += 8;

    if (fp.px > GLOBAL_X_MAX) GLOBAL_X_MAX = fp.px;
    if (fp.px < GLOBAL_X_MIN) GLOBAL_X_MIN = fp.px;
    if (fp.py > GLOBAL_Y_MAX) GLOBAL_Y_MAX = fp.py;
    if (fp.py < GLOBAL_Y_MIN) GLOBAL_Y_MIN = fp.py;
    

    fp.qx = bytes.getFloat64(i);
    i += 8;
    fp.qy = bytes.getFloat64(i);
    i += 8;
    fp.qz = bytes.getFloat64(i);
    i += 8;
    fp.qw = bytes.getFloat64(i);
    i += 8;
    for (var k = 4; k < rotLen; k++)
	i += 8;

    tfArray.push(fp);
    return i;
}

function readStyle0Map(bytes) {
    var i = 1;
    GLOBAL_TFS = [];
    while (i < bytes.byteLength) {
	i = readTf(bytes, i, GLOBAL_TFS, (i - 1) / 66);
    }
    GLOBAL_FP = [];
    GLOBAL_CUSTOM_TFS = [];
}


function readFloorplan(bytes, i) {
    var fp = {};
    fp.minZ = bytes.getFloat64(i);
    i += 8;
    fp.maxZ = bytes.getFloat64(i)
    i += 8;
    fp.polygons = [];
    var polys = bytes.getInt32(i);
    i += 4;
    while (polys--) {
	var poly = {};
	poly.closed = bytes.getUint8(i);
	i += 1;
	poly.area = bytes.getFloat64(i);
	i += 8;
	poly.layer = bytes.getInt32(i);
	i += 4;
	var verts = bytes.getInt32(i);
	i += 4;
	poly.verticies = [];
	while (verts--) {
	    poly.verticies.push([bytes.getFloat64(i), bytes.getFloat64(i + 8)]);
	    i += 16;
	}
	fp.polygons.push(poly);
    }
    GLOBAL_FP.push(fp);
}


function readStyle123Map(bytes) {
    var i = 1;

    if (bytes.getUint8(0) >= 2) {
	var len = bytes.getInt32(i);
	i += 4 + len;
    }

    tfCount = bytes.getInt32(i);
    i += 4;
    GLOBAL_TFS = [];
    while (tfCount--) {
	i = readTf(bytes, i, GLOBAL_TFS, tfCount);
    }

    WORLD_BYTES_START_CUSTOM = i;

    GLOBAL_CUSTOM_TFS = [];
    if (bytes.getUint8(0) >= 3) {
	tfCount = bytes.getInt32(i);
	i+= 4;
	while (tfCount--) {
	    i = readTf(bytes, i, GLOBAL_CUSTOM_TFS, tfCount);
	}
    }
    

    WORLD_BYTES_END_CUSTOM = i;

    GLOBAL_FP = [];
    fpLevelsCount = bytes.getInt32(i);
    i += 4;

    while (fpLevelsCount--) {
	i = readFloorplan(bytes, i);
    }
    
}

function drawTfs() {
    var step = Math.ceil(GLOBAL_TFS.length / 1000);
    if (step < 1) step = 1;
    
    $('div.tf-display').remove();
    var tf_form = $('#map-content');
    for (var i = 0; i < GLOBAL_TFS.length; i += step) {
	$('<div class="tf-display"></div>')
	    .css('background-color', 'red')
	    .css('height', POINT_SIZE + 'px')
	    .css('width', POINT_SIZE + 'px')
	    .css('position', 'absolute')
	    .css('z-index', '1002')
	    .css('left', -GLOBAL_TFS[i].px - POINT_SIZE / 2)
	    .css('top', GLOBAL_TFS[i].py - POINT_SIZE / 2)
	    .appendTo(tf_form);
    }

    updateMapMargins();
}


CUSTOM_POINT_SIZE = 0.5;
function drawCustomTfs() {
    $('div.ctf-display').remove();
    var tf_form = $('#map-content');
    for (var i = 0; i < GLOBAL_CUSTOM_TFS.length; i++) {
	$('<div class="ctf-display"></div>')
	    .css('background-color', 'yellow')
	    .css('height', CUSTOM_POINT_SIZE + 'px')
	    .css('width', CUSTOM_POINT_SIZE + 'px')
	    .css('border-radius', (CUSTOM_POINT_SIZE / 2.0) + 'px')
	    .css('position', 'absolute')
	    .css('z-index', '1001')
	    .css('left', -GLOBAL_CUSTOM_TFS[i].px - CUSTOM_POINT_SIZE / 2)
	    .css('top', GLOBAL_CUSTOM_TFS[i].py - CUSTOM_POINT_SIZE / 2)
	    .appendTo(tf_form);
    }

    updateMapMargins();
}

function drawFloorplan() {
    $('svg.floorplan').remove();
    var fp = GLOBAL_FP.length > 0 ? GLOBAL_FP[0] : null;
    if (!fp) return;

    var SVG_MIN_X = 0.0;
    var SVG_MAX_X = 0.0;
    var SVG_MIN_Y = 0.0;
    var SVG_MAX_Y = 0.0;

    var layeredPolygons = {}

    for (var i in fp.polygons) {
	var poly = fp.polygons[i];
	for (var k in poly.verticies) {
	    if (poly.verticies[k][0] < SVG_MIN_X) SVG_MIN_X = poly.verticies[k][0];
	    if (poly.verticies[k][0] > SVG_MAX_X) SVG_MAX_X = poly.verticies[k][0];
	    if (poly.verticies[k][1] < SVG_MIN_Y) SVG_MIN_Y = poly.verticies[k][1];
	    if (poly.verticies[k][1] > SVG_MAX_Y) SVG_MAX_Y = poly.verticies[k][1];
	    if (!(poly.layer in layeredPolygons)) {
		layeredPolygons[poly.layer] = [];
	    }
	    layeredPolygons[poly.layer].push(poly);
	}
    }
    
    var polyContent = "";
    for (var layer in layeredPolygons) {
	for (var i in layeredPolygons[layer]) {
	    var poly = layeredPolygons[layer][i];
	    var content = "";
	    
	    var fcs = ["#d2d2d2", "white", "#15A6D9", "purple"];
	    var minArea = [2.0, 0.04, 0, 0];
	    if (poly.area <= minArea[poly.layer % minArea.length]) {
		continue;
	    }

	    for (var k in poly.verticies) {
		if (content != "") content += " ";
		content += (SVG_MAX_X - poly.verticies[k][0])
		    + "," + (poly.verticies[k][1] - SVG_MIN_Y);
	    }
	    	    
	    polyContent += '<polygon points="' + content
		+ '" style="fill:' + fcs[poly.layer % fcs.length] + ';';
	    if (poly.layer % fcs.length != 1) {
		polyContent += '" />';
	    } else {
		polyContent += 'stroke:' + "black" + ';stroke-width:0.01" />';
	    }
	}
    }

    var fpCon = $('<svg class="floorplan">' + polyContent + '</div>')
	.css("position", "absolute").css('z-index', '1000');
    fpCon.attr("width", SVG_MAX_X - SVG_MIN_X);
    fpCon.attr("height", SVG_MAX_Y - SVG_MIN_Y);
    fpCon.css("left", -SVG_MAX_X);
    fpCon.css("top", SVG_MIN_Y);

    if (SVG_MIN_X < GLOBAL_X_MIN) GLOBAL_X_MIN = SVG_MIN_X;
    if (SVG_MAX_X > GLOBAL_X_MAX) GLOBAL_X_MAX = SVG_MAX_X;
    if (SVG_MIN_Y < GLOBAL_Y_MIN) GLOBAL_Y_MIN = SVG_MIN_Y;
    if (SVG_MAX_Y > GLOBAL_Y_MAX) GLOBAL_Y_MAX = SVG_MAX_Y;
    
    $('#map-content').append(fpCon);
    updateMapMargins();
}


function updateMapMargins() {
    $('#map-content')
	.css('margin-left', (GLOBAL_X_MAX + 1) + 'px')
	.css('width', (-GLOBAL_X_MIN + 1) + 'px')
	.css('margin-top', (-GLOBAL_Y_MIN + 1) + 'px')
	.css('height', (GLOBAL_Y_MAX + 1) + 'px');
}

var ROBOT_GOALS = {};

function robotGoalUpdated(for_robot, remove) {
    return function (data) {
	if (SELECTED_TYPE == "ROBOT" && SELECTED_ITEM == for_robot) {
	    if (remove) {
		delete ROBOT_GOALS[data.key];
	    } else {
		ROBOT_GOALS[data.key] = data.val();
                ROBOT_GOALS[data.key].uuid = data.key;
                ROBOT_GOALS[data.key].timestamp = Math.round(ROBOT_GOALS[data.key].timestamp);
	    }
	}
	reloadRobotGoals();
    }
};

function reloadRobotGoals() {
    var time_goals = [];
    for (var goal in ROBOT_GOALS) {
	time_goals.push(ROBOT_GOALS[goal]);
    }
    time_goals = time_goals.sort(function (a, b) {
	if (a.timestamp < b.timestamp) {
	    return -1;
	} else if (a.timestamp > b.timestamp) {
	    return 1;
	}
	return 0;
    });

    $('div.goal-display').remove();
    
    $('#robot-goals-body').html('');
    for (var goal in time_goals) {
	var v = time_goals[goal];
	if (!GOAL_TYPES[v.name]) {
	    continue;
	}
	

	var table = $('<div class="popup" style="display: none; border: 1px solid black; '
		      + 'position: absolute; background: white; z-index: 1000000; top: 0px; left: 0px;"></div>');

	
	writeParameterListStatic(table, GOAL_TYPES[v.name].parameters, v.parameters);
	table.prepend($('<div class="row"><div class="col-sm-3"><label>Timestamp</label></div></div>')
		      .append($('<div class="col-sm-9"></div>').text(v.timestamp)));
	table.prepend($('<div class="row"><div class="col-sm-3"><label>Priority</label></div></div>')
		      .append($('<div class="col-sm-9"></div>').text(v.priority ? v.priority : 0)));

	var info = $('<div class="col-sm-7"></div>')
	    .append($('<div class="infotext"></div>').text(v.name))
	    .append($('<div style="position: relative;"></div>').append(table));
	info.mouseover(function() { $(this).children().children('div.popup').css('display', ''); });
	info.mouseout(function() { $(this).children().children('div.popup').css('display', 'none'); });
	
	var t = $('<div class="row"></div>');
	t.append(info);
	if (v.complete) {
	    t.append($('<div class="col-sm-5">Completed</div>'));
	} else if (v.cancel && !v.reject) {
	    t.append($('<div class="col-sm-5">Cancelling</div>'));
	} else if (v.cancel && v.reject) {
	    t.append($('<div class="col-sm-5">Cancelled</div>'));
	} else if (v.reject) {
	    t.append($('<div class="col-sm-5">Rejected</div>'));
	} else {
	    t.append($('<div class="col-sm-5"></div>').append(
		$('<button class="btn btn-primary" type="button">Cancel</button>')
		    .click(function(remove_path) {
			return function() {
			    getDatabase(remove_path).set(true);
			}
		    }(['robot_goals', CURRENT_USER.uid, SELECTED_ITEM, 'goals', v.uuid, 'cancel']))));
	}
	$('#robot-goals-body').append(t);

	if (!v.complete && !v.reject) {
	    for (var param in GOAL_TYPES[v.name].parameters) {
		if (GOAL_TYPES[v.name].parameters[param] == "TRANSFORM") {
		    $('<div class="goal-display"></div>')
			.css('background-color', 'purple')
			.css('height', GOAL_POINT_SIZE + 'px')
			.css('width', GOAL_POINT_SIZE + 'px')
			.css('position', 'absolute')
			.css('z-index', '1003')
			.css('left', -v.parameters[param].px - GOAL_POINT_SIZE / 2 - 0.01)
			.css('top', v.parameters[param].py - GOAL_POINT_SIZE / 2 - 0.01)
			.appendTo($('#map-content'));
		}
	    }
	}
    }
}

function robotGoals() {
    if (SELECTED_ITEM == "" || SELECTED_TYPE != "ROBOT") {
	return;
    }
    if (!CURRENT_USER) {
	return;
    }
    
    $('#robot-goals-header').text("Goals For Robot: "
				  + ((SELECTED_ITEM in ROBOTS && ROBOTS[SELECTED_ITEM].name)
				     ? ROBOTS[SELECTED_ITEM].name : SELECTED_ITEM));
    $('#robot-add-goal').modal("hide");
    $('#robot-goals').modal("show");
    reloadRobotGoals();
}

var GOAL_TYPES = {};

function mouseToWorld(e) {
    var x = -((e.pageX / $('#map-content').css('zoom'))
	      - $('#map-content').offset().left);
    var y = ((e.pageY / $('#map-content').css('zoom'))
	     - $('#map-content').offset().top);
    return {"x": x, "y": y}
}

function guid() {
    function s4() {
	return Math.floor((1 + Math.random()) * 0x10000)
	    .toString(16)
	    .substring(1);
    }
    return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
	s4() + '-' + s4() + s4() + s4();
}


function robotModalAddGoalCancel() {
    $('#robot-add-goal').modal("hide");
    if ($('#robot-add-goal').data('show-manager')) {
	robotGoals();
    }
}

function robotModalAddGoalSubmit() {
    $('#robot-add-goal').modal("hide");
    if ($('#robot-add-goal').data('show-manager')) {
	$('#robot-goals').modal('show');
	reloadRobotGoals();
    }

    var goal_type = GOAL_TYPES[$('#robot-add-goal-goal-type').val()];
    var write_object = {
	'uuid': guid(),
	'sequence': SEQUENCE++,
	'timestamp': firebase.database.ServerValue.TIMESTAMP,
	'name': goal_type["name"],
	'version': goal_type["version"],
	'parameters': {},
	'priority': 100,
    };

    readParameterList($('#robot-add-goal-controls'),
		      goal_type["parameters"], write_object.parameters);

    getDatabase(["robot_goals", CURRENT_USER.uid, SELECTED_ITEM, "goals",
		 write_object.uuid]).set(write_object);
}


function readParameterList(listElement, types, outputValues) {
    listElement.children().each(function () {
	var param = $(this).data('param');
	if (!(param in types)) {
	    return;
	}
	var paramtype = types[param];
	if (paramtype == "MAP") {
	    outputValues[param] = $(this).children().children("select").val();
	} else if (paramtype == "STRING") {
	    outputValues[param] = $(this).children().children("input").val();
	} else if (paramtype == "LONG") {
	    outputValues[param] = $(this).children().children("input").val();
	} else if (paramtype.split(":")[0] == "OBJECT") {
	    outputValues[param] = $(this).children().children("select").val();
	} else if (paramtype == "ANIMATION") {
	    outputValues[param] = $(this).children().children("select").val();
	} else if (paramtype == "TRANSFORM") {
	    outputValues[param] = {'ts': -1.0};
	    $(this).children().children().children().children("input").each(function () {
		outputValues[param][$(this).prop('name')] = parseFloat($(this).val());
	    });
	} else {
	    alert('invalid parameter type: ' + paramtype);
	}
    });
}

function writeParameterListStatic(listElement, types, initValues) {
    listElement.html('');
    for (var param in types) {
	var paramtype = types[param];
	var row = $('<div class="row"></div>').data('param', param)
	    .append($('<div class="col-sm-3"></div>')
		    .append($('<label></label>').html(param)));
	var cont = $('<div class="col-sm-9"></div>').appendTo(row);
	row.appendTo(listElement);
	if (paramtype == "MAP") {
	    cont.text(initValues[param]);
	} else if (paramtype == "STRING") {
	    cont.text(initValues[param]);
	} else if (paramtype == "LONG") {
	    cont.text(initValues[param]);
	} else if (paramtype == "ANIMATION") {
	    cont.text(initValues[param]);
	} else if (paramtype.split(":")[0] == "OBJECT") {
	    if (OBJECTS[initValues[param]]) {
		if (OBJECTS[initValues[param]].variables) {
		    if (OBJECTS[initValues[param]].variables.name) {
			cont.text(OBJECTS[initValues[param]].variables.name);
		    } else {
			cont.text(initValues[param]);
		    }
		} else {
		    cont.text(initValues[param]);
		}
	    } else {
		cont.text(initValues[param]);
	    }
	} else if (paramtype == "TRANSFORM") {
	    var postypes = ["px", "py", "pz", "qx", "qy", "qz", "qw"];
	    for (var postype_i in postypes) {
		var postype = postypes[postype_i];
		var row = $('<div class="row"><div class="col-sm-1">' + postype
			    + '</div><div class="posinfo col-sm-11"></div></div>')
		    .data('param', param);
		if (param in initValues) {
		    if (postype in initValues[param]) {
			row.children("div.posinfo").text(initValues[param][postype]);
		    }
		}
		cont.append(row);
	    }
	} else {
	    alert("Parameter type invalid: " + paramtype);
	}
    }
}

var ANIMATIONS = {};

function writeParameterList(listElement, types, initValues, transformSelectStartCb, transformSelectEndCb) {
    listElement.html('');
    if (!initValues) initValues = {};
    for (var param in types) {
	var paramtype = types[param];
	var row = $('<div class="row"></div>').data('param', param)
	    .append($('<div class="col-sm-3"></div>')
		    .append($('<label></label>').html(param)));
	var cont = $('<div class="col-sm-9"></div>').appendTo(row);
	row.appendTo(listElement);
	if (paramtype == "MAP") {
	    var sel = $('<select class="form-control"></select>');
	    sel.append($("<option selected></option>")
		       .val(getMapUUID()).text(getMapUUID()));
	    cont.append(sel);
	} else if (paramtype == "STRING") {
	    var sel = $('<input class="form-control"></input>');
	    if (param in initValues) sel.val(initValues[param]);
	    cont.append(sel);
	} else if (paramtype == "LONG") {
	    var sel = $('<input class="form-control"></input>');
	    if (param in initValues) sel.val(initValues[param]);
	    cont.append(sel);
	} else if (paramtype == "ANIMATION") {
	    var sel = $('<select class="form-control animation-list"></select>');
	    for (var animation in ANIMATIONS) {
		var opt = $("<option selected></option>")
		    .val(animation).text(animation);
		if (param in initValues) {
		    if (animation == initValues[param]) {
			opt.prop('selected', true);
		    }
		}
		sel.append(opt);
	    }
	    cont.append(sel);
	} else if (paramtype.split(":")[0] == "OBJECT") {
	    var sel = $('<select class="form-control"></select>');
	    var object_type = paramtype.split(":")[1];
	    for (var object_uuid in OBJECTS) {
		if (OBJECTS[object_uuid].type == object_type) {
		    var opt = $("<option></option>").val(object_uuid).text(object_uuid);
		    if (OBJECTS[object_uuid].variables) {
			if (OBJECTS[object_uuid].variables.name) {
			    opt.text(OBJECTS[object_uuid].variables.name);
			}
		    }
		    if (param in initValues) {
			if (object_uuid == initValues[param]) {
			    opt.prop('selected', true);
			}
		    }
		    sel.append(opt);
		}
	    }
	    cont.append(sel);
	} else if (paramtype == "TRANSFORM") {
	    var postypes = ["px", "py", "pz", "qx", "qy", "qz", "qw"];
	    for (var postype_i in postypes) {
		var postype = postypes[postype_i];
		var row = $('<div class="row"><div class="col-sm-1">' + postype
			    + '</div><div class="col-sm-11"><input name="'
			    + postype + '" class="form-control"/></div></div>')
		    .data('param', param);
		if (param in initValues) {
		    if (postype in initValues[param]) {
			row.children().children().val(initValues[param][postype]);
		    }
		}
		cont.append(row);
	    }
	    $('<button type="button" class="btn btn-primary">Select Point...</button>')
		.click(function (iparam) {
		    return function () {
			transformSelectStartCb();
			
			$('#map-scroll-region').unbind("click").click(function(e) {
			    var start = mouseToWorld(e);
			    var start_point =
				$('<div class="maptemp"></div>')
				.css("position", "absolute")
				.css("width", "0.1px").css("height", "0.1px")
				.css("z-index", '1003').css('background-color', 'blue')
				.css('top', start.y - 0.05).css('left', -start.x - 0.05)
				.appendTo('#map-content');
			    
			    $('#map-scroll-region').unbind("click").click(function(e) {
				$('#map-scroll-region').unbind("click").click(defaultClickPointGenerate);
				var end = mouseToWorld(e);
				
				var angle = Math.atan2(end.y - start.y, end.x - start.y);
				
				// TODO Maybe this could be less hacky?
				listElement.children().children().children().children().children()
				    .each(function () {
					if ($(this).parent().parent().data('param') == iparam) {
					    if ($(this).attr('name') == "px") {
						$(this).val(start.x)
					    } else if ($(this).attr('name') == "py") {
						$(this).val(start.y)
					    } else if ($(this).attr('name') == "pz") {
						$(this).val(0.0);
					    } else if ($(this).attr('name') == "qx") {
						$(this).val(0.0);
					    } else if ($(this).attr('name') == "qy") {
						$(this).val(0.0);
					    } else if ($(this).attr('name') == "qz") {
						$(this).val(Math.sin(angle / 2));
					    } else if ($(this).attr('name') == "qw") {
						$(this).val(Math.cos(angle / 2));
					    }
					}
				    });
				start_point.remove();

				transformSelectEndCb();
				
			    });
			});
			
		    };
		}(param)).appendTo($('<div class="col-sm-11"></div>')
				   .appendTo(
				       $('<div class="row"><div class="col-sm-1"></div></div>')
					   .appendTo(cont)));
	} else {
	    alert("Invalid parameter type: " + paramtype);
	}
    }   
}

function defaultClickPointGenerate(e) {
    // Disable click to goal, since it is annoying
    if (true) {
	return;
    }
    
    var end = mouseToWorld(e);
    if (SELECTED_ITEM == "" || SELECTED_TYPE != "ROBOT") {
	return;
    }
    if (!CURRENT_USER) {
	return;
    }
    var map_uuid = getMapUUID();
    if (!map_uuid) {
	return;
    }

    var write_object = {
	'uuid': guid(),
	'sequence': SEQUENCE++,
	'timestamp': firebase.database.ServerValue.TIMESTAMP,
	'name': "drivePoint",
	'version': "1.0.0",
	'priority': 100,
	'parameters': {'map': map_uuid,
		       'location': {'ts': -1, 'px': end.x, 'py': end.y, 'pz': 0.0,
				    'qx': 0.0, 'qy': 0.0, 'qz': 0.0, 'qw': 1.0},
		      }
    };

    getDatabase(["robot_goals", CURRENT_USER.uid, SELECTED_ITEM, "goals",
		 write_object.uuid]).set(write_object);
}
    

$('#map-scroll-region').click(defaultClickPointGenerate);


function robotModalAddGoal(showManager) {
    if (SELECTED_ITEM == "" || SELECTED_TYPE != "ROBOT") {
	return;
    }
    $('#robot-goals').modal("hide");
    $('#robot-add-goal-header').text("Add Goal For Robot: "
				     + ((SELECTED_ITEM in ROBOTS && ROBOTS[SELECTED_ITEM].name)
					? ROBOTS[SELECTED_ITEM].name : SELECTED_ITEM));
    $('#robot-add-goal').data('show-manager', showManager);
    $('#robot-add-goal').modal("show");
}

function robotSelectorChange() {
    setSelected("ROBOT", $('#map-robot-select').val());
    updateSelectedRobot();
}

var CALL_UUID = null;


function startVideo() {
    if (SELECTED_TYPE != "ROBOT" || SELECTED_ITEM == "") {
	return;
    }
    if (!CURRENT_USER) {
	return;
    }
    if (CALL_UUID) {
	return;
    }
    var user = CURRENT_USER.uid;
    CALL_UUID = guid();
    getDatabase(["robot_goals", CURRENT_USER.uid, SELECTED_ITEM, "webrtc", CALL_UUID])
	.on("value", function (snapshot) {
	    console.log(snapshot.val());
	});
    getDatabase(["robot_goals", CURRENT_USER.uid, SELECTED_ITEM, "webrtc", CALL_UUID])
	.set({'timestamp': firebase.database.ServerValue.TIMESTAMP});
}

function setSelected(type, val) {
    var oldSelectType = SELECTED_TYPE;
    var oldSelectItem = SELECTED_ITEM;
    if (type == "ROBOT") {
	SELECTED_TYPE = "ROBOT";
	SELECTED_ITEM = val;
    } else if (type == "OBJECT") {
	SELECTED_TYPE = "OBJECT";
	SELECTED_ITEM = val;
    } else if (type == "CUSTOM_POINTS") {
	SELECTED_TYPE = "CUSTOM_POINTS";
	SELECTED_ITEM = "";
    } else {
	alert('Cannot select: "' + type + '"');
    }
    updateSelectedRobot(oldSelectType, oldSelectItem);
    updateSelectedObject(oldSelectType, oldSelectItem);
    updateSelectedCustomPoints(oldSelectType, oldSelectItem);
}

var ROBOT_GOAL_WATCH = null;
function removeRobotGoalWatch() {
    if (ROBOT_GOAL_WATCH != null) {
	MAP_WATCH.removeWatch(ROBOT_GOAL_WATCH);
	ROBOT_GOAL_WATCH = null;
    }
}

function updateSelectedRobot(oldSelectType, oldSelectItem) {
    $('div.robot').each(function() {
	$(this).children().remove();
	if ($(this).data('uuid') == SELECTED_ITEM && SELECTED_TYPE == "ROBOT") {
	    $(this).append('<div style="width: 100%; height: 100%; '
			   + 'opacity: 0.6; background-color: orange;"></div>');
	}
    });
    $('#map-robot-select').children().each(function() {
	if ($(this).attr('value') == "" && SELECTED_TYPE != "ROBOT") {
	    $(this).prop('selected', true);
	} else if ($(this).attr('value') == SELECTED_ITEM && SELECTED_TYPE == "ROBOT") {
	    $(this).prop('selected', true);
	} else {
	    $(this).removeAttr('selected');
	}
    });

    if (oldSelectType == "ROBOT" && oldSelectItem != "") {
	removeRobotGoalWatch();
    }
    ROBOT_GOALS = {};
    $('#robot-goals-body').html('');
    $('div.goal-display').remove();
    if (SELECTED_ITEM != "" && SELECTED_TYPE == "ROBOT") {
	GOAL_TYPES = {};
	getDatabase(["robot_goals", CURRENT_USER.uid, SELECTED_ITEM, "animations"])
	    .once("value", function (value) {
		if (value.val()) {
		    ANIMATIONS = value.val();
		} else {
		    ANIMATIONS = {};
		}
		getDatabase(["robot_goals", CURRENT_USER.uid, SELECTED_ITEM, "goal_types"])
		    .once("value", function (value) {
			GOAL_TYPES = value.val();
			$('#robot-add-goal-body').html('');
			var selector = $('<select class="form-control" id="robot-add-goal-goal-type"></select>')
			for (var gt in GOAL_TYPES) {
			    if (GOAL_TYPES[gt].userVisible != false) {
				selector.append($('<option></option>').prop('value', gt).text(gt));
			    }
			}
			$('#robot-add-goal-body').append(selector);
			$('#robot-add-goal-body').append('<div id="robot-add-goal-controls"></div>');
			
			selector.change(function () {
			    writeParameterList($('#robot-add-goal-controls'), GOAL_TYPES[$(this).val()]["parameters"], {},
					       function() {
						   $('#robot-add-goal').modal("hide");
					       },
					       function() {
						   $('#robot-add-goal').modal("show");
					       });
			});
			selector.change();

			removeRobotGoalWatch();
			
			// Start updating the goals
			ROBOT_GOAL_WATCH =
			    MAP_WATCH.addWatchChildren(getDatabase(['robot_goals', CURRENT_USER.uid, SELECTED_ITEM, 'goals']),
						       function (sel) {
							   return function (removed) {
							       return robotGoalUpdated(sel, removed);
							   }
						       }(SELECTED_ITEM));
		    });
	    });
    }
}

function updateSelectedObject(oldSelectType, oldSelectItem) {
    $('#map-object-select').children().each(function() {
	if ($(this).attr('value') == "" && SELECTED_TYPE != "OBJECT") {
	    $(this).prop('selected', true);
	} else if ($(this).attr('value') == SELECTED_ITEM && SELECTED_TYPE == "OBJECT") {
	    $(this).prop('selected', true);
	} else {
	    $(this).removeAttr('selected');
	}
    });
}

DELETE_CUSTOM_POINTS = false;

function updateSelectedCustomPoints(oldSelectType, oldSelectItem) {
    var dis = (SELECTED_TYPE == "CUSTOM_POINTS");
    $('#map-row').children().children().children("select").prop("disabled", dis);
    $('#map-row').children().children().children("button").prop("disabled", dis);
    $('#map-custom-points').prop('disabled', false);
    $('#map-custom-points-delete').prop('disabled', !dis);
    $('#map-zoom-in').prop('disabled', false);
    $('#map-zoom-out').prop('disabled', false);
    if (dis) {
	$('#map-custom-points').text('Stop Custom Points...');
    } else {
	$('#map-custom-points').text('Start Custom Points...');
    }
    updateDeleteCustomPoints();
}

function updateDeleteCustomPoints() {
    if (DELETE_CUSTOM_POINTS) {
	$('#map-custom-points-delete').text('Deleting Custom Points...');
    } else {
	$('#map-custom-points-delete').text('Adding Custom Points...');
    }
}

var ROBOTS = {};

var WATCHED_ROBOTS = [];

var ROBOT_TYPES = {'KOBUKI':
		   {'width': 0.3515,
		    'height': 0.3515,
		    'image_url': 'robots/KOBUKI/image.png',
		   },
		   'NEATO':
		   {'width': 0.3302,
		    'height': 0.3175,
		    'image_url': 'robots/NEATO/image.png'
		   },
		   'MISTY_WOZ1':
		   {'width': 0.256,
		    'height': 0.238545454,
		    'image_url': 'robots/MISTY_WOZ1/image.png',
		   },
		   'PARALLAX_ARLO':
		   {'width': 18.0 * (2.54/ 100.0),
		    'height': 18.0 * (2.54/ 100.0),
		    'image_url': 'robots/PARALLAX_ARLO/image.png'},
                   'RUBBERMAID_CART':
		   {'width': 18.0 * (2.54/ 100.0),
		    'height': 39.0 * (2.54/ 100.0) * 2,
		    'image_url': 'robots/RUBBERMAID_CART/image.png'},
                   'PHONE':
		   {'width': 10.0 * (2.54/ 100.0),
		    'height': 10.0 * 800 / 468 * (2.54/ 100.0),
		    'image_url': 'robots/PHONE/image.png'}
		  };

var ROBOT_VPS = {};

function onVpsUpdate() {
    $('#main-row-vps').html('');
    for (var id in ROBOT_VPS) {
	var tf = ROBOT_VPS[id].tf;
	$('<li></li>').append(
	    $('<a></a>').text(ROBOT_VPS[id].name)
		.data('target', '#maps/VPS:' + tf.px + ":" + tf.py + ":" + tf.pz)
		.click(function() {
		    CURRENT_PAGE = "MAIN";
		    location.hash = $(this).data('target');
		    render();
		}))
	    .appendTo($('#main-row-vps'));
    }
}

function robotUpdated(remove) {
    return function(robotData) {
	var mapId = getMapUUID();
	var id = robotData.getKey();
	var rv = robotData.val();
	if (!id || !rv)
	    return;

	if (remove) {
	    if (id in ROBOT_VPS) {
		delete ROBOT_VPS[id];
		onVpsUpdate();
	    }
	    deleteRobot(id);
	    return;
	}
	
	var robotType = id.split(":")[0].trim();

	if (!(robotType in ROBOT_TYPES)) {
	    console.log("Robot type invalid: " + robotType);
	    if (id in ROBOT_VPS) {
		delete ROBOT_VPS[id];
		onVpsUpdate();
	    }
	    deleteRobot(id);
	    return;
	}
	
	var rt = ROBOT_TYPES[robotType];


	if (rv == null) {
	    console.log("Robot deleted from firebase");
	    deleteRobot(id);
	    if (id in ROBOT_VPS) {
		delete ROBOT_VPS[id];
		onVpsUpdate();
	    }
	} else if (!rv.last_update_time || get_current_time() - ROBOT_UPDATE_TIMEOUT > rv.last_update_time) {
	    console.log("Robot timed out: " + rv['last_update_time']);
	    if (id in ROBOT_VPS) {
		delete ROBOT_VPS[id];
		onVpsUpdate();
	    }
	    deleteRobot(id);
	} else if (!rv.map || rv.map != mapId) {
	    if (rv.map == 'VPS') {
		ROBOT_VPS[id] = {'tf': rv['tf'], 'name': rv['name']};
		onVpsUpdate();
	    }
	    deleteRobot(id);
	} else {
	    if (id in ROBOTS) {
		$('#map-robot-select').children().each(function (e) {
		    if ($(this).val() == id) {
			$(this).text(rv.name ? rv.name : id);
		    }
		});
	    } else {
		$('<div class="robot"></div>')
		    .css("width", rt['width'] + 'px').css("height", rt['height'] + "px")
		    .css("position", "absolute").css("display", "none").data('uuid', id)
		    .css("background-image", 'url("' + rt['image_url'] + '")')
		    .css("background-size", "cover").css("z-index", "1010")
		    .click(function() {
			if (SELECTED_TYPE != "CUSTOM_POINTS") {
			    setSelected("ROBOT", $(this).data('uuid'));
			    updateSelectedRobot();
			}
		    }).data('robot-type', robotType).appendTo($('#map-content'));
				
		$('#map-robot-select').append($('<option></option>').val(id)
					      .text(rv.name ? rv.name : id));
	    }
	    ROBOTS[id] = {"name": rv.name, 'timestamp': Date.now()};
	    $('div.robot').each(function() {
		if ($(this).data('uuid') == rv["uuid"] && rv.tf) {
		    // TODO: this quaternion multiplication stuff is possibly bad
		    var pose = projectPoint(rv["tf"]);
		    $(this).css("display", "block")
			.css("transform", "rotate(" + (pose[2] * 180.0 / Math.PI) + "deg)")
			.css("left", -pose[0] - $(this).width() / 2)
			.css("top", pose[1] - $(this).height() / 2);
		}
	    });
	}  
    };
}

setInterval(function() {
    for (var robotId in ROBOTS) {
	var rv = ROBOTS[robotId];
	if (rv.timestamp && Date.now() - ROBOT_UPDATE_TIMEOUT > rv.timestamp) {
	    if (robotId in ROBOT_VPS) {
		delete ROBOT_VPS[robotId];
		onVpsUpdate();
	    }
	    deleteRobot(robotId);
	}
    }
}, 1000);

function deleteRobot(fixedRobotId) {
    $('div.robot').each(function() {
	if ($(this).data('uuid') == fixedRobotId) {
	    $(this).remove();
	}
    });
    $('#map-robot-select').children().each(function (e) {
	if ($(this).val() == fixedRobotId) {
	    $(this).remove();
	}
    });
    delete ROBOTS[fixedRobotId];
}

var OBJECTS = {};
function objectUpdated(uuid, removeObject) {
    return function(objectData) {
	if (!CURRENT_USER) {
	    return;
	}

	if (objectData) {
	    if (removeObject) {
		if (objectData.key in OBJECTS) {
		    delete OBJECTS[objectData.key];
		}
	    } else {
		OBJECTS[objectData.key] = objectData.val();
	    }
	}

	$('div.object-display').remove();

	$('#map-object-select').html('');
	$('#map-object-select').append($('<option selected></option>').prop('value', "").text("None"));
	for (var object_uuid in OBJECTS) {
	    var object = OBJECTS[object_uuid];
	    if (("type" in object) && (object.type in OBJECT_TYPES)) {
		var opt = $('<option></option>').prop('value', object.uuid).text(object.uuid);
		if (SELECTED_ITEM == object.uuid && SELECTED_TYPE == "OBJECT") {
		    opt.prop('selected', true);
		}
		if (object.variables) {
		    if (object.variables.name) {
			opt.text(object.variables.name);
		    }
		}
		$('#map-object-select').append(opt);

		for (var display_name in OBJECT_TYPES[object.type].display){
		    var display = OBJECT_TYPES[object.type].display[display_name];
		    if (display.type == "point") {
			if (!object.variables) {
			    continue;
			}
			var pt = object.variables[display.location];
			if (!pt) {
			    continue;
			}
			var x = parseFloat(pt.px);
			var y = parseFloat(pt.py);
			if (display.offset) {
			    x += parseFloat(display.offset.split(",")[0]);
			    y += parseFloat(display.offset.split(",")[1]);
			}
			var size = display.size ? display.size : 0.07;
			$('<div class="object-display"></div>')
			    .css('background-color', display.color)
			    .css('height', size + 'px')
			    .css('width', size + 'px')
			    .css('position', 'absolute')
			    .css('z-index', '1004')
			    .css('left', -x - size / 2 - 0.01)
			    .css('top', y - size / 2 - 0.01)
			    .appendTo($('#map-content'));
		    } else if (display.type == "text") {
			if (!object.variables) {
			    continue;
			}
			var pt = object.variables[display.location];
			if (!pt) {
			    continue;
			}
			var x = parseFloat(pt.px);
			var y = parseFloat(pt.py);
			if (display.offset) {
			    x += parseFloat(display.offset.split(",")[0]);
			    y += parseFloat(display.offset.split(",")[1]);
			}
			$('<div class="object-display"></div>')
			    .css('color', display.color)
			    .css('height', '0.2px')
			    .css('width', '1px')
			    .css('position', 'absolute')
			    .css('z-index', '1004')
			    .css('left', -x - 0.5)
			    .css('top', y - 0.1)
			    .css('text-align', 'center')
			    .text(object.variables[display.variable])
			    .css('font-size', '0.15px')
			    .appendTo($('#map-content'));
		    } else {
			alert("Invalid object display: " + display.type);
		    }
		}
		
	    }
	}
	
    };
}

var OVERLAYS = {};
function overlayUpdated(uuid, removeObject) {
    return function(overlayData) {
	if (!CURRENT_USER) {
	    return;
	}
	
	if (!overlayData) {
	    return;
	}
	if (removeObject) {
	    if (overlayData.key in OVERLAYS) {
		delete OVERLAYS[overlayData.key];
	    }
	} else {
	    OVERLAYS[overlayData.key] = overlayData.val();
	    var overlayExists = false;
	    $('img.map-overlay').each(function() {
		if ($(this).data('uuid') == overlayData.key) {
		    overlayExists = true;
		    updateOverlay($(this), overlayData);
		}
	    });
	    if ($('img.map-overlay').filter(function() {
		return $(this).data('uuid') == overlayData.key; }).length == 0) {
		getStorage(['overlays', CURRENT_USER.uid, getMapUUID(), overlayData.key])
		    .getDownloadURL().then(function(url) {
			if ($('img.map-overlay').filter(function() {
			    return $(this).data('uuid') == overlayData.key; }).length != 0) {
			    console.log("Duplicate ignored");
			    return;
			}
			$('<img class="map-overlay"></img>')
			    .prop("src", url).css('position', 'absolute')
			    .css("background-size", "cover").css("z-index", "1010")
			    .data('uuid', overlayData.key)
			    .data('scale', overlayData.val().scale)
			    .load(function() {
				$(this).data('raw-width', $(this).width());
				$(this).data('raw-height', $(this).height());
				updateOverlay($(this), overlayData);
			    }).appendTo($('#map-content'));
		    });
	    }
	}
    }
}

function updateOverlay(selector, data) {
    var val = data.val();

    if (!selector.data('timestamp') || val.timestamp > selector.data('timestamp')) {
	selector.data('timestamp', val.timestamp);
	selector.data('scale', val.scale);
    }
    var scale = selector.data('scale');
    if (!scale || scale < 0.00000000000000001) {
	scale = 1.0;
    }
    selector.width(selector.data('raw-width') * scale);
    selector.height(selector.data('raw-height') * scale);
    var pose = projectPoint(val.tf);
    selector.css("display", "block")
	.css("transform", "rotate(" + (pose[2] * 180.0 / Math.PI) + "deg)")
	.css("left", -pose[0])
	.css("top", pose[1]);
}


function addOverlay() {
    var mapUuid = getMapUUID();
    if (!mapUuid) {
	return;
    }
    if ($('#map-add-overlay')[0].files.length != 1) {
	console.log("Wrong number of files");
	return;
    }
    if (!CURRENT_USER) {
	return;
    }
    var file = $('#map-add-overlay')[0].files[0];
    $('#map-add-overlay').prop('disabled', true);

    var fname = file.name.split(".");
    if (fname.length == 0 || fname.length == 1) {
	fname = fname.join(".");
    } else {
	fname = fname.splice(0, fname.length - 1).join(".");
    }

    var overlayUuid = guid();
    var store = getStorage(["overlays", CURRENT_USER.uid, mapUuid, overlayUuid]);
    store.put(file).then(
	function(user_uid, mapUuid, overlayUuid) {
	    return function(snapshot) {
		ref = getDatabase(['overlays', user_uid, mapUuid, overlayUuid]);
		ref.set({'name': fname, 'scale': 1.0,
			 'tf': {'px': 0, 'py': 0, 'pz': 0, 'qx': 0, 'qy': 0, 'qz': 0, 'qw': 0},
			  'timestamp': firebase.database.ServerValue.TIMESTAMP,})
		    .then(function() {
			$('#map-add-overlay').val('');
			$('#map-add-overlay').prop('disabled', false);
		    });
	    };
	}(CURRENT_USER.uid, mapUuid, overlayUuid));
}

MAP_WATCH = new WatchManager();

WORLD_BYTES = null;
WORLD_BYTES_START_CUSTOM = 0;
WORLD_BYTES_END_CUSTOM = 0;

function reloadMapData() {
    if (!CURRENT_USER) {
	console.log("Reload map with no user");
	return;
    }
    var uuid = getMapUUID();
    if (!uuid) {
	console.log("Reload map with no map UUID");
	return;
    }
    
    MAP_WATCH.clearWatch();
    WORLD_BYTES = null;
    OBJECTS = {};
    ROBOTS = {};
    onObjectTypeUpdate();
    setSelected("ROBOT", "");
    $('svg.floorplan').remove();
    $('div.robot').remove();
    $('div.maptemp').remove();
    $('#map-robot-select').children().remove();
    $('#map-robot-select').append($('<option selected></option>').val("").text("None"));
    $('#map-object-select').children().remove();
    $('#map-object-select').append($('<option selected></option>').val("").text("None"));
    $('#map-robot').css('display', 'none');
    MAP_WATCH.addWatchChildren(getDatabase(['objects', CURRENT_USER.uid, uuid]),
			       function(remove) { return objectUpdated(uuid, remove); });
    MAP_WATCH.addWatchChildren(getDatabase(['overlays', CURRENT_USER.uid, uuid]),
			       function(remove) { return overlayUpdated(uuid, remove); });
    $('#map-map-name').text('');
    
    if (uuid == 'VPS') {
	var MAP_WIDTH = 10;
	GLOBAL_X_MAX = MAP_WIDTH;
	GLOBAL_X_MIN = -MAP_WIDTH;
	GLOBAL_Y_MAX = MAP_WIDTH;
	GLOBAL_Y_MIN = -MAP_WIDTH;
	GLOBAL_TFS = [];
	GLOBAL_FP = [];
	GLOBAL_CUSTOM_TFS = [];
	$('svg.floorplan').remove();
	$('div.ctf-display').remove();
	$('div.tf-display').remove();
	updateMapMargins();
	$('#map-map-name').text('VPS Map');
	return;
    }

    getDatabase(['maps', CURRENT_USER.uid, uuid])
	.once("value", function(snapshot) {
	    var val = snapshot.val();
	    $('#map-map-name').text('Map name: ' + val.name);
	    loadMapDetailed(uuid, val);
	});

}

function loadMapDetailed(uuid, metadata) {
    getStorage(['maps', CURRENT_USER.uid, uuid, 'dat']).getDownloadURL()
	.then(function(url) {
	    var xhr = new XMLHttpRequest();
	    xhr.responseType = 'arraybuffer';
	    xhr.onload = function(event) {
		var arraybuffer = xhr.response;
		if (!arraybuffer) return;
		var bytes = new DataView(arraybuffer);
		if (!bytes.byteLength) return;
		WORLD_BYTES = bytes;

		GLOBAL_X_MAX = 0.0;
		GLOBAL_X_MIN = 0.0;
		GLOBAL_Y_MAX = 0.0;
		GLOBAL_Y_MIN = 0.0;
		GLOBAL_TFS = [];
		
		if (bytes.getUint8(0) == 0) {
		    readStyle0Map(bytes);
		} else if (bytes.getUint8(0) == 1
			   || bytes.getUint8(0) == 2
			   || bytes.getUint8(0) == 3) {
		    readStyle123Map(bytes);
		} else {
		    console.log("Invalid map style: " + bytes.getUint8(0));
		}

		drawTfs();
		drawCustomTfs();
		drawFloorplan();
		setHideBackground(metadata["background_hidden"] == true);
	    };
	    xhr.open('GET', url);
	    xhr.send();
	}).catch(function(error) {
	    // Handle any errors
	    console.log("Error loading: " + uuid + " " + error);
	});
}


function setMap() {
    CURRENT_PAGE = "MAIN";
    location.hash = 'maps/' + $(this).data('map-uuid');
    render();
}

function deleteMap() {
    if (!CURRENT_USER) {
	return;
    }
    if (confirm("Delete map '" + $(this).data('map-name') + "'? The map will"
		+ " not actually be deleted from the server and may be recovered. "
	        + " the map uuid is '" + $(this).data('map-uuid') + "'.")) {
	getDatabase(["maps", CURRENT_USER.uid, $(this).data('map-uuid'), 'deleted'])
	    .set('true');
    }
}


function listMaps(data) {
    if (!CURRENT_USER) {
	return;
    }
    $('#main-row-maps').html('');
    var str = data.val();
    for (var map_uuid in str) {
	var map = str[map_uuid];
	if ("deleted" in map) continue;
	$('#main-row-maps').append(
	    $("<li></li>").append($("<a></a>").text(map.name)
				  .data("map-uuid", map.uuid)
				  .click(setMap))
		.append(" (").append($("<a></a>").text("delete")
				     .data("map-uuid", map_uuid)
				     .data("map-name", map.name)
				     .click(deleteMap)).append(")"));
    }
}


function onObjectTypeUpdate() {
    $('#add-object-type-select').html('');
    for (var t in OBJECT_TYPES) {
	$('#add-object-type-select').append($('<option></option>').prop('value', t).text(t));
    }
    objectUpdated(getMapUUID(), false)(null);
}

function objectSelectorChange() {
    setSelected("OBJECT", $('#map-object-select').val());
}

function deleteObject() {
    if (!CURRENT_USER) return;
    if (SELECTED_TYPE != "OBJECT" || SELECTED_ITEM == "") return;
    var map_uuid = getMapUUID();
    if (!map_uuid) return;
    if (confirm("Are you sure you want to delete this object?")) {
	getDatabase(["objects", CURRENT_USER.uid, map_uuid, SELECTED_ITEM]).remove();
	setSelected("OBJECT", "");
    }
}

function addObject() {
    $('#object-add').modal("show");
}

function addObjectModal() {
    $('#object-add').modal("hide");
    var object_type = $('#add-object-type-select').val();
    var map_uuid = getMapUUID();
    if (object_type in OBJECT_TYPES && CURRENT_USER && map_uuid) {
	var uuid = guid();
	getDatabase(["objects", CURRENT_USER.uid, map_uuid, uuid])
	    .set({"uuid": uuid, "type": object_type});
    } else if (!(object_type in OBJECT_TYPES)) {
	console.log("Invalid object type: " + object_type);
    }
}

var edit_object = null;
function editObject() {
    var object_uuid = SELECTED_ITEM;
    if (object_uuid == "" || SELECTED_TYPE != "OBJECT") {
	return;
    }
    var map_uuid = getMapUUID();
    if (!map_uuid) {
	return;
    }

    if (!(object_uuid in OBJECTS)) {
	return;
    }

    var object = OBJECTS[object_uuid];
    if (!(object.type in OBJECT_TYPES)) {
	return;
    }

    if (!CURRENT_USER) {
	return;
    }

    edit_object = {'object': object_uuid, 'map': map_uuid,
		   'type': OBJECT_TYPES[object.type],
		   'user': CURRENT_USER.uid};
    
    writeParameterList($('#object-edit-body'),
		       OBJECT_TYPES[object.type]["variables"],
		       object.variables,
		       function() {
			   $('#object-edit').modal("hide");
		       },
		       function() {
			   $('#object-edit').modal("show");
		       });
    $('#object-edit').modal("show");    
}

function editObjectModal() {
    var variables_out = {};
    if (edit_object) {
        readParameterList($('#object-edit-body'),
			  edit_object.type.variables, variables_out);
	getDatabase(["objects", edit_object.user, edit_object.map,
		     edit_object.object, "variables"]).set(variables_out);
    }
    edit_object = null;
    $('#object-edit').modal("hide");		      
}


function render() {
    if (CURRENT_USER) {
	CURRENT_PAGE = "MAIN";
	var lhash = location.hash.split("/")[0].replace("#", "").trim();
	if (lhash == "maps") {
	    CURRENT_PAGE = "MAP";
	    reloadMapData();
	} else {
	    MAP_WATCH.clearWatch();
	}
    }
    
    $('#login-row').css("display", CURRENT_PAGE == "LOGIN" ? "" : "none");
    $('#register-row').css("display", CURRENT_PAGE == "REGISTER" ? "" : "none");
    $('#main-row').css("display", CURRENT_PAGE == "MAIN" ? "" : "none");
    $('#map-row').css("display", CURRENT_PAGE == "MAP" ? "" : "none");
    
    $('#nav-bar-logged-out').css("display", (CURRENT_USER == null && !NO_USER) ? "" : "none");
    $('#nav-bar-logged-in').css("display", (CURRENT_USER != null && !NO_USER) ? "" : "none");
    
    if (CURRENT_PAGE != "MAP") {
	$('div.tf-display').remove();
	$('div.ctf-display').remove();
	$('div.goal-display').remove();
	$('div.object-display').remove();
    }

    $('#map-settings-region').css("display", "none");

    resizeMap();
}


render();

USER_WATCH = new WatchManager();

$('.current-email').text('loading...');
firebase.auth().onAuthStateChanged(function(user) {
    USER_WATCH.clearWatch();
    CURRENT_USER = user;
    if (CURRENT_USER) {
	USER_WATCH.addWatchChildren(
	    getDatabase(["robots", CURRENT_USER.uid]),
	    function (remove) { return robotUpdated(remove); });
	USER_WATCH.addWatch(getDatabase(["maps", CURRENT_USER.uid]), "value", listMaps);
	USER_WATCH.addWatch(getDatabase(["sounds", CURRENT_USER.uid]), "value", listSounds);
	USER_WATCH.addWatch(getDatabase(["animations", CURRENT_USER.uid]), "value", listAnimations);
	USER_WATCH.addWatch(getDatabase(["global_sounds"]), "value", listGlobalSounds);
	USER_WATCH.addWatch(getDatabase(["global_animations"]), "value", listGlobalAnimations);
	$('.current-email').text(CURRENT_USER.email);
    } else {
	$('.current-email').text('loading...');
    }

    NO_USER = false;
    if (user) {
	if (CURRENT_PAGE == "LOGIN" || CURRENT_PAGE == "REGISTER" || CURRENT_PAGE == "") {
	    CURRENT_PAGE = "MAIN";
	}
    } else {
	CURRENT_PAGE = "LOGIN";
    }
    render();
});


function resizeMap() {
    var header = $('body').height() - $('#map-scroll-region').height()
    var height = window.innerHeight - 40 - header;
    $('#map-scroll-region').css('height', height + "px");
}

function deleteFileFromManager() {
    var file = $(this).data('uuid');
    if (!file) {
	return;
    }
    if (!CURRENT_USER) {
	return;
    }
    if (confirm("Delete '" + $(this).data('name') + "'?")) { 
	var f = function (db_bin, t_folder, user) {
	    var db = null; 
	    if (user) { 
		db = getDatabase([db_bin, user]);
	    } else {
		db = getDatabase([db_bin]);
	    }
	    db.child(file).remove().then(
		function (folder, user) {
		    return function () {
			var store = null;
			if (user) {
			    store = getStorage([folder, user]);
			} else {
			    store = getStorage([folder]);
			}
			if (store) {
			    store.child(file).delete();
			}
		    }
		}(t_folder, user));
	};
	f($(this).data('db_key'), $(this).data('storage_folder'), $(this).data('user'));
    }
}

function listSounds(data) {
    fileManager(data, $('#main-row-sounds'), "sounds", "sounds", true, false, "User Sound");
}
function listAnimations(data) {
    fileManager(data, $('#main-row-animations'), "animations", "animations", true, true, "User Animation");
}

function listGlobalSounds(data) {
    fileManager(data, $('#main-row-global-sounds'), "global_sounds", "global_sounds", false, false, "Global Sound");
}
function listGlobalAnimations(data) {
    fileManager(data, $('#main-row-global-animations'), "global_animations", "global_animations", false, true, "Global Animation");
}

function fileManager(data, main_row, db_key, storage_folder, user, editor, editor_name) {
    if (!CURRENT_USER) {
	console.log("No current user for sounds");
	return;
    }
    main_row.html('');
    var files = data.val();
    for (var file_name in files) {
	var file = files[file_name];
	var row = $("<li></li>").append(file.name).append(" (");
	row.append($("<a></a>").text("delete")
		   .data("name", file.name)
		   .data("uuid", file_name)
		   .data("db_key", db_key)
		   .data("user", user ? CURRENT_USER.uid : false)
		   .data("storage_folder", storage_folder)
		   .click(deleteFileFromManager));
	if (editor) {
	    row.append(", ");
	    row.append($("<a></a>").text("edit")
		       .data("name", file.name)
		       .data("uuid", file_name)
		       .data("db_key", db_key)
		       .data("editor_name", editor_name)
		       .data("user", user ? CURRENT_USER.uid : false)
		       .data("storage_folder", storage_folder)
		       .click(editFileFromManager));
	}
	row.append(")");
	main_row.append(row);
    }
}


var EDITOR_DB_KEY = null;
var EDITOR_STORAGE = null;
var EDITOR_USER = null;
var EDITOR_UUID = null;
var EDITOR_INITIAL = null;
function editFileFromManager() {
    if (!checkEditorChange()) {
	return;
    }
    $('#main-row-editor').css('display', 'none');
    
    EDITOR_DB_KEY = $(this).data("db_key");
    EDITOR_STORAGE = $(this).data('storage_folder');
    EDITOR_USER = $(this).data('user');
    EDITOR_UUID = $(this).data('uuid');

    $('#main-row-editor-header').text($(this).data('editor_name') + ': ' + $(this).data('name'));

    var store = null;
    if (EDITOR_USER) {
	store = getStorage([EDITOR_STORAGE, EDITOR_USER, EDITOR_UUID]);
    } else {
	store = getStorage([EDITOR_STORAGE, EDITOR_UUID]);
    }
    store.getDownloadURL()
	.then(function (myUuid) {
	    return function(url) {
		console.log(url);
		var xhr = new XMLHttpRequest();
		xhr.responseType = 'text';
		xhr.onload = function(event) {
		    if (EDITOR_UUID == myUuid) { // TODO check all four
			EDITOR_INITIAL = xhr.response;
			$('#main-row-editor-text').val(xhr.response);
			$('#main-row-editor').css('display', '');
		    }
		}
		xhr.open('GET', url);
		xhr.send();
	    };
	}(EDITOR_UUID));
}

function checkEditorChange() {
    if ($('#main-row-editor-text').val() == EDITOR_INITIAL || EDITOR_INITIAL == null) {
	return true;
    }
    return confirm("Are you sure, you have unsaved changes?");
}

function editorSave() {
    var store = null;
    if (EDITOR_USER) {
	store = getStorage([EDITOR_STORAGE, EDITOR_USER, EDITOR_UUID]);
    } else {
	store = getStorage([EDITOR_STORAGE, EDITOR_UUID]);
    }
    var saved = $('#main-row-editor-text').val();
    store.putString(saved)
	.then(function(db_key, user, uuid, saved) {
	    return function () {
		var db = null;
		if (user) {
		    db = getDatabase([db_key, user, uuid, "timestamp"]);
		} else {
		    db = getDatabase([db_key, uuid, "timestamp"]);
		}
		db.set(firebase.database.ServerValue.TIMESTAMP)
		    .then(function() {
			if (saved == $('#main-row-editor-text').val()) {
			    EDITOR_INITIAL = saved;
			}
		    });
	    };
	}(EDITOR_DB_KEY, EDITOR_USER, EDITOR_UUID, saved));
}

function editorClose() {
    if (checkEditorChange()) {
	$('#main-row-editor').css('display', 'none');
    }
}


function soundFilesUploaded() {
    filesUploaded($('#main-row-sound-files'), 'sounds', 'sounds', true);
}
function animationFilesUploaded() {
    filesUploaded($('#main-row-animation-files'), 'animations', 'animations', true);
}
function globalSoundFilesUploaded() {
    filesUploaded($('#main-row-global-sound-files'), 'global_sounds', 'global_sounds', false);
}
function globalAnimationFilesUploaded() {
    filesUploaded($('#main-row-global-animation-files'), 'global_animations', 'global_animations', false);
}

function filesUploaded(upload_queue, db_key, storage_folder, user) {
    if (upload_queue[0].files.length != 1) {
	console.log("Wrong number of files");
	return;
    }
    if (!CURRENT_USER) {
	return;
    }
    var file = upload_queue[0].files[0];
    upload_queue.prop('disabled', true);

    if (file.name.length >= 40) {
	alert("File name is too long: " + file.name.length + " as it is "
	      + file.name.length + " characters long. Must be 40 or less.");
	return;
    }

    var fname = file.name.split(".");
    if (fname.length == 0 || fname.length == 1) {
	fname = fname.join(".");
    } else {
	fname = fname.splice(0, fname.length - 1).join(".");
    }

    var fname_lower = fname.toLowerCase();
    
    var bytes = [];
    var charCode;
    for (var i = 0; i < fname_lower.length; ++i) {
	charCode = fname_lower.charCodeAt(i);
	bytes.push((charCode & 0xFF00) >> 8);
	bytes.push(charCode & 0xFF);
    }
    var fn = "";
    for (var i = 0; i < bytes.length; i++) {
	var c = bytes[i].toString(16);
	if (c.length == 0) { c = '0' + c; }
	if (c.length == 1) { c = '0' + c; }
	fn += c;
    }
    
    var db = null;
    if (user) {
	db = getStorage([storage_folder, CURRENT_USER.uid]);
    } else {
	db = getStorage([storage_folder]);
    }
    db.child(fn).put(file).then(
	function(user_uid) {
	    return function(snapshot) {
		var ref = null;
		if (user_uid) {
		    ref = getDatabase([db_key, user_uid]);
		} else {
		    ref = getDatabase([db_key]);
		}
		ref.child(fn)
		    .set({'name': fname, 'id': fn,
			  'timestamp': firebase.database.ServerValue.TIMESTAMP,})
		    .then(function() {
			upload_queue.val('');
			upload_queue.prop('disabled', false);
		    });
	    };
	}(user ? CURRENT_USER.uid : false));    
}

function customPointClickGenerate(event) {
    var end = mouseToWorld(event);
    if (DELETE_CUSTOM_POINTS) {
	var have_hit = true;
	while (have_hit) {
	    have_hit = false;
	    var rm = 0;
	    for (var i in GLOBAL_CUSTOM_TFS) {
		var dx = end.x - GLOBAL_CUSTOM_TFS[i].px;
		var dy = end.y - GLOBAL_CUSTOM_TFS[i].py;
		var dist = (dx * dx) + (dy * dy);
		if (dist < CUSTOM_POINT_SIZE * CUSTOM_POINT_SIZE / 4) {
		    have_hit = true;
		    rm = i;
		    break;
		}
	    }
	    if (have_hit) {
		GLOBAL_CUSTOM_TFS.splice(i, 1);
		CHANGED_CUSTOM_TFS = true;
	    }
	}
    } else {
	var write_object = {
	    'ts': -1, 'px': end.x, 'py': end.y, 'pz': 0.0,
	    'qx': 0.0, 'qy': 0.0, 'qz': 0.0, 'qw': 1.0};
	GLOBAL_CUSTOM_TFS.push(write_object);
	CHANGED_CUSTOM_TFS = true;
    }
    drawCustomTfs();
}

CHANGED_CUSTOM_TFS = false;

function customPointsDelete() {
    DELETE_CUSTOM_POINTS = !DELETE_CUSTOM_POINTS;
    updateDeleteCustomPoints();
}

function customPoints() {
    if (SELECTED_TYPE != "CUSTOM_POINTS") {
	if (WORLD_BYTES != null) {
	    if (WORLD_BYTES.getUint8(0) == 1 || WORLD_BYTES.getUint8(0) == 2 || WORLD_BYTES.getUint8(0) == 3) {
		CHANGED_CUSTOM_TFS = false;
		setSelected("CUSTOM_POINTS", "");
		$('#map-scroll-region').unbind('click').click(customPointClickGenerate);
	    } else {
		alert("Invalid world version: " + WORLD_BYTES.getUint8(0));
	    }
	}
    } else if (CHANGED_CUSTOM_TFS && WORLD_BYTES) {
	$('#map-custom-points').prop('disabled', true);
	$('#map-custom-points-delete').prop('disabled', true);

	var off = 0;
	if (WORLD_BYTES.getUint8(0) == 1) {
	    off = 4;
	}

	var out_bytes = new ArrayBuffer(WORLD_BYTES.byteLength
					- WORLD_BYTES_END_CUSTOM
					+ WORLD_BYTES_START_CUSTOM
					+ off + 4 + GLOBAL_CUSTOM_TFS.length * 66);
	var out_data = new DataView(out_bytes);
	out_data.setUint8(0, 3);
	if (WORLD_BYTES.getUint8(0) == 1) {
	    out_data.setInt32(1, 0);
	}
	for (var i = 1; i < WORLD_BYTES_START_CUSTOM; i++) {
	    out_data.setUint8(i + off, WORLD_BYTES.getUint8(i));
	}
	
	var end_length = WORLD_BYTES.byteLength - WORLD_BYTES_END_CUSTOM;
	for (var i = 0; i < end_length; i++) {
	    out_data.setUint8(out_bytes.byteLength - end_length + i,
			      WORLD_BYTES.getUint8(WORLD_BYTES_END_CUSTOM + i));
	}
	i = WORLD_BYTES_START_CUSTOM + off;
	//console.log("Have gap: " + (out_bytes.byteLength - WORLD_BYTES.byteLength + WORLD_BYTES_END_CUSTOM - i));
	out_data.setInt32(i, GLOBAL_CUSTOM_TFS.length);
	i += 4;
	for (var k = 0; k < GLOBAL_CUSTOM_TFS.length; k++) {
	    var tf = GLOBAL_CUSTOM_TFS[k];
	    out_data.setUint8(i++, 3); //Pos len
	    out_data.setUint8(i++, 4); //Pos len
	    out_data.setFloat64(i, -1.0);
	    i += 8;
	    out_data.setFloat64(i, tf.px);
	    i += 8;
	    out_data.setFloat64(i, tf.py);
	    i += 8;
	    out_data.setFloat64(i, tf.pz);
	    i += 8;
	    out_data.setFloat64(i, tf.qx);
	    i += 8;
	    out_data.setFloat64(i, tf.qy);
	    i += 8;
	    out_data.setFloat64(i, tf.qz);
	    i += 8;
	    out_data.setFloat64(i, tf.qw);
	    i += 8;
	}
	if (i != out_bytes.byteLength - (WORLD_BYTES.byteLength - WORLD_BYTES_END_CUSTOM)) {
	    alert("Byte count invalid: got " + i + " wanted "
		  + (out_bytes.byteLength - (WORLD_BYTES.byteLength - WORLD_BYTES_END_CUSTOM))
		  + " start: " + WORLD_BYTES_START_CUSTOM
		  + " end: " + WORLD_BYTES_END_CUSTOM);
	    return;
	}

	getStorage(['maps', CURRENT_USER.uid, getMapUUID(), 'dat']).put(new Uint8Array(out_bytes))
	    .then(function(snapshot) {
		getDatabase(["maps", CURRENT_USER.uid, getMapUUID(), "timestamp"])
		    .set(firebase.database.ServerValue.TIMESTAMP)
		    .then(function() {
			setSelected("ROBOT", "");
			$('#map-scroll-region').unbind('click').click(defaultClickPointGenerate);
		    });
	    });
    } else if (!CHANGED_CUSTOM_TFS) {
	setSelected("ROBOT", "");
	$('#map-scroll-region').unbind('click').click(defaultClickPointGenerate);
    }
}

function mapSettingsDisplay() {
    if ($('#map-settings-region').css("display") == "none") {
	$('#map-settings-region').css("display", "");
    } else {
	$('#map-settings-region').css("display", "none");
    }
    resizeMap();
}

function setHideBackground(hidden) {
    if (hidden) {
	$('svg.floorplan').css("display", "none");
	$('#map-toggle-svg').text('Show Background');
    } else {
	$('svg.floorplan').css("display", "");
	$('#map-toggle-svg').text('Hide Background');
    }
}

function mapToggleSvg() {
    var uuid = getMapUUID();
    if (uuid && CURRENT_USER) {
	var newHide = $('svg.floorplan').css("display") != "none";
	setHideBackground(newHide);
	getDatabase(["maps", CURRENT_USER.uid, uuid, "background_hidden"]).set(newHide);
    }
}

resizeMap();
$(window).resize(function() {
    resizeMap();
});



function zoomIn() {
    $('#map-content').css('zoom', $('#map-content').css('zoom') * 1.2);
}
function zoomOut() {
    $('#map-content').css('zoom', $('#map-content').css('zoom') / 1.2);
}


