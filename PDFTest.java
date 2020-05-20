import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public class PDFTest {
	/**
	 * The scaling factor for font units to PDF units
	 */
	private static final int FONTSCALE = 1000;

	/**
	 * The default font
	 */
	private static final PDType1Font DEFAULT_FONT = PDType1Font.HELVETICA;

	/**
	 * The default font size
	 */
	private static final int DEFAULT_FONT_SIZE = 10;

	/**
	 * The line height as a factor of the font size
	 */
	private static final float LINE_HEIGHT_FACTOR = 1.05f;

	private int fontSize = DEFAULT_FONT_SIZE;
	private PDRectangle mediaBox = PDRectangle.LETTER;
	private boolean landscape = false;
	private PDFont font = DEFAULT_FONT;
	private int margin = 20;
	private int sitchMargin = 20;
	private int splitType = 1;

	private final int[][] pos2x2x2 = { { 0, 0 }, { 1, 0 }, { 1, 0 }, { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 1 }, { 0, 1 } };
	private final int[][] pos4x2x2 = { { 0, 0 }, { 3, 0 }, { 1, 0 }, { 2, 0 }, { 2, 0 }, { 1, 0 }, { 3, 0 }, { 0, 0 },
			{ 0, 1 }, { 3, 1 }, { 1, 1 }, { 2, 1 }, { 2, 1 }, { 1, 1 }, { 3, 1 }, { 0, 1 } };

	private static final Map<String, PDType1Font> STANDARD_14 = new HashMap<String, PDType1Font>();
	static {
		STANDARD_14.put(PDType1Font.TIMES_ROMAN.getBaseFont(), PDType1Font.TIMES_ROMAN);
		STANDARD_14.put(PDType1Font.TIMES_BOLD.getBaseFont(), PDType1Font.TIMES_BOLD);
		STANDARD_14.put(PDType1Font.TIMES_ITALIC.getBaseFont(), PDType1Font.TIMES_ITALIC);
		STANDARD_14.put(PDType1Font.TIMES_BOLD_ITALIC.getBaseFont(), PDType1Font.TIMES_BOLD_ITALIC);
		STANDARD_14.put(PDType1Font.HELVETICA.getBaseFont(), PDType1Font.HELVETICA);
		STANDARD_14.put(PDType1Font.HELVETICA_BOLD.getBaseFont(), PDType1Font.HELVETICA_BOLD);
		STANDARD_14.put(PDType1Font.HELVETICA_OBLIQUE.getBaseFont(), PDType1Font.HELVETICA_OBLIQUE);
		STANDARD_14.put(PDType1Font.HELVETICA_BOLD_OBLIQUE.getBaseFont(), PDType1Font.HELVETICA_BOLD_OBLIQUE);
		STANDARD_14.put(PDType1Font.COURIER.getBaseFont(), PDType1Font.COURIER);
		STANDARD_14.put(PDType1Font.COURIER_BOLD.getBaseFont(), PDType1Font.COURIER_BOLD);
		STANDARD_14.put(PDType1Font.COURIER_OBLIQUE.getBaseFont(), PDType1Font.COURIER_OBLIQUE);
		STANDARD_14.put(PDType1Font.COURIER_BOLD_OBLIQUE.getBaseFont(), PDType1Font.COURIER_BOLD_OBLIQUE);
		STANDARD_14.put(PDType1Font.SYMBOL.getBaseFont(), PDType1Font.SYMBOL);
		STANDARD_14.put(PDType1Font.ZAPF_DINGBATS.getBaseFont(), PDType1Font.ZAPF_DINGBATS);
	}

	public List<List<String>> parseTextToPageLines(Reader text, final float pageWidth, final float pageHeight,
			final float fontHeight) throws IOException {
		List<List<String>> pageList = new ArrayList<List<String>>();
		List<String> pageLines = null;
		try {
			BufferedReader data = new BufferedReader(text);
			String nextLine;
			float y = -1;
			// maxStringLength 除去边框一行显示长度
			float maxStringLength = pageWidth - 2 * margin - sitchMargin;

			// There is a special case of creating a PDF document from an empty
			// string.
			// textIsEmpty 空行处理标记
			boolean textIsEmpty = true;

			while ((nextLine = data.readLine()) != null) {

				// The input text is nonEmpty. New pages will be created and
				// added
				// to the PDF document as they are needed, depending on the
				// length of
				// the text.
				textIsEmpty = false;

				// 去掉行末回车换行
				String[] lineWords = nextLine.replaceAll("[\\n\\r]+$", "").split("");
				int lineIndex = 0;
				while (lineIndex < lineWords.length) {
					StringBuilder nextLineToDraw = new StringBuilder();
					float lengthIfUsingNextWord = 0;
					do {
						String word1;
						word1 = lineWords[lineIndex];
						// word1 is the part before ff, word2 after
						// both can be empty
						// word1 can also be empty without ff, if a line has
						// many spaces
						lengthIfUsingNextWord = (font.getStringWidth(nextLineToDraw.toString() + word1) / FONTSCALE)
								* fontSize;
						if (lengthIfUsingNextWord > maxStringLength)
							break;
						nextLineToDraw.append(word1);
						lineIndex++;
					} while (lineIndex < lineWords.length && lengthIfUsingNextWord < maxStringLength);
					if (y < margin) {
						// 新页
						pageLines = new ArrayList<String>();
						pageList.add(pageLines);
						y = pageHeight - margin + fontHeight;
					}
					pageLines.add(nextLineToDraw.toString());

					y -= fontHeight;
				}
			}

			// 将页面添加文档
			if (textIsEmpty) {
				pageList.add(new ArrayList<String>());
			}
		} catch (IOException io) {
			throw io;
		}
		return pageList;
	}

	// type 1 a4 2x2 2 a4 4x2
	public void createPDFFromPages(PDDocument doc, Reader text) throws IOException {
		int splitX = splitType == 1 ? 2 : 4;
		int splitY = splitType == 1 ? 2 : 2;
		int subPageTotal = splitType == 1 ? 8 : 16;
		float subPageWidth = 0, subPageHeight = 0;
		List<List<String>> pages = null;
		try {
			float height = font.getBoundingBox().getHeight() / FONTSCALE;
			PDRectangle actualMediaBox = mediaBox;

			// landscape纸张横向标记
			if (splitType == 2) {
				actualMediaBox = new PDRectangle(mediaBox.getHeight(), mediaBox.getWidth());
			}

			// calculate font height and increase by a factor.
			// 使用因子计算文字高度
			height = height * fontSize * LINE_HEIGHT_FACTOR;
			// page pdf 页面
			PDPage page1 = new PDPage(actualMediaBox);
			PDPage page2 = new PDPage(actualMediaBox);
			subPageWidth = page1.getMediaBox().getWidth() / splitX;
			subPageHeight = page1.getMediaBox().getHeight() / splitY;
			pages = parseTextToPageLines(text, page1.getMediaBox().getWidth() / splitX, page1.getMediaBox().getHeight()
					/ splitY, height);
			// contentStream pdf 内容对象
			PDPageContentStream contentStreamPage1 = null;
			PDPageContentStream contentStreamPage2 = null;
			PDPageContentStream tempContentStreamPage = null;
			float y = -1;

			page1 = new PDPage(actualMediaBox);
			page2 = new PDPage(actualMediaBox);
			doc.addPage(page1);
			doc.addPage(page2);
			contentStreamPage1 = new PDPageContentStream(doc, page1);
			contentStreamPage1.setFont(font, fontSize);
			contentStreamPage1.beginText();
			contentStreamPage2 = new PDPageContentStream(doc, page2);
			contentStreamPage2.setFont(font, fontSize);
			contentStreamPage2.beginText();

			for (int i = 0; i < pages.size(); ++i) {
				if (i > 0 && i % subPageTotal == 0) {
					page1 = new PDPage(actualMediaBox);
					page2 = new PDPage(actualMediaBox);
					doc.addPage(page1);
					doc.addPage(page2);
					contentStreamPage1 = new PDPageContentStream(doc, page1);
					contentStreamPage1.setFont(font, fontSize);
					contentStreamPage1.beginText();
					contentStreamPage2 = new PDPageContentStream(doc, page2);
					contentStreamPage2.setFont(font, fontSize);
					contentStreamPage2.beginText();
				}
				List<String> p = pages.get(i);
				// 新页文字初始位置
				int tempPageX = splitType == 1 ? pos2x2x2[i % subPageTotal][0] : pos4x2x2[i % subPageTotal][0];
				int tempPageY = splitType == 1 ? pos2x2x2[i % subPageTotal][1] : pos4x2x2[i % subPageTotal][1];
				y = subPageHeight * (splitY - tempPageY) - margin + height;
				System.out.println("tempPageX " + tempPageX + " tempPageY " + tempPageY);
				System.out.println("posx " + (tempPageX * subPageWidth + margin) + " , posy "
						+ (tempPageY * subPageHeight + y));
				if (i % 2 == 0) {
					System.out.println("page 1");
					tempContentStreamPage = contentStreamPage1;
				} else {
					tempContentStreamPage = contentStreamPage2;
				}
				float currX = tempPageX*subPageWidth+margin+sitchMargin, currY = tempPageY*subPageHeight+y;
				tempContentStreamPage.newLineAtOffset(currX,  currY);
				for (int j = 0; j < p.size(); ++j) {
					// System.out.println("----"+p.get(j));
					tempContentStreamPage.newLineAtOffset(0, -height);
					tempContentStreamPage.showText(p.get(j));
					currY-=height;
				}
				//System.out.println("end posx " + -(currX) + " , posy " + -currY);
				tempContentStreamPage.newLineAtOffset(-currX,  -currY);
				tempContentStreamPage = null;
				if (((i + 1) % subPageTotal) == 0) {
					contentStreamPage1.endText();
					contentStreamPage1.close();
					contentStreamPage1 = null;
					contentStreamPage2.endText();
					contentStreamPage2.close();
					contentStreamPage2 = null;
				}
			}
			if (contentStreamPage1 != null) {
				contentStreamPage1.endText();
				contentStreamPage1.close();
				contentStreamPage1 = null;
			}
			if (contentStreamPage2 != null) {
				contentStreamPage2.endText();
				contentStreamPage2.close();
				contentStreamPage2 = null;
			}

		} catch (IOException io) {
			if (doc != null) {
				doc.close();
			}
			throw io;
		}
	}

	private static PDRectangle createRectangle(String paperSize) {
		if ("letter".equalsIgnoreCase(paperSize)) {
			return PDRectangle.LETTER;
		} else if ("legal".equalsIgnoreCase(paperSize)) {
			return PDRectangle.LEGAL;
		} else if ("A0".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A0;
		} else if ("A1".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A1;
		} else if ("A2".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A2;
		} else if ("A3".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A3;
		} else if ("A4".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A4;
		} else if ("A5".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A5;
		} else if ("A6".equalsIgnoreCase(paperSize)) {
			return PDRectangle.A6;
		} else {
			return null;
		}
	}

	/**
	 * This will print out a message telling how to use this example.
	 */
	private void usage() {
		String[] std14 = getStandard14Names();

		StringBuilder message = new StringBuilder();
		message.append("Usage: jar -jar pdfbox-app-x.y.z.jar TextToPDF [options] <outputfile> <textfile>\n");
		message.append("\nOptions:\n");
		message.append("  -standardFont <name> : ").append(DEFAULT_FONT.getBaseFont()).append(" (default)\n");

		for (String std14String : std14) {
			message.append("                         ").append(std14String).append("\n");
		}
		message.append("  -ttf <ttf file>      : The TTF font to use.\n");
		message.append("  -fontSize <fontSize> : default: ").append(DEFAULT_FONT_SIZE).append("\n");
		/*
		 * message.append("  -pageSize <pageSize> : Letter (default)\n");
		 * message.append("                         Legal\n");
		 * message.append("                         A0\n");
		 * message.append("                         A1\n");
		 * message.append("                         A2\n");
		 * message.append("                         A3\n");
		 * message.append("                         A4\n");
		 * message.append("                         A5\n");
		 * message.append("                         A6\n");
		 */
		message.append("  -landscape           : sets orientation to landscape");

		System.err.println(message.toString());
		System.exit(1);
	}

	/**
	 * A convenience method to get one of the standard 14 font from name.
	 *
	 * @param name
	 *            The name of the font to get.
	 *
	 * @return The font that matches the name or null if it does not exist.
	 */
	private static PDType1Font getStandardFont(String name) {
		return STANDARD_14.get(name);
	}

	/**
	 * This will get the names of the standard 14 fonts.
	 *
	 * @return An array of the names of the standard 14 fonts.
	 */
	private static String[] getStandard14Names() {
		return STANDARD_14.keySet().toArray(new String[14]);
	}

	/**
	 * @return Returns the font.
	 */
	public PDFont getFont() {
		return font;
	}

	/**
	 * @param aFont
	 *            The font to set.
	 */
	public void setFont(PDFont aFont) {
		this.font = aFont;
	}

	/**
	 * @return Returns the fontSize.
	 */
	public int getFontSize() {
		return fontSize;
	}

	/**
	 * @param aFontSize
	 *            The fontSize to set.
	 */
	public void setFontSize(int aFontSize) {
		this.fontSize = aFontSize;
	}

	/**
	 * Sets page size of produced PDF.
	 *
	 * @return returns the page size (media box)
	 */
	public PDRectangle getMediaBox() {
		return mediaBox;
	}

	/**
	 * Sets page size of produced PDF.
	 *
	 * @param mediaBox
	 */
	public void setMediaBox(PDRectangle mediaBox) {
		this.mediaBox = mediaBox;
	}

	/**
	 * Tells the paper orientation.
	 *
	 * @return true for landscape orientation
	 */
	public boolean isLandscape() {
		return landscape;
	}

	/**
	 * Sets paper orientation.
	 *
	 * @param landscape
	 */
	public void setLandscape(boolean landscape) {
		this.landscape = landscape;
	}

	public void setSplitTytpe(int t) {
		this.splitType = t;
	}

	public static void main(String[] args) throws Exception {
		// suppress the Dock icon on OS X
		System.setProperty("apple.awt.UIElement", "true");

		PDFTest app = new PDFTest();

		PDDocument doc = new PDDocument();
		try {
			if (args.length < 2) {
				app.usage();
			} else {
				for (int i = 0; i < args.length - 2; i++) {
					if (args[i].equals("-standardFont")) {
						i++;
						app.setFont(getStandardFont(args[i]));
					} else if (args[i].equals("-ttf")) {
						i++;
						PDFont font = PDType0Font.load(doc, new File(args[i]));
						app.setFont(font);
					} else if (args[i].equals("-fontSize")) {
						i++;
						app.setFontSize(Integer.parseInt(args[i]));
					}
					/*
					 * else if( args[i].equals( "-pageSize" )) { i++;
					 * PDRectangle rectangle = createRectangle(args[i]); if
					 * (rectangle == null) { throw new
					 * IOException("Unknown argument: " + args[i]); }
					 * app.setMediaBox(rectangle); } else if( args[i].equals(
					 * "-landscape" )) { app.setLandscape(true); }
					 */
					else if (args[i].equals("-splittype")) {
						i++;
						if ("4".equals(args[i])) {
							app.setSplitTytpe(1);
						}
						if ("8".equals(args[i])) {
							app.setSplitTytpe(2);
						}
					} else {
						throw new IOException("Unknown argument: " + args[i]);
					}

					PDRectangle rectangle = createRectangle("A4");
					app.setMediaBox(rectangle);
				}

				FileReader fileReader = new FileReader(args[args.length - 1]);
				// app.createPDFFromText(doc, fileReader);
				app.createPDFFromPages(doc, fileReader);
				fileReader.close();
				doc.save(args[args.length - 2]);
			}
		} finally {
			doc.close();
		}
	} // end main

} // end class PDFTest

