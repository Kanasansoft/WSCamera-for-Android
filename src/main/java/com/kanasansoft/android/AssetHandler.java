package com.kanasansoft.android;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import android.content.res.AssetManager;

public class AssetHandler extends AbstractHandler {

	private AssetManager assetManager = null;
	private String htmlDirectory = null;

	public AssetHandler(AssetManager assetManager, String htmlDirectory) {
		super();
		this.assetManager = assetManager;
		this.htmlDirectory = htmlDirectory;
	}

	public void handle(
			String target,
			Request baseRequest,
			HttpServletRequest request,
			HttpServletResponse response
			) throws IOException, ServletException {

		String path = new File(htmlDirectory, target).getPath();
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;

		try {

			bis = new BufferedInputStream(assetManager.open(path));
			String contentType = null;
			try {
				contentType = URLConnection.guessContentTypeFromStream(bis);
			} catch (NullPointerException e) {
			}
			if (contentType == null) {
				try {
					contentType = URLConnection.guessContentTypeFromName(target);
				} catch (NullPointerException e) {
				}
			}

			response.setContentType(contentType);
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);

			bos = new BufferedOutputStream(response.getOutputStream());
			int data;
			while ((data = bis.read()) != -1) {
				bos.write(data);
			}
			bos.flush();

		} catch (FileNotFoundException e) {

			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			baseRequest.setHandled(true);

		} finally {

			if (bis != null) {
				try {
					bis.close();
				} catch (IOException e) {
					throw e;
				} finally {
					if (bos != null) {
						try {
							bos.close();
						} catch (IOException e) {
							throw e;
						} finally {
						}
					}
				}
			}

		}

	}

}
