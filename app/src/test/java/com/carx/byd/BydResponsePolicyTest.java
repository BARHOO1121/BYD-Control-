package com.carx.byd;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class BydResponsePolicyTest {
    @Test public void numericAndStringSuccessAreAccepted() throws Exception {
        assertTrue(BydResponsePolicy.isAccepted(new JSONObject("{\"code\":0}")));
        assertTrue(BydResponsePolicy.isAccepted(new JSONObject("{\"code\":\"0\"}")));
    }

    @Test public void qrPayloadWithoutStatusDoesNotAuthorizeConfirmation() throws Exception {
        assertFalse(BydResponsePolicy.isAccepted(new JSONObject("{\"respondData\":\"SYNTHETIC-ENCRYPTED-DATA\"}")));
        assertFalse(BydResponsePolicy.isAccepted(new JSONObject("{\"code\":null,\"respondData\":\"SYNTHETIC\"}")));
        assertFalse(BydResponsePolicy.isAccepted(new JSONObject("{\"success\":true}")));
    }

    @Test public void rejectionAndConflictingSuccessNeverAuthorize() throws Exception {
        assertFalse(BydResponsePolicy.isAccepted(new JSONObject("{\"code\":1005,\"respondData\":\"SYNTHETIC\"}")));
        assertFalse(BydResponsePolicy.isAccepted(new JSONObject("{\"code\":0,\"success\":false}")));
        assertFalse(BydResponsePolicy.isAccepted(null));
    }

    @Test public void onlyKnownSessionExpiryCodesTriggerReauthentication() {
        assertTrue(BydResponsePolicy.isExpired("1002"));
        assertTrue(BydResponsePolicy.isExpired("1005"));
        assertTrue(BydResponsePolicy.isExpired("1010"));
        assertFalse(BydResponsePolicy.isExpired("0"));
        assertFalse(BydResponsePolicy.isExpired("other"));
        assertFalse(BydResponsePolicy.isExpired(null));
    }
}
