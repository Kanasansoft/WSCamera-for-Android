var display;
var ws;

function initCrossBrowser() {
	window.Intent = window.Intent || window.WebKitIntent;
	window.navigator.startActivity = window.navigator.startActivity || window.navigator.webkitStartActivity;
	window.intent = window.intent || window.webkitIntent;
}

var createObjectURL =
	window.URL       && window.URL.createObjectURL       ? function (file) { return window.URL.createObjectURL(file);       } :
	window.webkitURL && window.webkitURL.createObjectURL ? function (file) { return window.webkitURL.createObjectURL(file); } :
	undefined;

function onopen() {
	ws.send("request_image");
}

function onclose() {
}

function onmessage(e) {
	display.src = createObjectURL(e.data);
	ws.send("request_image");
}

function onunload() {
	if (ws) {
		ws.close();
	}
}

function setupWebIntents() {
	if (!intent) {
		return;
	}
	var textNode = document.createTextNode("When you click you can capture this video.");
	var heading = document.createElement("h1");
	var body = document.getElementById("body");
	heading.appendChild(textNode);
	body.insertBefore(heading, body.firstChild);
	display.addEventListener(
		"click",
		function(){
			if (display.src == "") {
				return;
			}
			var computedStyle = document.defaultView.getComputedStyle(display, "");
			var canvas = document.createElement("canvas");
			var height = parseInt(computedStyle.height);
			var width = parseInt(computedStyle.width);
			canvas.height = height;
			canvas.width = width;
			var context = canvas.getContext('2d');
			var image = new Image();
			image.addEventListener(
				"load",
				function(){
					context.drawImage(image, 0, 0, width, height);
					intent.postResult(canvas.toDataURL("image/jpeg"));
				},
				false
			);
			image.src = display.src;
		},
		false
	);
	display.style.cursor = "pointer";
}

function initialize() {

	initCrossBrowser();

	display = document.getElementById("display");

	var protocol = (location.protocol == "https:") ? "wss" : "ws";
	var host     = location.host;
	ws           = new WebSocket(protocol + "://" + host + "/wscamera/ws");

	ws    .addEventListener("open"   , onopen   , false);
	ws    .addEventListener("close"  , onclose  , false);
	ws    .addEventListener("message", onmessage, false);
	window.addEventListener("unload" , onunload , false);

	setupWebIntents();

}

window.addEventListener("load",initialize,false);
