# PDF Quill

Java library focused on generating print-ready PDFs for receipts, tickets, and other compact documents with fine-grained control over layout, typography, barcodes, and cut marks for both thermal and standard printers.

## Key Features
- PDF generation powered by Apache PDFBox with declarative layout configuration (margins, printable area, line height, lines per page)
- Support for multiple paper formats (`A4`, `A5`, `THERMAL_56MM`, and more) with thermal paper detection for smart cropping
- Text printing with automatic word wrapping, mixed font styles per line through `TextBuilder`, optional whitespace preservation, line skipping helpers (`skipLine`/`skipLines`), and cut signals via `cutSignal`
- Image and barcode/QR Code rendering using ZXing through `printImage` and `printBarcode`
- Font customization (`FontSettings`) and basic PDF permission control (`PermissionSettings`)
- Output helpers: Base64 (`getBase64PDFBytes`), raw bytes (`getPDFBytes`), temp files (`getPDFFile`), or custom paths via `writePDF(Path)`

## Requirements
- JDK 8+ (compiled for Java 8 bytecode; runs on newer JDKs as well)
- Maven 3.9+

## Java Version Support
- Source and target compatibility set to Java 8 for broad runtime support.
- Verified with JDK 8; the published JAR works unchanged on modern JDKs (11, 17, 21, ...).

## Build
```bash
mvn clean package
```
The compiled artifact will be available at `target/pdf-quill-1.0-SNAPSHOT.jar`. Run `mvn install` to publish it into the local Maven cache and consume it from other Maven or Gradle projects.

## Quick Start

```java
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.pdfquill.PDFQuill;
import org.pdfquill.exceptions.PDFQuillException;
import org.pdfquill.barcode.BarcodeType;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.font.FontSettings;

FontSettings fontSettings = new FontSettings();
fontSettings.setFontSize(10);

PDFQuill quill = PDFQuill.builder()
        .withPaperType(PaperType.THERMAL_56MM)
        .withFontSettings(fontSettings)
        .preserveSpaces(true)
        .build();

try {
    quill.printLine("Sample Store");
    quill.printLine("Full address line");
    quill.skipLine();
    quill.printBarcode("123456789012", BarcodeType.CODE128);
    quill.cutSignal();

    String pdfBase64 = quill.getBase64PDFBytes();
    byte[] pdfBytes = quill.getPDFBytes();
    File pdfFile = quill.getPDFFile();
    // Or write to a specific location:
    quill.writePDF(Paths.get("/tmp/receipt.pdf"));
    // send pdfBase64/pdfBytes/pdfFile to printer, API, etc.
} catch (PDFQuillException | IOException e) {
    // handle failure (retry, log, etc.)
}
```

## Output Options
- `getPDFBytes()`: returns the PDF bytes; modify or persist them as needed.
- `getBase64PDFBytes()`: returns a Base64 string, convenient for transport over JSON or HTTP APIs.
- `getPDFFile()`: writes the document to a temporary `.pdf` file (deleted on JVM exit) and returns it for direct printing or storage.
- `writePDF(Path)`: writes to any provided location, creating parent directories when necessary.

## Rich Text Blocks

For multi-style lines (bold headers, different sizes, mixed fragments), build a `TextBuilder` and send it to `writeFromTextBuilder`. The builder accepts `String` overloads—use the variant with `FontSettings` whenever you need to tweak styling for a specific fragment.

```java
import java.io.IOException;

import org.pdfquill.writer.TextBuilder;
import org.pdfquill.settings.font.FontSettings;
import org.pdfquill.settings.font.FontType;

FontSettings regularFont = new FontSettings();
FontSettings titleFont = new FontSettings();
titleFont.setSelectedFont(titleFont.getFontByFontType(FontType.BOLD));
titleFont.setFontSize(16);

TextBuilder builder = new TextBuilder()
        .addText("Subtotal: ", regularFont)
        .addText("R$ 29,90", titleFont)
        .addText(" (promo)");

try {
    quill.writeFromTextBuilder(builder);
} catch (IOException e) {
    // handle layout or rendering failure
}
```

## Configuration Tips
- **Fonts**: tweak default/bold/italic fonts via `FontSettings` or rely on `configureFontSettings` for inline customization in the builder.
- **Layout**: instantiate `PageLayout` manually or combine `withPaperType` with `withPageLayout` to customize margins, line height, and maximum line width.
- **Line breaks**: use `skipLine()` or `skipLines(int)` to insert vertical spacing without emitting text while keeping pagination intact.
- **Permissions**: enable or disable printing, editing, and content extraction with `withPermissionSettings` or `configurePermissionSettings`.
- **Whitespace**: call `preserveSpaces(true)` to keep leading spaces, which is handy for manual alignment in receipts.
- **Images**: `printImage` accepts a `ByteArrayInputStream`; convert files using `Files.readAllBytes(path)`.

## Dependencies
- [Apache PDFBox](https://pdfbox.apache.org/) for PDF rendering
- [ZXing](https://github.com/zxing/zxing) for barcode and QR Code generation
- `javax.xml.bind` for Base64 encoding
