package org.pdfquill.exceptions;

/**
 * Indicates problems while creating barcode or QR code images.
 */
public class BarcodeGenerationException extends PDFGenerationException {
    private static final long serialVersionUID = -1003954668128612070L;

    public BarcodeGenerationException(String message) {
        super(message);
    }

    public BarcodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
