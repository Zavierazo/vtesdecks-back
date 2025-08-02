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
import java.util.List;
import java.util.Scanner;

import static com.opencsv.ICSVWriter.DEFAULT_QUOTE_CHARACTER;
import static com.opencsv.ICSVWriter.DEFAULT_SEPARATOR;
import static com.vtesdecks.util.Constants.CONTENT_DISPOSITION_HEADER;

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

    public static String normalizeLackeyName(String name) {
        return AnyAscii.transliterate(StringUtils.trim(name)).replaceAll("[/\\\\]", "");
    }
}
