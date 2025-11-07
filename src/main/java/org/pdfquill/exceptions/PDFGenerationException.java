package org.pdfquill.exceptions;

/**
 * Signals failures while generating the PDF document in memory.
 */
public class PDFGenerationException extends PDFQuillException {
    private static final long serialVersionUID = -8241358458402546420L;

    public PDFGenerationException(String message) {
        super(message);
    }

    public PDFGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
