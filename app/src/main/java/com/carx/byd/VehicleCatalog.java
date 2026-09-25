package com.carx.byd;

import org.json.JSONObject;

public final class VehicleCatalog {
    private VehicleCatalog() {}

    public static String rawName(JSONObject car) {
        if (car == null) return "BYD";
        String alias = BydApiClient.value(car, "autoAlias", "auto_alias");
        String model = BydApiClient.value(car, "modelName", "model_name", "seriesName", "series_name");
        if (!"—".equals(alias) && !alias.trim().isEmpty()) return alias;
        return "—".equals(model) ? "BYD" : model;
    }

    public static String arabicName(JSONObject car) {
        String raw = rawName(car);
        if (raw.contains("海鸥荣耀版")) return "سيجل – الفئة الفاخرة";
        if (raw.contains("海鸥")) return "BYD سيجل";
        if (raw.contains("海豚")) return "BYD دولفين";
        if (raw.contains("海豹")) return "BYD سيل";
        if (raw.contains("元PLUS") || raw.toUpperCase().contains("ATTO 3")) return "BYD أتو 3";
        if (raw.contains("宋PLUS")) return "BYD سونغ بلس";
        if (raw.contains("宋Pro") || raw.contains("宋PRO")) return "BYD سونغ برو";
        if (raw.contains("秦PLUS")) return "BYD تشين بلس";
        if (raw.contains("汉")) return "BYD هان";
        if (raw.contains("唐")) return "BYD تانغ";
        if (raw.contains("豹8")) return "BYD ليوبارد 8";
        if (raw.contains("豹5")) return "BYD ليوبارد 5";
        if (raw.contains("腾势")) return "DENZA";
        return raw;
    }

    public static String findImageUrl(JSONObject car) {
        if (car == null) return "";
        String[] keys={"autoImage","auto_image","carImage","car_image","imageUrl","image_url","imgUrl","img_url","modelPic","model_pic","vehicleImage","vehicle_image","carImg","car_img","picUrl","pic_url"};
        for(String k:keys){
            String v=BydApiClient.value(car,k);
            if(v!=null && !"—".equals(v) && (v.startsWith("https://") || v.startsWith("http://"))) return v;
        }
        return "";
    }
}
