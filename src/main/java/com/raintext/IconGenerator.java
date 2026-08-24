package com.raintext;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class IconGenerator {

    public static void main(String[] args) throws IOException {
        generateIcon(16, "src/main/resources/icons/icon-16.png");
        generateIcon(32, "src/main/resources/icons/icon-32.png");
        generateIcon(48, "src/main/resources/icons/icon-48.png");
        generateIcon(64, "src/main/resources/icons/icon-64.png");
        generateIcon(128, "src/main/resources/icons/icon-128.png");
        generateIcon(256, "src/main/resources/icons/icon-256.png");
        System.out.println("Icons generated successfully!");
    }

    public static void generateIcon(int size, String outputPath) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 背景 - 圆角矩形
        g2d.setColor(new Color(41, 98, 255));
        int padding = size / 16;
        int cornerRadius = size / 5;
        g2d.fillRoundRect(padding, padding, size - padding * 2, size - padding * 2, cornerRadius, cornerRadius);

        // 内部白色区域 - 文档形状
        g2d.setColor(Color.WHITE);
        int docPadding = size / 5;
        int docWidth = size - docPadding * 2;
        int docHeight = size - docPadding * 2;
        int docCornerRadius = size / 10;
        g2d.fillRoundRect(docPadding, docPadding, docWidth, docHeight, docCornerRadius, docCornerRadius);

        // 文字行 - 蓝色横线
        g2d.setColor(new Color(41, 98, 255));
        int linePadding = size / 4;
        int lineHeight = Math.max(1, size / 20);
        int lineSpacing = size / 8;

        for (int i = 0; i < 4; i++) {
            int y = linePadding + i * lineSpacing;
            if (y + lineHeight < size - docPadding) {
                int lineWidth = docWidth - (size / 6);
                if (i == 3) {
                    lineWidth = lineWidth * 2 / 3; // 最后一行短一些
                }
                g2d.fillRoundRect(docPadding + size / 12, y, lineWidth, lineHeight, lineHeight, lineHeight);
            }
        }

        // 右下角 - 放大镜/搜索符号
        g2d.setColor(new Color(255, 152, 0));
        int magnifierSize = size / 4;
        int magnifierX = size - magnifierSize - padding;
        int magnifierY = size - magnifierSize - padding;

        // 放大镜圆圈
        g2d.setStroke(new BasicStroke(Math.max(1, size / 24)));
        g2d.drawOval(magnifierX, magnifierY, magnifierSize, magnifierSize);

        // 放大镜手柄
        int handleX1 = magnifierX + magnifierSize * 3 / 4;
        int handleY1 = magnifierY + magnifierSize * 3 / 4;
        int handleX2 = magnifierX + magnifierSize + magnifierSize / 3;
        int handleY2 = magnifierY + magnifierSize + magnifierSize / 3;
        g2d.drawLine(handleX1, handleY1, handleX2, handleY2);

        g2d.dispose();

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", outputFile);
    }
}
