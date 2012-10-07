var display;
var ws;

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

function initialize() {

	display = document.getElementById("display");

	var protocol = (location.protocol == "https:") ? "wss" : "ws";
	var host     = location.host;
	ws           = new WebSocket(protocol + "://" + host + "/wscamera/ws");

	ws    .addEventListener("open"   , onopen   , false);
	ws    .addEventListener("close"  , onclose  , false);
	ws    .addEventListener("message", onmessage, false);
	window.addEventListener("unload" , onunload , false);

}

window.addEventListener("load",initialize,false);
