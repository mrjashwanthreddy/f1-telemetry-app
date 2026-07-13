package com.f1telemetry.config;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Dark-themed splash screen shown immediately at startup while Spring Boot initializes.
 * Styled to match the F1 Telemetry dashboard aesthetic — dark background, red accents.
 */
public class SplashScreen extends JFrame {

    private final JLabel statusLabel;
    private float animationAlpha = 0f;
    private Timer fadeInTimer;

    public SplashScreen() {
        setTitle("F1 Telemetry");
        setUndecorated(true);
        setSize(480, 320);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));

        // Main panel with dark gradient background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Dark gradient background
                GradientPaint gradient = new GradientPaint(
                        0, 0, new Color(10, 15, 30),
                        0, getHeight(), new Color(15, 23, 42)
                );
                g2.setPaint(gradient);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));

                // Subtle border glow
                g2.setColor(new Color(229, 9, 20, 60));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 20, 20));

                // Draw F1 logo text
                g2.setColor(new Color(229, 9, 20));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 72));
                FontMetrics fm = g2.getFontMetrics();
                String logo = "F1";
                int x = (getWidth() - fm.stringWidth(logo)) / 2;
                g2.drawString(logo, x, 140);

                // Subtitle
                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                fm = g2.getFontMetrics();
                String subtitle = "RACE ENGINEER";
                x = (getWidth() - fm.stringWidth(subtitle)) / 2;
                g2.drawString(subtitle, x, 170);

                // Loading bar background
                int barWidth = 200;
                int barHeight = 4;
                int barX = (getWidth() - barWidth) / 2;
                int barY = 220;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                // Animated loading bar segment
                long time = System.currentTimeMillis() % 2000;
                float progress = (float) time / 2000f;
                int segmentWidth = 60;
                int segmentX = barX + (int) ((barWidth - segmentWidth) * progress);
                g2.setColor(new Color(229, 9, 20));
                g2.fillRoundRect(segmentX, barY, segmentWidth, barHeight, 4, 4);

                g2.dispose();
            }
        };
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setOpaque(false);

        // Status label at the bottom
        statusLabel = new JLabel("Starting Race Engineer...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(100, 116, 139));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Rounded window shape
        setShape(new RoundRectangle2D.Float(0, 0, 480, 320, 20, 20));

        // Animate the loading bar
        Timer animTimer = new Timer(16, e -> mainPanel.repaint());
        animTimer.start();
    }

    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void showSplash() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    public void hideSplash() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }
}
