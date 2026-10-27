package com.carx.byd;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class VehicleListParserTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> fixtures() {
        return Arrays.asList(new Object[][] {
            {"original direct list", "[{\"vin\":\"SYNTHETIC-CAR-001\",\"energyType\":1}]", VehicleListResult.Status.LOADED, "$"},
            {"original named list", "{\"diLinkAutoInfoList\":[{\"vin\":\"SYNTHETIC-CAR-001\"}]}", VehicleListResult.Status.LOADED, "$.diLinkAutoInfoList"},
            {"data array", "{\"data\":[{\"vin\":\"SYNTHETIC-CAR-001\"}]}", VehicleListResult.Status.LOADED, "$.data"},
            {"data named list", "{\"data\":{\"diLinkAutoInfoList\":[{\"vin\":\"SYNTHETIC-CAR-001\"}]}}", VehicleListResult.Status.LOADED, "$.data.diLinkAutoInfoList"},
            {"result array", "{\"result\":[{\"vin\":\"SYNTHETIC-CAR-001\"}]}", VehicleListResult.Status.LOADED, "$.result"},
            {"result data object", "{\"result\":{\"data\":{\"diLinkAutoInfoList\":[{\"vin\":\"SYNTHETIC-CAR-001\"}]}}}", VehicleListResult.Status.LOADED, "$.result.data.diLinkAutoInfoList"},
            {"explicit direct empty", "[]", VehicleListResult.Status.EMPTY, "$"},
            {"explicit named empty", "{\"diLinkAutoInfoList\":[]}", VehicleListResult.Status.EMPTY, "$.diLinkAutoInfoList"},
            {"explicit wrapped empty", "{\"code\":0,\"data\":[]}", VehicleListResult.Status.EMPTY, "$.data"},
            {"empty object is not no cars", "{}", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"null list is not no cars", "{\"diLinkAutoInfoList\":null}", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"string list is not no cars", "{\"diLinkAutoInfoList\":\"[]\"}", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"wrong field", "{\"cars\":[]}", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"unexpected response null", "null", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"unexpected response string", "\"not a list\"", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"unexpected response number", "123", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"unexpected response boolean", "false", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"over-depth wrapper", "{\"data\":{\"data\":{\"data\":[]}}}", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"nested server rejection", "{\"code\":1005,\"data\":[]}", VehicleListResult.Status.SERVER_REJECTED, "none"},
            {"nested failure flag", "{\"success\":false,\"diLinkAutoInfoList\":[]}", VehicleListResult.Status.SERVER_REJECTED, "none"},
            {"nested error in data", "{\"data\":{\"code\":1002,\"diLinkAutoInfoList\":[]}}", VehicleListResult.Status.SERVER_REJECTED, "none"},
            {"two possible lists", "{\"data\":[],\"result\":[{\"vin\":\"SYNTHETIC-CAR-001\"}]}", VehicleListResult.Status.AMBIGUOUS_FORMAT, "none"},
            {"empty cannot hide another list", "{\"diLinkAutoInfoList\":[],\"data\":[]}", VehicleListResult.Status.AMBIGUOUS_FORMAT, "none"},
            {"malformed competing field", "{\"diLinkAutoInfoList\":null,\"data\":[]}", VehicleListResult.Status.UNSUPPORTED_FORMAT, "none"},
            {"scalar row", "[7]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"null row", "[null]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"missing VIN", "[{\"name\":\"SYNTHETIC\"}]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"numeric VIN", "[{\"vin\":123}]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"empty VIN", "[{\"vin\":\"  \"}]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"null VIN string", "[{\"vin\":\"null\"}]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"missing VIN sentinel", "[{\"vin\":\"—\"}]", VehicleListResult.Status.INVALID_RECORDS, "$"},
            {"mixed rows do not partly activate controls", "[{\"vin\":\"SYNTHETIC-CAR-001\"},{}]", VehicleListResult.Status.INVALID_RECORDS, "$"}
        });
    }

    private final String json;
    private final VehicleListResult.Status status;
    private final String path;

    public VehicleListParserTest(String name, String json, VehicleListResult.Status status, String path) {
        this.json = json;
        this.status = status;
        this.path = path;
    }

    @Test public void distinguishExplicitEmptyFromUnusableResponse() throws Exception {
        VehicleListResult result = VehicleListParser.parse(new JSONTokener(json).nextValue());
        assertEquals(status, result.status);
        assertEquals(path, result.path);
        assertEquals(status == VehicleListResult.Status.LOADED || status == VehicleListResult.Status.EMPTY, result.isRecognized());
        if (status != VehicleListResult.Status.LOADED) assertEquals(0, result.vehicles.length());
    }
}
