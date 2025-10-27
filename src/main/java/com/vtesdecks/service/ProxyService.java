package com.vtesdecks.service;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import com.vtesdecks.cache.indexable.proxy.ProxyCardOption;
import com.vtesdecks.model.api.ApiProxyCard;
import com.vtesdecks.model.api.ApiProxyCardOption;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@Service
public class ProxyService {

    private final static float MILLIMETERS = 2.834f;
    private final static float CARD_HEIGHT = 88 * MILLIMETERS;
    private final static float CARD_WIDTH = 63 * MILLIMETERS;
    private final static float MARGIN_LEFT = 10 * MILLIMETERS;
    private final static float MARGIN_BOT = 15 * MILLIMETERS;
    private final static String CARD_IMAGES_URL = "https://cdn.vtesdecks.com/img/cards/sets/%s/%d.jpg";
    private final static String CARD_IMAGE_FAILOVER_URL = "https://vtesdecks.com/assets/img/cards/%d.jpg";


    private final static float[][] CARD_POSITIONS = new float[][]{
            new float[]{MARGIN_LEFT, MARGIN_BOT + CARD_HEIGHT * 2},
            new float[]{MARGIN_LEFT + CARD_WIDTH, MARGIN_BOT + CARD_HEIGHT * 2},
            new float[]{MARGIN_LEFT + CARD_WIDTH * 2, MARGIN_BOT + CARD_HEIGHT * 2},

            new float[]{MARGIN_LEFT, MARGIN_BOT + CARD_HEIGHT},
            new float[]{MARGIN_LEFT + CARD_WIDTH, MARGIN_BOT + CARD_HEIGHT},
            new float[]{MARGIN_LEFT + CARD_WIDTH * 2, MARGIN_BOT + CARD_HEIGHT},

            new float[]{MARGIN_LEFT, MARGIN_BOT},
            new float[]{MARGIN_LEFT + CARD_WIDTH, MARGIN_BOT},
            new float[]{MARGIN_LEFT + CARD_WIDTH * 2, MARGIN_BOT},
    };

    @Autowired
    private RestTemplate restTemplate;

    public boolean checkProxyCardOptionExists(ProxyCardOption proxyCardOption) {
        String imgUrl = getProxyImageUrl(proxyCardOption.getSetAbbrev(), proxyCardOption.getCardId());
        return existImage(imgUrl);
    }


    public String getProxyImageUrl(String setAbbrev, Integer cardId) {
        return String.format(CARD_IMAGES_URL, StringUtils.lowerCase(setAbbrev), cardId);
    }

    private boolean existImage(String imgUrl) {
        try {
            restTemplate.headForHeaders(imgUrl);
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
        return true;
    }

    public byte[] generatePDF(List<ApiProxyCard> cards, Map<Integer, List<ApiProxyCardOption>> cardOptions) throws DocumentException, IOException {
        final Document document = new Document();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter pdfWriter = PdfWriter.getInstance(document, outputStream);
        document.open();

        int n = 0;
        for (ApiProxyCard card : cards) {
            Image img = getPDFImage(card.getSetAbbrev(), card.getCardId(), cardOptions.get(card.getCardId()));
            img.scaleAbsolute(CARD_WIDTH, CARD_HEIGHT);
            for (int i = 0; i < card.getAmount(); i++) {
                if (n == 9) {
                    n = 0;
                    drawLines(pdfWriter);
                    document.newPage();
                }
                img.setAbsolutePosition(CARD_POSITIONS[n][0], CARD_POSITIONS[n][1]);
                document.add(img);
                n++;
            }
        }
        drawLines(pdfWriter);
        document.close();
        return outputStream.toByteArray();
    }

    private Image getPDFImage(String setAbbrev, Integer cardId, List<ApiProxyCardOption> cardOptions) throws BadElementException, IOException {
        if (setAbbrev != null) {
            String imgUrl = getProxyImageUrl(setAbbrev.toLowerCase(), cardId);
            if (existImage(imgUrl)) {
                return Image.getInstance(imgUrl);
            }
        }
        if (!isEmpty(cardOptions)) {
            String imgUrl = getProxyImageUrl(cardOptions.get(0).getSetAbbrev().toLowerCase(), cardId);
            if (existImage(imgUrl)) {
                return Image.getInstance(imgUrl);
            }
        }
        return Image.getInstance(String.format(CARD_IMAGE_FAILOVER_URL, cardId));
    }

    private void drawLines(PdfWriter writer) {
        PdfContentByte canvas = writer.getDirectContent();
        BaseColor color = BaseColor.DARK_GRAY;
        canvas.setColorStroke(color);

        canvas.moveTo(MARGIN_LEFT + CARD_WIDTH, MARGIN_BOT);
        canvas.lineTo(MARGIN_LEFT + CARD_WIDTH, MARGIN_BOT + CARD_HEIGHT * 3);

        canvas.moveTo(MARGIN_LEFT + CARD_WIDTH * 2, MARGIN_BOT);
        canvas.lineTo(MARGIN_LEFT + CARD_WIDTH * 2, MARGIN_BOT + CARD_HEIGHT * 3);

        canvas.moveTo(MARGIN_LEFT, MARGIN_BOT + CARD_HEIGHT);
        canvas.lineTo(MARGIN_LEFT + CARD_WIDTH * 3, MARGIN_BOT + CARD_HEIGHT);

        canvas.moveTo(MARGIN_LEFT, MARGIN_BOT + CARD_HEIGHT * 2);
        canvas.lineTo(MARGIN_LEFT + CARD_WIDTH * 3, MARGIN_BOT + CARD_HEIGHT * 2);

        canvas.closePathStroke();
    }
}
