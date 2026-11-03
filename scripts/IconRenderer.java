import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the launcher icon as the 512 px store icon. The app itself uses the adaptive vector icon
 * (minSdk 28), so no launcher bitmaps are generated.
 * Shapes and colours mirror res/drawable/ic_launcher_foreground.xml and the ic_launcher_background
 * colour; change both together.
 *
 * Usage, from the project root: java -Djava.awt.headless=true scripts/IconRenderer.java
 */
public class IconRenderer {
    static final Color BACKGROUND = new Color(0x17283C);
    static final Color PAPER = Color.WHITE;
    static final Color TITLE = new Color(0xC5CFDA);
    static final Color MOST_USED = new Color(0xFF8A3D);
    static final Color SECOND = new Color(0x3D8BFF);
    static final Color THIRD = new Color(0x9DB8DA);

    /** Same outline as the vector's paper path, in the 108-unit adaptive-icon canvas. */
    static Shape paper() {
        Path2D p = new Path2D.Double();
        p.moveTo(40, 28);
        p.lineTo(68, 28);
        p.append(new Arc2D.Double(63, 28, 10, 10, 90, -90, Arc2D.OPEN), true);
        p.lineTo(73, 76);
        double[] xs = {69.2, 65.4, 61.6, 57.8, 54, 50.2, 46.4, 42.6, 38.8, 35};
        for (int i = 0; i < xs.length; i++) p.lineTo(xs[i], i % 2 == 0 ? 80 : 76);
        p.lineTo(35, 33);
        p.append(new Arc2D.Double(35, 28, 10, 10, 180, -90, Arc2D.OPEN), true);
        p.closePath();
        return p;
    }

    static void bar(Graphics2D g, Color color, double y, double endX, double width) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(41, y, endX, y));
    }

    /** The visible 72 units of the 108-unit canvas, scaled to [size] px, optionally cut to a rounded square. */
    static BufferedImage render(int size, boolean rounded) {
        BufferedImage img = new BufferedImage(size, size, rounded ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (rounded) {
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.44, size * 0.44));
            g.setComposite(AlphaComposite.SrcIn);
        }
        g.scale(size / 72.0, size / 72.0);
        g.translate(-18, -18);
        g.setColor(BACKGROUND);
        g.fill(new Rectangle2D.Double(0, 0, 108, 108));
        g.setComposite(AlphaComposite.SrcAtop);
        g.setColor(PAPER);
        g.fill(paper());
        bar(g, TITLE, 36, 53, 3.5);
        bar(g, MOST_USED, 46, 67, 5);
        bar(g, SECOND, 55, 60, 5);
        bar(g, THIRD, 64, 52, 5);
        g.dispose();
        return img;
    }

    static void write(BufferedImage img, String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        System.out.println(path);
    }

    public static void main(String[] args) throws Exception {
        // Store listing: 512 x 512, full bleed (Pleos App Market registration info).
        write(render(512, false), "docs/store/icon-512.png");
        // Rounded preview for documents and slides.
        write(render(512, true), "docs/store/icon-512-rounded.png");
    }
}
