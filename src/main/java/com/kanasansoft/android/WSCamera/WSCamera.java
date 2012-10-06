package com.kanasansoft.android.WSCamera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

public class WSCamera extends Activity {

	private Preview preview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);

		preview = new Preview(this);

		setContentView(preview);

	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
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
			yuv.compressToJpeg(new Rect(0, 0, width, height), 0, baos);
			byte[] jpeg = baos.toByteArray();
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
			try {
				camera = Camera.open();
				camera.setPreviewCallback(new ImageHandler());
				camera.setPreviewDisplay(holder);
				camera.startPreview();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			camera.stopPreview();
			camera.release();
		}

	}
}
