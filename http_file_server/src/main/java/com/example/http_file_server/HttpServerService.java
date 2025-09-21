package com.example.http_file_server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

public class HttpServerService extends Service {

    private MyHttpServer server;

    @Override
    public void onCreate() {
        super.onCreate();

        // Create test file each time the service starts
        try {
            File dir = getFilesDir();
            String name = System.currentTimeMillis() + ".txt";
            FileWriter writer = new FileWriter(new File(dir, name));
            writer.write("Hello from " + name);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        server = new MyHttpServer(getFilesDir());
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }

    public static class MyHttpServer extends NanoHTTPD {
        private final File baseDir;

        public MyHttpServer(File baseDir) {
            super(8080);
            this.baseDir = baseDir;
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();

            if (uri.startsWith("/file/")) {
                String fileName = uri.substring("/file/".length());
                File target = new File(baseDir, fileName);
                if (target.exists() && target.isFile()) {
                    try {
                        return newChunkedResponse(
                                Response.Status.OK,
                                "application/octet-stream",
                                new FileInputStream(target)
                        );
                    } catch (IOException e) {
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error");
                    }
                } else {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
                }
            }

            StringBuilder html = new StringBuilder("<html><body><h1>Files</h1><ul>");
            File[] files = baseDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        html.append("<li><a href=\"/file/")
                                .append(f.getName())
                                .append("\">")
                                .append(f.getName())
                                .append("</a></li>");
                    }
                }
            }
            html.append("</ul></body></html>");
            return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
        }
    }
}
