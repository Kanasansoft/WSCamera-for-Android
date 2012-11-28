package com.kanasansoft.android.WSCamera;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import com.kanasansoft.android.WSCamera.WSCamera.UPnPServer.SSDPInformation;

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
		System.setProperty("java.net.preferIPv4Stack", "true");

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
		if (deviceUUID == null) {
			deviceUUID = UUID.randomUUID().toString();
			Editor edit = pref.edit();
			edit.putString("WSCamera", deviceUUID);
			edit.commit();
		}

		String serverName = "Android/" + Build.VERSION.RELEASE + " UPnP/1.0 WSCamera/0.0.3";

		upnpServer = new UPnPServer();

		ArrayList<SSDPInformation> ssdpInfos = new ArrayList<SSDPInformation>();

		{
			SSDPInformation ssdp = new SSDPInformation();
			ssdp.put("SERVER", serverName);
			ssdp.put("DEVICE", "uuid:" + deviceUUID);
			ssdp.put("SERVICE_TYPE", "urn:schemas-webintents-org:service:WebIntents:1");
			ssdp.put("SERVICE_NAME", "uuid:" + deviceUUID + "::urn:schemas-webintents-org:service:WebIntents:1");
			ssdp.put("LOCATION", "http://%s:40320/");
			ssdp.addOption("action.webintents.org: http://webintents.org/pick");
			ssdp.addOption("location.webintents.org: /wscamera/html/index.html");
			ssdpInfos.add(ssdp);
		}
		{
			SSDPInformation ssdp = new SSDPInformation();
			ssdp.put("SERVER", serverName);
			ssdp.put("DEVICE", "uuid:" + deviceUUID);
			ssdp.put("SERVICE_TYPE", "urn:kanasansoft-com:service:WSCameraPage:1");
			ssdp.put("SERVICE_NAME", "uuid:" + deviceUUID + "::urn:kanasansoft-com:service:WSCameraPage:1");
			ssdp.put("LOCATION", "http://%s:40320/wscamera/html/index.html");
			ssdpInfos.add(ssdp);
		}
		{
			SSDPInformation ssdp = new SSDPInformation();
			ssdp.put("SERVER", serverName);
			ssdp.put("DEVICE", "uuid:" + deviceUUID);
			ssdp.put("SERVICE_TYPE", "urn:kanasansoft-com:service:WSCameraPushImage:1");
			ssdp.put("SERVICE_NAME", "uuid:" + deviceUUID + "::urn:kanasansoft-com:service:WSCameraPushImage:1");
			ssdp.put("LOCATION", "ws://%s:40320/wscamera/ws");
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
			upnpServer.start();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		try {
			server.stop();
			upnpServer.stop();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	class ImageHandler implements Camera.PreviewCallback {

		public void onPreviewFrame(byte[] data, Camera camera) {
			Parameters params = camera.getParameters();
			int width = params.getPreviewSize().width;
			int height = params.getPreviewSize().height;
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
			if (camera == null) {
				try {
					Method getnum = Camera.class.getMethod("getNumberOfCameras", new Class[]{});
					int num = (int)(Integer)getnum.invoke(null, new Object[]{});
					for (int i = 0; i < num; i++) {
						Method open = Camera.class.getMethod("open", new Class[]{int.class});
						camera = (Camera)open.invoke(null, new Object[]{Integer.valueOf(i)});
						if (camera != null) {
							break;
						}
					}
				} catch (NoSuchMethodException e) {
				} catch (IllegalArgumentException e) {
				} catch (IllegalAccessException e) {
				} catch (InvocationTargetException e) {
				}
			}

			if (camera == null) {
				return;
			}

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

			if (camera == null) {
				return;
			}

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
				"%s" +
				"Content-Length: 0\r\n" +
				"\r\n";

		static String SSDP_MESSAGE_BYEBYE =
				"NOTIFY * HTTP/1.1\r\n" +
				"HOST: 239.255.255.250:1900\r\n" +
				"NT: %s\r\n" +
				"NTS: ssdp:alive\r\n" +
				"USN: %s\r\n" +
				"%s" +
				"Content-Length: 0\r\n" +
				"\r\n";

		static String ResponseSSDPMessage =
				"HTTP/1.1 200 OK\r\n" +
				"CACHE-CONTROL: max-age = 1800\r\n" +
				"EXT: \r\n" +
				"LOCATION: %s\r\n" +
				"SERVER: %s\r\n" +
				"ST: %s\r\n" +
				"USN: %s\r\n" +
				"%s" +
				"Content-Length: 0\r\n" +
				"\r\n";

		ArrayList<SSDPInformation> ssdpInfos = new ArrayList<SSDPInformation>();

		ExecutorService executor = null;
		UDPServer udpserver = null;

		UPnPServer() {
		}

		public void setSSDPInfos(ArrayList<SSDPInformation> ssdpInfos) {
			this.ssdpInfos = ssdpInfos;
		}

		public void start() throws IOException {
			executor = Executors.newSingleThreadExecutor();
			MSearchHandler handler = new MSearchHandler(ssdpInfos);
			udpserver = new UDPServer(handler, 1900, 8192);
			udpserver.join("239.255.255.250");
			executor.execute(udpserver);
		}

		public void stop() throws UnknownHostException, IOException {
			udpserver.leave("239.255.255.250");
			executor.shutdownNow();
		}

		static class SSDPInformation extends HashMap<String, String> {
			private static final long serialVersionUID = 1L;
			private ArrayList<String> options = new ArrayList<String>();
			void addOption(String option) {
				options.add(option);
			}
		}

	}

	static class UDPServer implements Runnable {

		byte[] data;
		UDPHandler handler;
		MulticastSocket socket;
		DatagramPacket packet;

		public UDPServer(UDPHandler handler, int port) throws IOException {
			this(handler, port, 1024);
		}

		public UDPServer(UDPHandler handler, int port, int bufferSize) throws IOException {
			data = new byte[bufferSize];
			this.handler = handler;
			socket = new MulticastSocket(port);
			packet = new DatagramPacket(data, data.length);
		}

		public void run() {
			while (true) {
				try {
					socket.setSoTimeout(100);
					socket.receive(packet);
					handler.transfer(packet.getData(), packet.getAddress(), packet.getPort());
				} catch (InterruptedIOException e) {
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (Thread.interrupted()) {
					break;
				}
			}
		}

		void join(String address) throws UnknownHostException, IOException {
			socket.joinGroup(InetAddress.getByName(address));
		}

		void leave(String address) throws UnknownHostException, IOException {
			socket.leaveGroup(InetAddress.getByName(address));
		}

		static interface UDPHandler {
			void transfer(byte[] data, InetAddress address, int port);
		}

	}

	static class MSearchHandler implements UDPServer.UDPHandler {

		private ArrayList<SSDPInformation> ssdpInfos;

		public MSearchHandler(ArrayList<SSDPInformation> ssdpInfos) {
			this.ssdpInfos = ssdpInfos;
		}

		public void transfer(byte[] data, InetAddress address, int port) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.execute(new AssignHandler(ssdpInfos, data, address, port));
		}

		static private class AssignHandler implements Runnable {
			ArrayList<SSDPInformation> ssdpInfos;
			byte[] data;
			InetAddress address;
			int port;
			AssignHandler(ArrayList<SSDPInformation> ssdpInfos, byte[] data, InetAddress address, int port) {
				this.ssdpInfos = ssdpInfos;
				this.data = data;
				this.address = address;
				this.port = port;
			}
			public void run() {

				String man = null;
				String mx = null;
				String st = null;

				BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)), 8192);

				try {
					String requestLine = br.readLine();
					if (requestLine == null || !requestLine.equals("M-SEARCH * HTTP/1.1")) {
						return;
					}
					String str = null;
					while((str = br.readLine()) != null){
						if (str.startsWith("MAN: ")) {
							man = str.substring(5);
						} else if (str.startsWith("MX: ")) {
							mx = str.substring(4);
						} else if (str.startsWith("ST: ")){
							st = str.substring(4);
						}
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				if (man == null || mx == null || st == null) {
					return;
				}
				if (!man.equals("\"ssdp:discover\"")) {
					return;
				}
				if (!mx.matches("^[1-9][0-9]*$")) {
					return;
				}
				int mxnum = Integer.parseInt(mx, 10);
				if (!(1 <= mxnum && mxnum <= 120)) {
					return;
				}

				ArrayList<InetAddress> hosts = NetworkUtility.getSelfAddress();
				ArrayList<Inet4Address> v4hosts = new ArrayList<Inet4Address>();
				for (InetAddress address : hosts) {
					if (address instanceof Inet4Address) {
						v4hosts.add((Inet4Address)address);
					}
				}
				String host = v4hosts.isEmpty() ? "127.0.0.1" : v4hosts.get(0).getHostAddress();

				for (SSDPInformation ssdpinfo : ssdpInfos) {
					if (
							st.equals("ssdp:all") ||
							st.equals(ssdpinfo.get("SERVICE_TYPE"))
							) {
						ArrayList<String> options = ssdpinfo.options;
						StringBuilder optionBuilder = new StringBuilder();
						for (String option : options) {
							optionBuilder.append(option + "\r\n");
						}
						String message = String.format(
								UPnPServer.ResponseSSDPMessage,
								String.format(ssdpinfo.get("LOCATION"), host),
								ssdpinfo.get("SERVER"),
								ssdpinfo.get("SERVICE_TYPE"),
								ssdpinfo.get("SERVICE_NAME"),
								optionBuilder.toString()
						);
						ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
						executor.schedule(new UPnPSender(message, address, port), (long)(mxnum * 1000 * Math.random()), TimeUnit.MILLISECONDS);
						;
					}
				}

			}
		}

	}

	static private class UPnPSender implements Runnable {
		private String message;
		private InetAddress address;
		private int port;
		UPnPSender(String message, InetAddress address, int port) {
			this.message = message;
			this.address = address;
			this.port = port;
		}
		public void run() {
			byte[] data = message.getBytes();
			InetSocketAddress isAddress = new InetSocketAddress(address, port);
			try {
				DatagramPacket packet = new DatagramPacket(data, data.length, isAddress);
				new MulticastSocket().send(packet);
			} catch (SocketException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

}

class NetworkUtility {

	static ArrayList<InetAddress> getSelfAddress() {
		ArrayList<InetAddress> hosts = new ArrayList<InetAddress>();
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while(interfaces.hasMoreElements()){
				NetworkInterface network = interfaces.nextElement();
				Enumeration<InetAddress> addresses = network.getInetAddresses();
				while(addresses.hasMoreElements()){
					InetAddress buffer = addresses.nextElement();
					if(!(buffer.isLoopbackAddress() || buffer.isAnyLocalAddress())){
						hosts.add(buffer);
					}
				}
			}
		} catch (SocketException e) {
		}
		return hosts;
	}

}
