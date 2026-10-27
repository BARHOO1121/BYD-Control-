package com.carx.byd;

/** Separates account authentication from vehicle retrieval, without Android or network dependencies. */
public final class AccountVehicleLoader {
    public enum Stage { PREPARE, LOGIN, VEHICLES }

    public interface Source {
        void prepare() throws Exception;
        void login() throws Exception;
        VehicleListResult fetchVehicles() throws Exception;
    }

    public static final class Outcome {
        public final boolean authenticated;
        public final Stage stage;
        public final VehicleListResult vehicleResult;
        public final Exception failure;

        private Outcome(boolean authenticated, Stage stage, VehicleListResult result, Exception failure) {
            this.authenticated = authenticated;
            this.stage = stage;
            this.vehicleResult = result;
            this.failure = failure;
        }
    }

    private AccountVehicleLoader() {}

    public static Outcome load(Source source) {
        Stage stage = Stage.PREPARE;
        boolean authenticated = false;
        try {
            source.prepare();
            stage = Stage.LOGIN;
            source.login();
            authenticated = true;
            stage = Stage.VEHICLES;
            VehicleListResult result = source.fetchVehicles();
            if (result == null) throw new IllegalStateException();
            return new Outcome(true, stage, result, null);
        } catch (Exception failure) {
            return new Outcome(authenticated, stage, null, failure);
        }
    }
}
