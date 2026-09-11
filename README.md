# PDF Quill

Java library focused on generating print-ready PDFs for receipts, tickets, and other compact documents with fine-grained control over layout, typography, barcodes, and receipt spacing for both thermal and standard printers.

PDF Quill builds documents sequentially through a Java API. It is useful for point-of-sale receipts, ticket generation, and compact reports. Your application handles delivery to a printer or another service.

## Contents

- [Requirements and build](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Output and document lifecycle](#output-options)
- [Paper and layout](#paper-and-layout)
- [Rich text](#rich-text-blocks)
- [Text alignment](#text-alignment)
- [Images and barcodes](#images-and-barcodes)
- [Limitations and troubleshooting](#limitations-and-troubleshooting)
- [Development and contributing](#development-and-contributing)
- [License](#license)

## Key Features

- PDF generation powered by Apache PDFBox with declarative layout configuration (margins, printable area, line height, lines per page)
- Support for multiple paper formats (`A4`, `A5`, `THERMAL_56MM`, and more) with thermal paper detection for smart cropping
- Text printing with automatic word wrapping, mixed font styles per line through `TextBuilder`, line skipping helpers (`skipLine`/`skipLines`), and receipt spacing via `cutSignal`
- Text alignment (`Alignment.LEFT`/`RIGHT`/`CENTER`) for `printLine`, configurable as a global default or overridden per call
- Image and barcode/QR Code rendering using ZXing through `printImage` and `printBarcode`
- Font customization (`FontSettings`) with regular, bold, italic, and bold italic variants
- Output helpers: Base64 (`getBase64PDFBytes`), raw bytes (`getPDFBytes`), temp files (`getPDFFile`), or custom paths via `writePDF(Path)`

## Requirements
- JDK 8 or newer (source and target are configured for Java 8; CI uses JDK 8)
- Maven 3.9+

## Java Version Support
- Source and target compatibility set to Java 8 for broad runtime support.
- The repository CI runs unit tests with Temurin JDK 8. Newer JDKs are not covered by the current CI matrix.

## Build
```bash
mvn clean package
```
The compiled artifact will be available at `target/pdf-quill-1.0-SNAPSHOT.jar`. Run `mvn install` to publish it into the local Maven cache and consume it from other Maven or Gradle projects.

## Installation

Build and install this checkout into your local Maven repository:

```bash
mvn clean install
```

Then add the dependency to the consuming project. These coordinates match the current `pom.xml`; the instructions below use the locally installed snapshot.

**Maven**

```xml
<dependency>
    <groupId>org.pdfquill</groupId>
    <artifactId>pdf-quill</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle (Groovy DSL)**

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.pdfquill:pdf-quill:1.0-SNAPSHOT'
}
```

Maven or Gradle also resolves the runtime dependencies. The generated JAR does not bundle them.

## Quick Start

Save this example as `ReceiptExample.java` in a Java project with the dependency above. Running its `main` method creates `output/receipt.pdf` relative to the working directory.

```java
import java.nio.file.Paths;

import org.pdfquill.PDFQuill;
import org.pdfquill.barcode.BarcodeType;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.Alignment;
import org.pdfquill.settings.font.FontType;

public class ReceiptExample {
    public static void main(String[] args) {
        PDFQuill quill = PDFQuill.builder()
                .withPaperType(PaperType.THERMAL_80MM)
                .configureFontSettings(font -> font.setFontSize(10))
                .build();

        quill.printLine("Sample Store", FontType.BOLD, Alignment.CENTER)
                .printLine("123 Main Street", Alignment.CENTER)
                .skipLine()
                .printLine("Coffee                   5.00")
                .printLine("Sandwich                12.00")
                .skipLine()
                .printLine("Total: 17.00", FontType.BOLD, Alignment.RIGHT)
                .printBarcode("receipt-001", BarcodeType.QRCODE)
                .printLine("Thank you!", Alignment.CENTER);

        quill.writePDF(Paths.get("output", "receipt.pdf"));
    }
}
```

The following examples assume an open `quill` instance and must be called before exporting it.

## Output Options
- `getPDFBytes()`: returns the PDF bytes; modify or persist them as needed.
- `getBase64PDFBytes()`: returns a Base64 string, convenient for transport over JSON or HTTP APIs.
- `getPDFFile()`: creates a temporary `.pdf` file, scheduled for deletion on JVM exit, unless a previously exported file still exists; in that case it returns that file. After `writePDF(Path)`, this can be your custom destination.
- `writePDF(Path)`: writes to any provided location, creating parent directories when necessary.

The first output call finalizes the document, closes the underlying PDFBox document, and caches its bytes. Finish all text, image, and barcode operations before requesting output. Subsequent output calls reuse the finalized content; `getPDFBytes()` returns a defensive copy.

`close()` also finalizes the PDF, and you can retrieve the output afterward. `PDFQuill` does not implement `AutoCloseable`, so it cannot be used directly in try-with-resources. Create a new instance for each document.

`writePDF(Path)` creates missing parent directories and overwrites an existing destination file.

## Paper and Layout

Supported `PaperType` values:

| Family | Formats |
| --- | --- |
| ISO | `A0`, `A1`, `A2`, `A3`, `A4`, `A5`, `A6` |
| North American | `LETTER`, `LEGAL`, `TABLOID` |
| Thermal | `THERMAL_42MM`, `THERMAL_56MM`, `THERMAL_58MM`, `THERMAL_64MM`, `THERMAL_80MM` |

Thermal formats start with a tall page and receive a content-based crop box during export. Standard formats retain their paper dimensions. Text wraps and paginates automatically.

Layout measurements use PDF points (72 points = 1 inch). Use `MeasurementUtils.mmToPt(...)` to convert millimeters.

```java
import org.pdfquill.measurements.MeasurementUtils;
import org.pdfquill.paper.PaperType;

float margin = MeasurementUtils.mmToPt(3f);
PDFQuill quill = PDFQuill.builder()
        .withPaperType(PaperType.THERMAL_80MM)
        .withMargins(margin, margin, margin, margin) // left, right, top, bottom
        .configureFontSettings(font -> font.setFontSize(10))
        .build();
```

Defaults are A4, Courier at 12 points, left alignment, 4-point left/right/bottom margins, and a 12-point top margin. Line height is font size multiplied by 1.15. Individual margins can be overridden with `withMarginLeft`, `withMarginRight`, `withMarginTop`, and `withMarginBottom`.

For reusable configuration, supply a `PageLayout` through `withPageLayout(...)`. The builder copies it, then applies explicit paper, margin, font, and alignment overrides. Keep margins small enough to leave a usable printable area.

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

## Text Alignment

`printLine` supports `Alignment.LEFT`, `Alignment.RIGHT`, and `Alignment.CENTER`. `LEFT` is always the out-of-the-box default (same as a plain `new PDFQuill()`, matching how most text editors behave)—you only get a different default if you explicitly ask for one via the builder. Once a default is set (or left as `LEFT`), you can still override it for individual lines when needed; word-wrapped lines are each aligned independently.

```java
import org.pdfquill.PDFQuill;
import org.pdfquill.settings.Alignment;
import org.pdfquill.settings.font.FontType;

// No withAlignment(...) call -> defaults to LEFT, same as new PDFQuill().
PDFQuill quill = PDFQuill.builder()
        .withAlignment(Alignment.CENTER) // opt in to a different document-wide default
        .build();

quill.printLine("Sample Store");                     // uses the configured default: CENTER
quill.printLine("Total: R$ 29,90", Alignment.RIGHT);  // per-call override
quill.printLine("Item description", FontType.BOLD, Alignment.LEFT); // font + alignment override
```

This applies only to `printLine`; lines built via `TextBuilder`/`writeFromTextBuilder` remain left-anchored for now.

## Images and Barcodes

```java
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.pdfquill.barcode.BarcodeType;

quill.printImage(new ByteArrayInputStream(
        Files.readAllBytes(Paths.get("logo.png"))));
quill.printBarcode("123456789012", BarcodeType.CODE128);
quill.printBarcode("https://example.com/receipts/001", BarcodeType.QRCODE);
```

Image operations above can throw `IOException`; handle it or declare it in the calling method. Images are centered in the printable area. The stream overload renders at 100 × 100 points. The `BufferedImage` overload uses its pixel width and height as PDF point dimensions. Images are not automatically scaled to fit the page width.

Supported barcode types are `UPCA`, `UPCE`, `EAN8`, `EAN13`, `INTERLEAVED2OF5`, `CODE128`, `CODABAR`, `CODE39`, and `QRCODE`. Supply a payload valid for the chosen format.

The overload `printBarcode(code, type, height, width)` controls ZXing raster dimensions, in that argument order. PDF display dimensions remain 48 × 48 mm for QR codes and 80 × 12 mm for other barcodes. Check the printable width when using narrow thermal paper: a barcode can extend beyond the margins.

## Limitations and Troubleshooting

- **Whitespace:** `preserveSpaces(true)` exists on the builder, but the current `printLine` implementation does not apply it. Do not rely on it to preserve leading spaces.
- **Permissions:** `withPermissionSettings` and `configurePermissionSettings` store configuration, but the current export path does not apply PDF protection. They do not enforce printing, editing, or extraction restrictions.
- **Cut signals:** `cutSignal()` adds three line heights of spacing and starts a new page for nonthermal formats. It currently writes spaces, not a visible cut line, and does not send a hardware cutter command.
- **Font coverage:** the font API uses PDFBox `PDType1Font`. Characters unsupported by the selected font can fail during rendering; arbitrary Unicode text and custom TrueType font loading are not exposed by this API.
- **Rich text:** `TextBuilder` content remains left-anchored. Its `addText(String)` overload uses a new default `FontSettings`, so pass explicit settings to keep fragment styling consistent.
- **Font family changes:** when changing font-family setters inside `configureFontSettings`, call `font.loadFontMap()` afterward so `FontType` lookups use the new fonts.
- **Clipped content:** verify paper width, margins, and image/barcode dimensions. Dimensions passed to the barcode overload change raster resolution, not its physical PDF size.
- **Writing after export:** output methods finalize the document. Use a new `PDFQuill` instance to generate another PDF.

Generation failures use `PDFGenerationException`; barcode and file export failures have the more specific `BarcodeGenerationException` and `PDFExportException` subclasses. All extend the unchecked `PDFQuillException`. Image and rich-text methods also expose checked `IOException`. Invalid arguments can raise `IllegalArgumentException`; inspect the cause when reporting a failure.

## Development and Contributing

Run commands from the repository root:

```bash
mvn test           # run the unit tests
mvn clean package  # test and build the JAR
mvn clean install  # also install the artifact locally
```

Tests use JUnit Jupiter and AssertJ. Test reports are written to `target/surefire-reports/`. The GitHub Actions workflow runs `mvn -B test` on JDK 8 for pushes.

| Location | Responsibility |
| --- | --- |
| `src/main/java/org/pdfquill/PDFQuill.java` | Public facade and builder |
| `src/main/java/org/pdfquill/writer/` | Text rendering, cursor state, images, pagination, and export |
| `src/main/java/org/pdfquill/settings/` | Layout, alignment, fonts, and permission configuration |
| `src/main/java/org/pdfquill/formatter/` | Text wrapping and barcode image preparation |
| `src/main/java/org/pdfquill/barcode/` | Barcode formats and ZXing integration |
| `src/main/java/org/pdfquill/paper/` | Paper dimensions and thermal format detection |
| `src/test/java/org/pdfquill/` | Unit and PDF behavior tests |

For bug reports, include a minimal Java example, paper/font settings, Java version, and the expected versus actual output. Use synthetic receipt data in shared examples. For contributions, keep changes focused, add relevant tests for behavior changes, run `mvn test`, and update examples when changing the public API.

## Dependencies

Versions declared in the current [pom.xml](pom.xml):

- Apache PDFBox / FontBox 2.0.27 for PDF rendering.
- ZXing 3.4.1 for barcode and QR code generation.
- JAXB API (`javax.xml.bind:jaxb-api`) 2.3.1 for Base64 encoding.
- JUnit Jupiter 5.10.2 and AssertJ 3.25.3 for tests only.

## License

PDF Quill is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution information.
