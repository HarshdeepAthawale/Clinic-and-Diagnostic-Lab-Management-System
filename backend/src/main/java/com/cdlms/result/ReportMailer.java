package com.cdlms.result;

/** Tells a patient their report is ready. Throwing leaves the report undispatched, so it can be retried. */
public interface ReportMailer {

    /** Everything the message needs; the report itself is never attached — the patient signs in to open it. */
    record Notice(String email, String patientName, String sampleCode, java.util.List<String> testNames) {
    }

    void send(Notice notice);
}
