package com.vtesdecks.util;

import com.anyascii.AnyAscii;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import static com.opencsv.ICSVWriter.DEFAULT_QUOTE_CHARACTER;
import static com.opencsv.ICSVWriter.DEFAULT_SEPARATOR;
import static com.vtesdecks.util.Constants.CONTENT_DISPOSITION_HEADER;
import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Slf4j
@UtilityClass
public class Utils {
    public static String readFile(ClassLoader classLoader, String filePath) {
        InputStream inputStream = classLoader.getResourceAsStream(filePath);
        StringBuilder defaultData = new StringBuilder();
        try (Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextLine()) {
                defaultData.append(scanner.nextLine());
            }
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

    public static String getCurrencyCode(HttpServletRequest httpServletRequest) {
        String countryCode = null;
        try {
            countryCode = httpServletRequest.getHeader(Constants.USER_COUNTRY_HEADER);
            if (countryCode == null || countryCode.isEmpty()) {
                return DEFAULT_CURRENCY;
            }
            String currency = Currency.getInstance(Locale.of("", countryCode)).getCurrencyCode();
            if (currency != null && !currency.isEmpty()) {
                return currency;
            }
        } catch (Exception e) {
            log.warn("Unable to obtain currency from request for country code {}", countryCode, e);
        }
        return DEFAULT_CURRENCY;
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

    public static <T> void returnCsv(HttpServletResponse response, String fileName, List<String> headerOrder, List<T> respList, Class<T> reqClass) throws IOException, CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(CONTENT_DISPOSITION_HEADER, "attachment; filename=\"" + fileName + "\"");
        HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(reqClass);
        strategy.setColumnOrderOnWrite(new OrderedComparatorIgnoringCase(headerOrder));
        StatefulBeanToCsv<T> writer = new StatefulBeanToCsvBuilder<T>(response.getWriter())
                .withQuotechar(DEFAULT_QUOTE_CHARACTER)
                .withSeparator(DEFAULT_SEPARATOR)
                .withOrderedResults(true)
                .withMappingStrategy(strategy)
                .build();
        writer.write(respList);
    }

    public static String removeSpecial(String str) {
        return str.replaceAll("[^a-zA-Z ]", "");
    }

    public static String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell != null ? cell.toString().trim() : null;
    }

    public static Integer getCellInteger(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        try {
            return Integer.parseInt(cell.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return AnyAscii.transliterate(StringUtils.trim(name)).replaceAll("[/\\\\]", "");
    }

    private static final int MAX_URL_LENGTH = 250;
    private static final int MAX_IMAGE_SIZE_BYTES = 512 * 1024; // 512 KB
    private static final int MAX_IMAGE_DIMENSION = 1024;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * Verify if the image URL is valid.
     * Scheme: only HTTPS
     * Max length: 250 characters
     * Extension: jpg, jpeg, png, gif, webp
     * Max size: 512 KB
     * Max dimensions: 512x512 pixels
     *
     * @param imageUrl the URL of the image to validate
     * @return string indicating the validation error, or null if valid
     */
    public static String isValidImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        // Check max length
        if (imageUrl.length() > MAX_URL_LENGTH) {
            return "exceeds maximum length of " + MAX_URL_LENGTH + " characters";
        }

        // Check HTTPS scheme
        if (!imageUrl.toLowerCase().startsWith("https://")) {
            return "must use HTTPS scheme";
        }

        // Check extension
        String lowerUrl = imageUrl.toLowerCase();
        // Remove query parameters for extension check
        String urlPath = lowerUrl.contains("?") ? lowerUrl.substring(0, lowerUrl.indexOf("?")) : lowerUrl;
        boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> urlPath.endsWith("." + ext));
        if (!hasValidExtension) {
            return "must have a valid extension: " + String.join(", ", ALLOWED_EXTENSIONS);
        }

        // Check image size and dimensions by fetching the image
        try {
            java.net.URI uri = java.net.URI.create(imageUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "VTESDecks Image Validator");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return "unable to access image URL, HTTP status: " + responseCode;
            }

            // Check content length if available
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_SIZE_BYTES) {
                return "size exceeds maximum of 512 KB";
            }

            // Read the image and validate size and dimensions
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] imageBytes = inputStream.readNBytes(MAX_IMAGE_SIZE_BYTES + 1);
                if (imageBytes.length > MAX_IMAGE_SIZE_BYTES) {
                    return "size exceeds maximum of 512 KB";
                }

                // Check dimensions
                try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
                    javax.imageio.ImageIO.setUseCache(false);
                    java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(bais);
                    if (image == null) {
                        return "unable to read image from URL";
                    }
                    if (image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
                        return "dimensions exceed maximum of " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION + " pixels";
                    }
                }
            }

            connection.disconnect();
        } catch (IllegalArgumentException | java.net.MalformedURLException e) {
            return "image URL format";
        } catch (java.io.IOException e) {
            log.warn("Error validating image URL: {}", imageUrl, e);
            return "unable to validate image URL";
        }
        return null;
    }
}
