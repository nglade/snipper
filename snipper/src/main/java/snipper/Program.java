package snipper;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Queue;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class Program {

	static {
		nu.pattern.OpenCV.loadShared();
		System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
	}

	public static void main(String[] args) {
		System.out.println("hi");
		JFileChooser chooser = new JFileChooser();
		Mat img = null, gsc = null, b;
		String file = null;
		if (chooser.showDialog(null, "select directory") == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile().getAbsolutePath();
		}

		workOnImage(file);
		JFrame frame = new JFrame("Result");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		try {
			final BufferedImage outImg = ImageIO.read(new File("out.jpg"));

			frame.getContentPane().add(new JPanel() {
				Dimension size = new Dimension(1280, 960);

				@Override
				public Dimension getPreferredSize() {
					return size;
				}

				Image img = null;

				Image getImage() {
					if (img == null)
						img = outImg.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);
					return img;
				}

				@Override
				protected void paintComponent(Graphics g) {
					// TODO Auto-generated method stub
					super.paintComponent(g);

					g.drawImage(getImage(), 0, 0, null);
				}
			});
			frame.pack();
			frame.setVisible(true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void workOnImage(String file) {
		Mat img;
		Mat gsc;
		Mat b;
		img = Imgcodecs.imread(file, Imgcodecs.IMREAD_COLOR);
		gsc = new Mat();
		b = new Mat();
		img.copyTo(gsc);

		Imgproc.cvtColor(img, gsc, Imgproc.COLOR_BGR2GRAY);

		Imgproc.blur(gsc, b, new Size(3, 3));
		gsc = b;

		System.out.println(img);
		Mat m = new Mat();
		// LineSegmentDetector d =
		// org.opencv.imgproc.Imgproc.createLineSegmentDetector();
		// d.detect(gsc, m);
		Mat linesImg = new Mat();
		img.copyTo(linesImg);
		linesImg.setTo(Scalar.all(0));
		// d.drawSegments(linesImg, m);
		List<MatOfPoint> contours = new ArrayList<>();
		MatOfInt4 hierarchy = new MatOfInt4();
		Imgproc.Canny(gsc, linesImg, 25, 100);
		Imgproc.findContours(linesImg, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE,
				new Point(0, 0));
		/// Approximate contours to polygons + get bounding rects and
		/// circles
		// List<List<Point>> polyContours = new ArrayList<>( contours.size()
		// );
		// List<Rect> boundRectangle = new ArrayList<>(contours.size());
		// List<Point2f> center = new ArrayList<>(contours.size());
		List<Rect> bounds = new LinkedList<>();

		float[] radius = new float[contours.size()];
		for (int i = 0; i < contours.size(); i++) {
			MatOfPoint2f contour2f = new MatOfPoint2f();
			contour2f.fromArray(contours.get(i).toArray());
			MatOfPoint2f poly = new MatOfPoint2f();
			Imgproc.approxPolyDP(contour2f, poly, 3, true);
			MatOfPoint p = new MatOfPoint();
			Rect bound = Imgproc.boundingRect(new MatOfPoint(poly.toArray()));
			if(bound.area()<50|| (bound.height<50&&bound.width<50))
				continue;
			if (!bounds.isEmpty()) {
				for (ListIterator<Rect> it = bounds.listIterator(); it.hasNext();) {
					Rect existing = it.next();
					Rect hullResult = getBounds(bound, existing);
					if (hullResult == existing)
						continue;
					else {
						if (hullResult != null) {
							it.remove();
							it.add(hullResult);
						} else {
							it.add(bound);
						}
						break;
					}
				}
			} else
				bounds.add(bound);

		}
		boolean changed = false;
		List<Rect> result = new ArrayList<>();

		do {
			changed = false;
			for (int i = 0; i < bounds.size(); i++) {
				Rect rect = bounds.get(i);
				boolean keep = true;
				for (int j = 0; j < bounds.size(); j++) {
					Rect rect2 = bounds.get(j);
					if (rect2 == rect)
						continue;
					else if (rect2.contains(rect.br()) && rect2.contains(rect.tl())) {
						keep = false;
						break;
					}
				}
				if (keep)
					result.add(rect);
				changed |=!keep;
			}
			bounds.retainAll(result);
		} while (changed);
		linesImg.setTo(Scalar.all(0));
		for (Rect bound : result) {
			Imgproc.rectangle(linesImg, bound.tl(), bound.br(), new Scalar(177, 0, 0));
		}
		Imgcodecs.imwrite("out.jpg", linesImg);
	}

	static Rect getBounds(Rect r1, Rect r2) {
		return getBoundsRecursive(r1, r2, true);
	}

	void test() {
		Rect r;
		Point p;
	}

	static Point tl(Rect r1, Rect r2) {
		return new Point(Math.min(r1.tl().x, r2.tl().x), Math.max(r1.tl().y, r2.tl().y));
	}

	static Point br(Rect r1, Rect r2) {
		return new Point(Math.max(r1.br().x, r2.br().x), Math.min(r1.br().y, r2.br().y));
	}

	static Rect getBoundsRecursive(Rect r1, Rect r2, boolean recurse) {
		if (r1.contains(r2.br())) {
			// if r2 completely inside r1, return r1;
			if (r1.contains(r2.tl())) {
				return r1;
			} else {
				// find new tl point.
				return new Rect(tl(r1, r2), r1.br());
			}
		} else if (r1.contains(r2.tl())) {
			if (r1.contains(r2.br()))
				return r1;
			else {
				// find new br point.
				return new Rect(r1.tl(), br(r1, r2));
			}
		} else {
			// for checking if r2 contains r1, swap arguments and recourse.
			if (distance(r1.br(), r2.tl()) < 25) {
				return new Rect(tl(r1, r2), br(r1, r2));
			} else if (recurse) {
				return getBoundsRecursive(r2, r1, false);
			}
			// return null, when both are not intersected.
			return null;
		}
	}

	// FIXME this is not the right way to do it...
	static double distance(Point p1, Point p2) {
		return Math.sqrt(Math.abs(p1.x - p2.x) * Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y) * Math.abs(p1.y - p2.y));
	}
}
