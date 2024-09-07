package com.vtesdecks.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

@Slf4j
public class Utils {
    public static String readFile(ClassLoader classLoader, String filePath) {
        File file = new File(classLoader.getResource(filePath).getFile());
        StringBuilder defaultData = new StringBuilder();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                defaultData.append(scanner.nextLine());
            }
        } catch (IOException e) {
            log.error("Error reading file", e);
        }
        return defaultData.toString();
    }

    public static String getIp(HttpServletRequest httpServletRequest) {
        String remoteAddr = "";
        if (httpServletRequest != null) {
            remoteAddr = httpServletRequest.getHeader("X-FORWARDED-FOR");
            if (StringUtils.isBlank(remoteAddr)) {
                remoteAddr = httpServletRequest.getRemoteAddr();
            }
        }
        return remoteAddr;
    }

    public static String removeCommas(String value, boolean advanced) {
        String result = value;
        if (value.endsWith(", The")) {
            result = "The " + value.substring(0, value.indexOf(", The"));
        }
        return result.replaceAll(",", "") + (advanced ? " (Advanced)" : "");
    }

    public static String getMD5(ClassLoader classLoader, String filePath) {
        try {
            return DigestUtils.md5Hex(classLoader.getResourceAsStream(filePath));
        } catch (IOException e) {
            log.error("Unable to obtain md5 for file {}", filePath, e);
            return null;
        }
    }

    public static void returnFile(HttpServletResponse response, String fileName, MediaType contentType, String content) {
        try {
            // Get your file stream from wherever.
            InputStream inputStream = new ByteArrayInputStream(content.getBytes());

            // Set the content type and attachment header.
            response.addHeader("Content-disposition", "attachment;filename=" + fileName);
            response.setContentType(contentType.getType());

            // Copy the stream to the response's output stream.
            IOUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("Unable to return file {} {} {}", fileName, contentType, content, e);
        }
    }

    public static String removeSpecial(String str) {
        return str.replaceAll("[^a-zA-Z ]", "");
    }
}
