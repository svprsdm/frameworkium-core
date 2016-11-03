package com.frameworkium.core.ui.capture;

import io.restassured.RestAssured;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.remote.SessionId;
import org.testng.ITestResult;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.frameworkium.core.common.properties.Property.CAPTURE_GRID_VIDEO;
import static com.frameworkium.core.common.properties.Property.GRID_URL;
import static com.frameworkium.core.ui.tests.BaseTest.getDriver;

public class VideoCapture {

    private static final Integer gridExtrasPort = 3000;
    private static final String gridExtrasUrlSuffix = "download_video";
    private static final String videoFormat = "mp4";
    private static final String videoFolder = "capturedVideo";
    private static final SimpleDateFormat videoNameDateFormat = new SimpleDateFormat("dd-mm-yyyy-HH-mm-ss");

    private static final Logger logger = LogManager.getLogger();

    public static boolean isRequired() {
        return CAPTURE_GRID_VIDEO.isSpecified()
                && GRID_URL.isSpecified()
                && Boolean.parseBoolean(CAPTURE_GRID_VIDEO.getValue());
    }

    public void saveVideo(ITestResult iTestResult, SessionId sessionId) {
        if (getDriver() == null) return;
        String currentDateTime = videoNameDateFormat.format(new Date());
        String testName = iTestResult.getName();

        try {
            waitUntilVideoIsAvailable(sessionId);
        } catch (InterruptedException | TimeoutException e) {
            logger.error(String.format("Timed out waiting for Session ID %s to become available after 6 seconds.", sessionId));
        }

        byte[] rawVideo = RestAssured.get(String.format(
                "%s/%s/%s.%s",
                getGridExtrasUrl(),
                gridExtrasUrlSuffix,
                sessionId,
                videoFormat
        )).asByteArray();

        Path path = Paths.get(videoFolder);
        try {
            if (!Files.exists(path)) Files.createDirectory(path);
            String fileName = String.format(
                    "%s/%s-%s.%s",
                    videoFolder,
                    testName,
                    currentDateTime,
                    videoFormat
            );
            Files.write(Paths.get(fileName), rawVideo);
            logger.info(String.format("Captured video from grid: %s", fileName));
        }
        catch (IOException e) {
            logger.error("Failed creating directory/file for video capture", e);
        }
    }

    private URL getGridExtrasUrl() {
        try {
            URL gridUrl = new URL(GRID_URL.getValue());
            return new URL(
                    String.format(
                            "http://%s:%d",
                            gridUrl.getHost(),
                            gridExtrasPort
                    )
            );
        }
        catch (MalformedURLException e) {
            logger.error("Unable to parse Grid URL", e);
            return null;
        }
    }

    private void waitUntilVideoIsAvailable(SessionId sessionId) throws TimeoutException, InterruptedException {
        String gridBody;
        int i = 0;
        while (i++ < 4) {
            gridBody = RestAssured.get(getGridExtrasUrl()).asString();
            if (gridBody.contains(sessionId.toString())) return;
            TimeUnit.SECONDS.sleep(2);
        }
        throw new TimeoutException();
    }
}
