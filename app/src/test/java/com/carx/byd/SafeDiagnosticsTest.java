package com.carx.byd;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;

import static org.junit.Assert.*;

public class SafeDiagnosticsTest {
    private static final String SECRET = "SYNTHETIC-PRIVATE-user-15550001111-vin-token-password";

    @Test public void parserSummaryContainsNoServerValuesOrUnknownFieldNames() throws Exception {
        JSONObject data = new JSONObject().put(SECRET, SECRET).put("message", SECRET).put("signToken", SECRET)
                .put("diLinkAutoInfoList", new JSONArray().put(new JSONObject().put("vin", SECRET)));
        VehicleListResult result = VehicleListParser.parse(data);
        assertFalse(result.safeSummary().contains(SECRET));
        assertFalse(result.userMessage().contains(SECRET));
        assertTrue(result.safeSummary().contains("received=1"));
    }

    @Test public void malformedSummaryNeverEchoesTheRejectedRecord() throws Exception {
        VehicleListResult result = VehicleListParser.parse(new JSONArray().put(SECRET));
        assertEquals(VehicleListResult.Status.INVALID_RECORDS, result.status);
        assertFalse(result.safeSummary().contains(SECRET)); assertEquals(1, result.invalidCount);
    }

    @Test public void arbitraryExceptionTextAndCauseAreNotExposed() {
        Throwable[] failures = {new IOException(SECRET), new SocketTimeoutException(SECRET), new GeneralSecurityException(SECRET),
                new IllegalArgumentException(SECRET), new RuntimeException(SECRET, new IOException(SECRET)),
                new BydApiClient.BydException(SECRET)};
        for (Throwable failure : failures) assertFalse(SafeDiagnostics.describe(failure).contains(SECRET));
    }

    @Test public void serverStatusCannotSmuggleAccountDataIntoDiagnostics() {
        for (BydApiClient.BydException.Kind kind : BydApiClient.BydException.Kind.values()) {
            assertFalse(SafeDiagnostics.describe(new BydApiClient.BydException(kind, SECRET)).contains(SECRET));
        }
        assertEquals("other", SafeDiagnostics.responseCode("15550001111"));
    }

    @Test public void knownSessionAndHttpFailuresRemainUnderstandable() {
        assertTrue(SafeDiagnostics.describe(new BydApiClient.BydException(BydApiClient.BydException.Kind.SESSION, "1005")).contains("1005"));
        assertTrue(SafeDiagnostics.describe(new BydApiClient.BydException(BydApiClient.BydException.Kind.HTTP, "503")).contains("503"));
        assertTrue(SafeDiagnostics.describe(new SocketTimeoutException(SECRET)).contains("NETWORK_TIMEOUT"));
    }

    @Test public void absentFailureHasSafeFallback() { assertTrue(SafeDiagnostics.describe(null).contains("UNEXPECTED_ERROR")); }
}
