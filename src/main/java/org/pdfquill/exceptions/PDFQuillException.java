package org.pdfquill.exceptions;

/**
 * Base runtime exception for all PDF Quill errors.
 */
public class PDFQuillException extends RuntimeException {
    private static final long serialVersionUID = 7489623123456789012L;

    public PDFQuillException(String message) {
        super(message);
    }

    public PDFQuillException(String message, Throwable cause) {
        super(message, cause);
    }
}
