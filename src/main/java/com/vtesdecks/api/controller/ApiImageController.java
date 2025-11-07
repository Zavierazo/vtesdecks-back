package com.vtesdecks.api.controller;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

@Controller
@RequestMapping("/api/1.0/images")
@Slf4j
public class ApiImageController {
    private static final int PIX_SIZE = 10;

    public enum Section {
        NAME(new Rectangle(0, 0, 340, 55)),
        CLAN(new Rectangle(0, 55, 65, 100)),
        DISCIPLINE(new Rectangle(0, 155, 65, 330)),
        ART(new Rectangle(65, 55, 290, 335)),
        SKILL(new Rectangle(65, 390, 290, 45), new Rectangle(65, 435, 240, 60)),
        CAPACITY(new Rectangle(300, 440, 50, 60));

        @Getter
        private final Rectangle[] rectangle;

        Section(Rectangle... rectangle) {
            this.rectangle = rectangle;
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{id}", produces = {
            MediaType.IMAGE_JPEG_VALUE
    })
    @ResponseBody
    public byte[] pixelate(@PathVariable Integer id, @RequestParam(name = "sections", required = false) List<Section> sections) throws Exception {
        URL imageUrl = new URL("https://raw.githubusercontent.com/Zavierazo/vtesdecks-statics/main/public/img/cards/" + id + ".jpg");
        BufferedImage image = ImageIO.read(imageUrl);
        BufferedImage pixelateImage = pixelateImage(image, sections);
        return convertImageToByteArray(pixelateImage);
    }

    private BufferedImage pixelateImage(BufferedImage bufferedImage, List<Section> sections) {
        Raster src = bufferedImage.getData();
        WritableRaster dest = src.createCompatibleWritableRaster();

        bufferedImage.copyData(dest);

        if (!CollectionUtils.isEmpty(sections)) {
            sections.forEach(section -> pixelateSection(src, dest, section.getRectangle()));
        }

        bufferedImage.setData(dest);

        return bufferedImage;
    }

    private static void pixelateSection(Raster src, WritableRaster dest, Rectangle[] sections) {
        for (Rectangle section : sections) {
            for (int y = section.y; y < section.y + section.getHeight(); y += PIX_SIZE) {
                for (int x = section.x; x < section.x + section.getWidth(); x += PIX_SIZE) {

                    double[] pixel = new double[3];
                    pixel = src.getPixel(x, y, pixel);

                    for (int yd = y; (yd < y + PIX_SIZE) && (yd < dest.getHeight()); yd++) {
                        for (int xd = x; (xd < x + PIX_SIZE) && (xd < dest.getWidth()); xd++) {
                            dest.setPixel(xd, yd, pixel);
                        }
                    }
                }
            }
        }
    }

    private static byte[] convertImageToByteArray(BufferedImage pixelateImage) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(pixelateImage, "jpg", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }
}
