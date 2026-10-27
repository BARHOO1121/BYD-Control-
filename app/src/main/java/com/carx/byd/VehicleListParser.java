package com.carx.byd;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Accepts the two original schemas plus bounded defensive data/result wrappers.
 * Wrapper support is covered by synthetic fixtures, not claimed as an observed
 * BYD contract. Unknown structures and malformed records are never empty lists.
 */
public final class VehicleListParser {
    private static final int MAX_WRAPPER_DEPTH = 2;
    private static final String[] WRAPPERS = {"data", "result"};

    private VehicleListParser() {}

    public static VehicleListResult parse(Object decoded) {
        Search search = new Search();
        collect(decoded, "$", 0, search);
        String shape = shape(decoded);
        if (search.rejected) return failure(VehicleListResult.Status.SERVER_REJECTED, shape);
        if (search.candidates.size() > 1) return failure(VehicleListResult.Status.AMBIGUOUS_FORMAT, shape);
        if (search.candidates.isEmpty()) return failure(VehicleListResult.Status.UNSUPPORTED_FORMAT, shape);
        Candidate candidate = search.candidates.get(0);
        // A malformed competing known field is not silently discarded.
        if (search.malformed) return failure(VehicleListResult.Status.UNSUPPORTED_FORMAT, shape);

        JSONArray array = candidate.array;
        int invalid = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            Object vin = item == null ? null : item.opt("vin");
            // Keep original records intact; do not guess IDs/aliases for control.
            if (!(vin instanceof String) || ((String) vin).trim().isEmpty()
                    || "null".equalsIgnoreCase(((String) vin).trim()) || "—".equals(vin)) invalid++;
        }
        if (invalid > 0) return new VehicleListResult(VehicleListResult.Status.INVALID_RECORDS,
                new JSONArray(), shape, candidate.path, array.length(), invalid);
        return new VehicleListResult(array.length() == 0 ? VehicleListResult.Status.EMPTY : VehicleListResult.Status.LOADED,
                array, shape, candidate.path, array.length(), 0);
    }

    private static void collect(Object value, String path, int depth, Search search) {
        if (value instanceof JSONArray) {
            search.candidates.add(new Candidate((JSONArray) value, path));
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        // Do not mistake a nested error envelope for successful empty data.
        if ((object.has("code") && !"0".equals(object.optString("code", "")))
                || Boolean.FALSE.equals(object.opt("success"))) search.rejected = true;
        if (object.has("diLinkAutoInfoList")) {
            Object list = object.opt("diLinkAutoInfoList");
            if (list instanceof JSONArray) search.candidates.add(new Candidate((JSONArray) list, path + ".diLinkAutoInfoList"));
            else search.malformed = true;
        }
        if (depth >= MAX_WRAPPER_DEPTH) return;
        for (String wrapper : WRAPPERS) {
            Object child = object.opt(wrapper);
            if (child instanceof JSONObject || child instanceof JSONArray) collect(child, path + "." + wrapper, depth + 1, search);
        }
    }

    private static VehicleListResult failure(VehicleListResult.Status status, String shape) {
        return new VehicleListResult(status, new JSONArray(), shape, "none", 0, 0);
    }

    private static String shape(Object value) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONArray) return "array";
        if (value instanceof JSONObject) return "object";
        if (value instanceof String) return "string";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        return "other";
    }

    private static final class Search {
        final List<Candidate> candidates = new ArrayList<>();
        boolean malformed;
        boolean rejected;
    }

    private static final class Candidate {
        final JSONArray array;
        final String path;
        Candidate(JSONArray array, String path) { this.array = array; this.path = path; }
    }
}
