package org.pdfquill.exceptions;

/**
 * Represents failures while persisting the generated PDF to disk.
 */
public class PDFExportException extends PDFGenerationException {
    private static final long serialVersionUID = -6269306958239718900L;

    public PDFExportException(String message) {
        super(message);
    }

    public PDFExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
