package com.f1telemetry.config;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class to generate a professional 256x256 F1 logo .ico file dynamically
 * for the Windows standalone .exe packaging.
 */
public class IconGenerator {

    public static void main(String[] args) {
        try {
            generateIcoFile("logo.ico");
            System.out.println("SUCCESS: logo.ico generated successfully in the project root!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateIcoFile(String outputPath) throws IOException {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // F1 Red background with rounded corners
        g2.setColor(new Color(229, 9, 20));
        g2.fillRoundRect(0, 0, size, size, size / 4, size / 4);

        // Inner glowing border
        g2.setColor(new Color(255, 255, 255, 30));
        g2.setStroke(new BasicStroke(8f));
        g2.drawRoundRect(4, 4, size - 8, size - 8, size / 4, size / 4);

        // Bold white F1 text
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.55)));
        FontMetrics fm = g2.getFontMetrics();
        String text = "F1";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = (size + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, x, y);
        g2.dispose();

        // Convert BufferedImage to PNG bytes
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(img, "png", pngOut);
        byte[] pngBytes = pngOut.toByteArray();

        // Write ICO binary header + PNG payload
        try (FileOutputStream fos = new FileOutputStream(new File(outputPath))) {
            // ICO Header (6 bytes)
            ByteBuffer header = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
            header.putShort((short) 0); // Reserved
            header.putShort((short) 1); // Type: Icon
            header.putShort((short) 1); // Count of images: 1
            fos.write(header.array());

            // ICO Directory Entry (16 bytes)
            ByteBuffer directory = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            directory.put((byte) 0);    // Width (0 means 256)
            directory.put((byte) 0);    // Height (0 means 256)
            directory.put((byte) 0);    // Palette count: 0
            directory.put((byte) 0);    // Reserved: 0
            directory.putShort((short) 1); // Color planes: 1
            directory.putShort((short) 32); // Bits per pixel: 32 (ARGB)
            directory.putInt(pngBytes.length); // Size of the PNG image data
            directory.putInt(22); // Offset where image data begins (6 + 16 = 22)
            fos.write(directory.array());

            // Image Data (PNG bytes)
            fos.write(pngBytes);
        }
    }
}
