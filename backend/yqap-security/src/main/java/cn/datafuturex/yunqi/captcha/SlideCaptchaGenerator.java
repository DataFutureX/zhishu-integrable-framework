package cn.datafuturex.yunqi.captcha;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.img.ImgUtil;
import cn.datafuturex.yunqi.config.CaptchaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.Random;

/**
 * 滑动拼图验证码图片生成器
 */
@Component
@RequiredArgsConstructor
public class SlideCaptchaGenerator {

    private final CaptchaProperties properties;
    private final Random random = new SecureRandom();

    /**
     * 生成滑动验证码图片
     *
     * @return 背景图、滑块图及目标位置
     */
    public CaptchaImage generate() {
        int width = properties.getImageWidth();
        int height = properties.getImageHeight();
        int blockWidth = properties.getBlockWidth();
        int blockHeight = properties.getBlockHeight();
        int blockRadius = properties.getBlockRadius();

        BufferedImage background = createBackground(width, height);

        int minX = blockWidth + 20;
        int maxX = width - blockWidth - 20;
        int targetX = minX + random.nextInt(maxX - minX);
        int targetY = 20 + random.nextInt(height - blockHeight - 40);

        Shape blockShape = createBlockShape(blockWidth, blockHeight, blockRadius);
        AffineTransform transform = AffineTransform.getTranslateInstance(targetX, targetY);
        Shape holeShape = transform.createTransformedShape(blockShape);

        BufferedImage slider = createSliderImage(background, blockShape, targetX, targetY, blockWidth, blockHeight, blockRadius);
        drawHole(background, holeShape);

        return new CaptchaImage(
                toBase64(background),
                toBase64(slider),
                targetX,
                targetY,
                blockRadius
        );
    }

    private BufferedImage createBackground(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color start = randomColor(80, 180);
        Color end = randomColor(80, 180);
        for (int y = 0; y < height; y++) {
            float ratio = (float) y / height;
            int r = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
            int gr = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
            int b = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
            g.setColor(new Color(r, gr, b));
            g.drawLine(0, y, width, y);
        }

        g.setColor(new Color(255, 255, 255, 60));
        for (int i = 0; i < 8; i++) {
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setColor(new Color(255, 255, 255, 80));
        for (int i = 0; i < 30; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int size = 1 + random.nextInt(3);
            g.fillOval(x, y, size, size);
        }

        g.dispose();
        return image;
    }

    private BufferedImage createSliderImage(BufferedImage background, Shape blockShape,
                                            int targetX, int targetY, int blockWidth, int blockHeight,
                                            int blockRadius) {
        int paddingTop = blockRadius;
        int paddingRight = blockRadius;
        int sliderWidth = blockWidth + paddingRight;
        int sliderHeight = blockHeight + paddingTop + blockRadius;

        BufferedImage slider = new BufferedImage(sliderWidth, sliderHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = slider.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(0, paddingTop);

        g.setClip(blockShape);
        g.drawImage(background, -targetX, -targetY, null);

        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(2f));
        g.draw(blockShape);

        g.setColor(new Color(0, 0, 0, 80));
        g.setStroke(new BasicStroke(1f));
        g.draw(blockShape);

        g.dispose();
        return slider;
    }

    private void drawHole(BufferedImage background, Shape holeShape) {
        Graphics2D g = background.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g.setColor(new Color(0, 0, 0, 100));
        g.fill(holeShape);
        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(holeShape);
        g.dispose();
    }

    /**
     * 生成拼图块形状（顶部凸起、右侧凹陷）
     */
    private Shape createBlockShape(int blockWidth, int blockHeight, int blockRadius) {
        double offset = blockWidth / 2.0 - blockRadius;
        GeneralPath path = new GeneralPath();

        path.moveTo(0, offset);
        path.lineTo(0, blockHeight - offset);
        path.quadTo(0, blockHeight, offset, blockHeight);
        path.lineTo(blockWidth - offset, blockHeight);
        path.quadTo(blockWidth, blockHeight, blockWidth, blockHeight - offset);

        path.lineTo(blockWidth, blockHeight / 2.0 + blockRadius);
        path.curveTo(
                blockWidth - blockRadius, blockHeight / 2.0 + blockRadius,
                blockWidth - blockRadius, blockHeight / 2.0 - blockRadius,
                blockWidth, blockHeight / 2.0 - blockRadius
        );

        path.lineTo(blockWidth, offset);
        path.quadTo(blockWidth, 0, blockWidth - offset, 0);

        path.lineTo(blockWidth / 2.0 + blockRadius, 0);
        path.curveTo(
                blockWidth / 2.0 + blockRadius, -blockRadius,
                blockWidth / 2.0 - blockRadius, -blockRadius,
                blockWidth / 2.0 - blockRadius, 0
        );

        path.lineTo(offset, 0);
        path.quadTo(0, 0, 0, offset);
        path.closePath();
        return path;
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(
                min + random.nextInt(range),
                min + random.nextInt(range),
                min + random.nextInt(range)
        );
    }

    private String toBase64(BufferedImage image) {
        return Base64.encode(ImgUtil.toBytes(image, "png"));
    }

    /**
     * 验证码图片结果
     */
    public record CaptchaImage(
            String backgroundImage,
            String sliderImage,
            int targetX,
            int targetY,
            int sliderImageOffsetY
    ) {
    }
}
