package com.kanasansoft.android.WSCamera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

import com.kanasansoft.android.AssetHandler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

public class WSCamera extends Activity {

	private static final String TAG = "WSCamera";

	private Preview preview;

	Server server = null;
	UPnPServer upnpServer = null;

	byte[] captureData = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// http://code.google.com/p/android/issues/detail?id=9431
		System.setProperty("java.net.preferIPv6Addresses", "false");

		initializeServer();
		initializeUPnPServer();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		preview = new Preview(this);

		setContentView(preview);

		warnStreaming();

	}

	private void initializeServer() {

		server = new Server(40320);

		ArrayList<Handler> handlers = new ArrayList<Handler>();

		{
			AssetHandler ah = new AssetHandler(getResources().getAssets(), "html");
			ContextHandler sch = new ContextHandler();
			sch.setHandler(ah);
			sch.setContextPath("/wscamera/html");
			handlers.add(sch);
		}
		{
			WSCameraServlet servlet = new WSCameraServlet();
			ServletHolder sh = new ServletHolder(servlet);
			ServletContextHandler sch = new ServletContextHandler();
			sch.addServlet(sh, "/wscamera/ws");
			handlers.add(sch);
		}

		HandlerList hl = new HandlerList();
		hl.setHandlers(handlers.toArray(new Handler[]{}));
		server.setHandler(hl);

	}

	private void initializeUPnPServer() {

		SharedPreferences pref = getApplicationContext().getSharedPreferences("WSCamera", MODE_PRIVATE);
		String deviceUUID = pref.getString("WSCamera", null);
		if (deviceUUID != null) {
			deviceUUID = UUID.randomUUID().toString();
			Editor edit = pref.edit();
			edit.putString("WSCamera", deviceUUID);
			edit.commit();
		}

		String host;
		try {
			host = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}

		String serverName = "Android/" + Build.VERSION.RELEASE + " UPnP/1.0 WSCamera/0.0.3";

		upnpServer = new UPnPServer();

		ArrayList<HashMap<String, String>> ssdpInfos = new ArrayList<HashMap<String, String>>();

		{
			HashMap<String, String> ssdp = new HashMap<String, String>();
			ssdp.put("SERVER", serverName);
			ssdp.put("SERVICE_TYPE", "urn:kanasansoft-com:service:WSCameraPage:1");
			ssdp.put("SERVICE_NAME", "uuid:" + deviceUUID + "::urn:kanasansoft-com:service:WSCameraPage:1");
			ssdp.put("LOCATION", "http://" + host + ":40320/webcamera/html/index.html");
			ssdpInfos.add(ssdp);
		}
		{
			HashMap<String, String> ssdp = new HashMap<String, String>();
			ssdp.put("SERVER", serverName);
			ssdp.put("SERVICE_TYPE", "urn:kanasansoft-com:service:WSCameraPushImage:1");
			ssdp.put("SERVICE_NAME", "uuid:" + deviceUUID + "::urn:kanasansoft-com:service:WSCameraPushImage:1");
			ssdp.put("LOCATION", "ws://" + host + ":40320/webcamera/ws");
			ssdpInfos.add(ssdp);
		}

		upnpServer.setSSDPInfos(ssdpInfos);

	}

	private void warnStreaming() {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setTitle("WSCamera is in streaming now.");
		alertDialogBuilder.setMessage("http(s)://[IP address of this device]:40320/wscamera/html/index.html");
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			server.start();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		try {
			server.stop();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	class ImageHandler implements Camera.PreviewCallback {

		public void onPreviewFrame(byte[] data, Camera camera) {
			Parameters params = camera.getParameters();
			int width = params.getPictureSize().width;
			int height = params.getPictureSize().height;
			YuvImage yuv = new YuvImage(
				data,
				params.getPreviewFormat(),
				width,
				height,
				null
			);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			yuv.compressToJpeg(new Rect(0, 0, width, height), 30, baos);
			captureData = baos.toByteArray();
		}

	}

	class Preview extends SurfaceView implements Callback {

		private Camera camera = null;

		public Preview(Context context) {

			super(context);

			SurfaceHolder holder = getHolder();
			holder.addCallback(this);
			holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		}

		public void surfaceCreated(SurfaceHolder holder) {

			camera = Camera.open();

			Parameters params = camera.getParameters();
			System.out.println(params.getPreviewFormat());
			params.setPreviewFormat(ImageFormat.NV21);
			camera.setParameters(params);

			try {
				camera.setPreviewDisplay(holder);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			camera.setPreviewCallback(new ImageHandler());
			camera.startPreview();

		}

		public void surfaceDestroyed(SurfaceHolder holder) {

			camera.stopPreview();
			camera.setPreviewCallback(null);
			try {
				camera.setPreviewDisplay(null);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}

			camera.release();

		}

	}

	class WSCameraServlet extends WebSocketServlet {
		private static final long serialVersionUID = 1L;
		public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
			return new WSCameraWebSocket();
		}
	}

	class WSCameraWebSocket implements WebSocket.OnFrame {
		private Connection connection = null;
		public void onOpen(Connection connection) {
			this.connection = connection;
		}

		public void onClose(int closeCode, String message) {
			this.connection = null;
		}

		public boolean onFrame(byte flags, byte opcode, byte[] data, int offset, int length) {
			byte[] sendData = captureData;
			if (sendData == null) {
				return false;
			}
			try {
				connection.sendMessage(sendData, 0, sendData.length);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			return false;
		}

		public void onHandshake(FrameConnection connection) {
		}
	}

	static class UPnPServer {

		static String SSDP_MESSAGE_ALIVE =
				"NOTIFY * HTTP/1.1\r\n" +
				"HOST: 239.255.255.250:1900\r\n" +
				"CACHE-CONTROL: max-age = 1800\r\n" +
				"NT: %s\r\n" +
				"NTS: ssdp:alive\r\n" +
				"SERVER: %s\r\n" +
				"USN: %s\r\n" +
				"\r\n";

		static String SSDP_MESSAGE_BYEBYE =
				"NOTIFY * HTTP/1.1\r\n" +
				"HOST: 239.255.255.250:1900\r\n" +
				"NT: %s\r\n" +
				"NTS: ssdp:alive\r\n" +
				"USN: %s\r\n" +
				"\r\n";

		static String ResponseSSDPMessage =
				"HTTP/1.1 200 OK\r\n" +
				"CACHE-CONTROL: max-age = 1800\r\n" +
				"EXT: \r\n" +
				"LOCATION: %s\r\n" +
				"SERVER: %s\r\n" +
				"ST: %s\r\n" +
				"USN: %s\r\n" +
				"\r\n";

		ArrayList<HashMap<String, String>> ssdpInfos = new ArrayList<HashMap<String, String>>();

		UPnPServer() {
		}

		public void setSSDPInfos(ArrayList<HashMap<String, String>> ssdpInfos) {
			this.ssdpInfos = ssdpInfos;
		}

	}

}
