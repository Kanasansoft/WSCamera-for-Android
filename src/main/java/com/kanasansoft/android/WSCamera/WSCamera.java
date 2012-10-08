package com.kanasansoft.android.WSCamera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

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
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
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

	byte[] captureData = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		preview = new Preview(this);

		// http://code.google.com/p/android/issues/detail?id=9431
		System.setProperty("java.net.preferIPv6Addresses", "false");

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

		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setTitle("WSCamera is in streaming now.");
		alertDialogBuilder.setMessage("http(s)://[IP address of this device]:40320/wscamera/html/index.html");
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();

		setContentView(preview);

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

}
