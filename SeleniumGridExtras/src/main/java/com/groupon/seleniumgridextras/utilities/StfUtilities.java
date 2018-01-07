package com.groupon.seleniumgridextras.utilities;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.log4j.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import okhttp3.*;

/**
 * Created by jfarrier on 4/04/2017.
 */
public class StfUtilities {
    private static Logger logger = Logger.getLogger(HttpUtility.class);


    //Look at using this instead https://github.com/email2vimalraj/appium-stf-example/blob/master/src/main/java/com/vimalselvam/DeviceApi.java

    public static void addDeviceInUseStf(String device, String stfURL, String stfToken) {
        //TODO
        /*
        https://github.com/openstf/stf/blob/master/doc/API.md#devices
        Attempts to add a device under the authenticated user's control. This is analogous to pressing "Use" in the UI.

        POST /api/v1/user/devices
        Using cURL:

        curl -X POST --header "Content-Type: application/json" --data '{"serial":"EP7351U3WQ"}' -H "Authorization: Bearer YOUR-TOKEN-HERE" https://stf.example.org/api/v1/user/devices

         */
        logger.info("setting device to in use for STF");
        logger.info("curl -X POST --header \"Content-Type: application/json\" --data '{\"serial\":\"" + device + "\"}' -H \"Authorization: Bearer "+stfToken+"\" "+ stfURL + "/api/v1/user/devices");
        //Process cmdProc = null;
        try {
            ResteasyClient client = new ResteasyClientBuilder().build();
            ResteasyWebTarget target = client.target(stfURL + "/api/v1/user/devices");
            Entity<String> entity = Entity.entity("{\"serial\":\"" + device + "\"}", MediaType.APPLICATION_JSON);
            Response response = target.request(MediaType.APPLICATION_JSON).header("Authorization", "Bearer "+stfToken).post(entity);
            String value = response.readEntity(String.class);
            logger.info("SENT STF REQUEST");
            logger.info("Response was " + value);
            response.close();
            //cmdProc = Runtime.getRuntime().exec("curl -X POST --header \"Content-Type: application/json\" --data '{\"serial\":\"" + device + "\"}' -H \"Authorization: Bearer "+stfToken+"\" "+ stfURL + "/api/v1/user/devices");
            Thread.sleep(1000);
        } catch (Exception e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        }
        return;
    }


    public static void removeInUseSTF(String device, String stfURL, String stfToken) {

        /*
        Removes a device from the authenticated user's device list. This is analogous to pressing "Stop using" in the UI.

        DELETE /api/v1/user/devices/{serial}
        Using cURL:

        curl -X DELETE -H "Authorization: Bearer YOUR-TOKEN-HERE" https://stf.example.org/api/v1/user/devices/{serial}
        */

        OkHttpClient client = new OkHttpClient();
        JsonParser jsonParser = new JsonParser();

        Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + stfToken)
                .url(stfURL + "/api/v1/user/devices/" + device)
                .delete()
                .build();
        okhttp3.Response response;
        try {
            response = client.newCall(request).execute();
            System.out.println(request.toString());
            System.out.println(response.toString());
            System.out.println(response.message());
            System.out.println(response.body().string());
            //JsonObject jsonObject = jsonParser.parse(response.body().string()).getAsJsonObject();

        } catch (Exception e) {
            throw new IllegalArgumentException("STF service is unreachable", e);
        }
    }

    //what to do about preparing?
    public static Boolean checkDeviceStf(String device, String stfURL, String stfToken) {
        /*
        Returns information about a specific device.

        GET /api/v1/devices/{serial}
        Using cURL:

        curl -H "Authorization: Bearer YOUR-TOKEN-HERE" https://stf.example.org/api/v1/devices/xxxxxxxxx
         */
        logger.info("checking stf for device ready");
        logger.info("curl -H \"Authorization: Bearer "+ stfToken + "\" "+ stfURL + "/api/v1/devices/"+device);

        Boolean isPresentTrue = false;
        Boolean isInUseFalse = false;
        Boolean isNotNoAutomationNote = false;
        Boolean isNoOneOwnerTrue = false;
        String value = "";
        //Process cmdProc = null;
        try {
            ResteasyClient client = new ResteasyClientBuilder().build();
            ResteasyWebTarget target = client.target(stfURL+"/api/v1/devices/"+device);
            Response response = target.request().header("Authorization", "Bearer "+ stfToken).get();
            value = response.readEntity(String.class);
            logger.info(value);
            response.close();
            //cmdProc = Runtime.getRuntime().exec("curl -H \"Authorization: Bearer "+ stfToken + "\" "+ stfURL + "/api/v1/devices/"+device);
            Thread.sleep(1000);
        } catch (Exception e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        }

        if(value.contains("\"present\":true")){
            isPresentTrue = true;
        }
        //check owner is not null
        if(value.contains("\"owner\":null")||value.contains("\"usage\":\"automation\"")){
            isNoOneOwnerTrue = true;
        }
        if(value.contains("\"using\":false")||value.contains("\"usage\":\"automation\"")){
            isInUseFalse = true;
        }
        if(!value.toLowerCase().contains("\"notes\":\"[NoAutomation]".toLowerCase())){
            isNotNoAutomationNote = true;
        }
        logger.info(value);
        logger.info("device in present = " + isPresentTrue);
        logger.info("is there no owner = " + isNoOneOwnerTrue);
        logger.info("device in use = " + !isInUseFalse);
        logger.info("Notes contain NoAutomation "+ isNotNoAutomationNote);

        return isPresentTrue&&isInUseFalse&&isNotNoAutomationNote&&isNoOneOwnerTrue;
    }
}
