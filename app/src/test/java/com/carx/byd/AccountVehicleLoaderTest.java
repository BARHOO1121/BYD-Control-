package com.carx.byd;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class AccountVehicleLoaderTest {
    private static class FakeSource implements AccountVehicleLoader.Source {
        int prepareCalls, loginCalls, listCalls;
        Exception preparationFailure, loginFailure, vehicleFailure;
        VehicleListResult result = VehicleListParser.parse(new JSONArray());
        public void prepare() throws Exception { prepareCalls++; if (preparationFailure != null) throw preparationFailure; }
        public void login() throws Exception { loginCalls++; if (loginFailure != null) throw loginFailure; }
        public VehicleListResult fetchVehicles() throws Exception { listCalls++; if (vehicleFailure != null) throw vehicleFailure; return result; }
    }

    @Test public void preparationFailureNeverAttemptsLogin() {
        FakeSource source = new FakeSource(); source.preparationFailure = new IOException("synthetic");
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(source);
        assertFalse(outcome.authenticated); assertEquals(AccountVehicleLoader.Stage.PREPARE, outcome.stage);
        assertEquals(0, source.loginCalls); assertEquals(0, source.listCalls);
    }

    @Test public void rejectedLoginNeverRequestsVehicles() {
        FakeSource source = new FakeSource(); source.loginFailure = new IOException("synthetic");
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(source);
        assertFalse(outcome.authenticated); assertEquals(AccountVehicleLoader.Stage.LOGIN, outcome.stage);
        assertEquals(0, source.listCalls);
    }

    @Test public void listNetworkFailureRetainsEarlierLoginSuccess() {
        FakeSource source = new FakeSource(); source.vehicleFailure = new IOException("synthetic");
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(source);
        assertTrue(outcome.authenticated); assertEquals(AccountVehicleLoader.Stage.VEHICLES, outcome.stage);
        assertSame(source.vehicleFailure, outcome.failure); assertNull(outcome.vehicleResult);
    }

    @Test public void unsupportedListIsNotALoginFailure() {
        FakeSource source = new FakeSource(); source.result = VehicleListParser.parse(new JSONObject());
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(source);
        assertTrue(outcome.authenticated); assertNull(outcome.failure);
        assertEquals(VehicleListResult.Status.UNSUPPORTED_FORMAT, outcome.vehicleResult.status);
    }

    @Test public void explicitEmptyListIsNotALoginFailure() {
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(new FakeSource());
        assertTrue(outcome.authenticated); assertNull(outcome.failure);
        assertEquals(VehicleListResult.Status.EMPTY, outcome.vehicleResult.status);
    }

    @Test public void successfulListPreservesOriginalRecordsAndOrder() throws Exception {
        JSONArray cars = new JSONArray("[{\"vin\":\"SYNTHETIC-CAR-001\",\"energyType\":1,\"features\":{\"trunk\":true}},{\"vin\":\"SYNTHETIC-CAR-002\"}]");
        FakeSource source = new FakeSource(); source.result = VehicleListParser.parse(cars);
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(source);
        assertTrue(outcome.authenticated); assertNull(outcome.failure);
        assertSame(cars, outcome.vehicleResult.vehicles);
        assertEquals("SYNTHETIC-CAR-001", outcome.vehicleResult.vehicles.getJSONObject(0).getString("vin"));
        assertTrue(outcome.vehicleResult.vehicles.getJSONObject(0).getJSONObject("features").getBoolean("trunk"));
        assertEquals(1, source.prepareCalls); assertEquals(1, source.loginCalls); assertEquals(1, source.listCalls);
    }

    @Test public void absentResultIsARetrievalFailure() {
        FakeSource source = new FakeSource(); source.result = null;
        AccountVehicleLoader.Outcome outcome = AccountVehicleLoader.load(source);
        assertTrue(outcome.authenticated); assertEquals(AccountVehicleLoader.Stage.VEHICLES, outcome.stage);
        assertNotNull(outcome.failure);
    }
}
