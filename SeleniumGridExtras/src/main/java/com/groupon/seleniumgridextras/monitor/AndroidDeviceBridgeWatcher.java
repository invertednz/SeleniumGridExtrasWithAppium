package com.groupon.seleniumgridextras.monitor;

import com.groupon.seleniumgridextras.ExecuteCommand;
import com.groupon.seleniumgridextras.config.RuntimeConfig;
import com.groupon.seleniumgridextras.grid.CreateAndroidAppiumNode;
import com.groupon.seleniumgridextras.tasks.KillADB;
import com.groupon.seleniumgridextras.tasks.config.TaskDescriptions;
import com.groupon.seleniumgridextras.utilities.Env;
import com.groupon.seleniumgridextras.utilities.StfUtilities;
import com.groupon.seleniumgridextras.utilities.json.JsonFileReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.groupon.seleniumgridextras.tasks.KillPort;
import com.groupon.seleniumgridextras.utilities.json.JsonCodec;
import com.groupon.seleniumgridextras.utilities.threads.CommonThreadPool;
import com.groupon.seleniumgridextras.utilities.threads.RemoteGridExtrasAsyncCallable;
import org.apache.log4j.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;






/**
 * Created by xhu on 5/06/2014.
 */

//TODO clean up these processes when killing segrid
public class AndroidDeviceBridgeWatcher extends DaemonCallable {
    private static Logger logger = Logger.getLogger(AndroidDeviceBridgeWatcher.class);
    public static List<String> deviceOptions = Arrays.asList("deviceName", "udid", "port", "bootstrap-port", "selendroid-port", "version", "platform-version", "url", "deviceType", "deviceCategory", "location");
    public static List<String> appiumProperties = Arrays.asList("udid", "port", "bootstrap-port", "selendroid-port", "platform-version");
    public static List<String> capProperties = Arrays.asList( "deviceName", "udid", "version", "deviceType", "deviceCategory", "location", "stfURL", "stfToken");
    public static List<String> configProperties = Arrays.asList( "port", "url");
    public static String stfToken;
    public static String stfURL;

    public static AndroidDeviceBridgeWatcher watch(File folder) {
        return (AndroidDeviceBridgeWatcher) new AndroidDeviceBridgeWatcher(folder).start();
    }


    private volatile File androidDeviceConfig;

    public AndroidDeviceBridgeWatcher(File file) {
        androidDeviceConfig = file;

    }

    private ConcurrentHashMap<String, String> nodes;

    @Override
    protected void run() {
        //TODO
        if(nodes==null)
            nodes = new ConcurrentHashMap<String, String>();
        try {
            logger.info("starting to monitor adb");
            File file;
            JsonObject config = null;
            try {
                logger.info("about to read config file " + androidDeviceConfig.getPath());
                config = JsonFileReader.getJsonObject(androidDeviceConfig);
                logger.info("read config file");
            } catch (IOException e) {
                e.printStackTrace();
            }

            Boolean isStfEnabled = checkStfEnabled(config);

            //check adb + wait for device restart.
            ArrayList<String> attachedDevices = getAttachedDevices();
            logger.info("number of devices attacehed = " + attachedDevices.size());
            
            //sleep is used to try to refresh the adb server which sometimes goes down, we don't want to kill these nodes if killing the adb server brings them back up.
            boolean sleep = false;
            //are any devices not online
            for (Map.Entry<String, String> node : nodes.entrySet()) {
                if (!attachedDevices.contains(node.getKey())) {
                    //stop nodes where the devices aren't running anymore
                    sleep = true;
                    logger.info("killing adb for device " + node.getKey());
                    logger.info("killing adb port " + node.getValue());
                    if(!isStfEnabled) {
                        try {
                            restartADB();
                        } catch (Exception e) {

                        }
                    }

                }
            }
            if(sleep) {
                Thread.sleep(20000);
                attachedDevices = getAttachedDevices();
                Thread.sleep(5000);
                attachedDevices = getAttachedDevices();

                for (Map.Entry<String, String> node : nodes.entrySet()) {
                    if (!attachedDevices.contains(node.getKey())) {
                        //stop nodes where the devices aren't running anymore
                        logger.info("Stopping port for device " + node.getKey());
                        logger.info("Stopping port " + node.getValue());
                        try {
                            stopNodeByPort(node.getValue());
                            nodes.remove(node.getKey());
                        } catch (Exception e) {

                        }
                    }
                }
            }

            if(isStfEnabled)
                checkDevicesStf();

            //logger.debug("Checked devices are not online");
            //if devices are offline try to send reboot? //TODO stop nodes in bad state/restart adb?

            //if new device exists then add
            for (String attachedDevice : attachedDevices) {
                boolean alreadyCreated = false;
                if (!nodes.containsKey(attachedDevice)) {
                    //System.out.println("Attaching device " + attachedDevice);
                    Boolean deviceReady = true;
                    if(isStfEnabled){
                        deviceReady = StfUtilities.checkDeviceStf(attachedDevice, stfURL, stfToken);
                    }
                    if(deviceReady) {
                        Boolean created = createDevice(attachedDevice, config, androidDeviceConfig);
                        //This now happens in create session
                        //if(isStfEnabled&&created)
                          //  StfUtilities.addDeviceInUseStf(attachedDevice, stfURL, stfToken);
                    }
                }

                //if device is already attached and we have stf enabled ensure that the device is checked out in STF
                //we now take the device when we start a session
                /*if (nodes.containsKey(attachedDevice)) {
                    if(isStfEnabled){
                        StfUtilities.addDeviceInUseStf(attachedDevice, stfURL, stfToken);
                    }
                }*/
            }


        }catch(Exception e){e.printStackTrace();logger.debug(e.getMessage());}

        logger.debug("Added new devices");
        try {
            Thread.sleep(20000);
        }catch (Exception e){}
    }

    /*
    check devices attached which are on stf as well
    if the device is not still available for use for automation
    then disconnect the device node
     */
    private void checkDevicesStf() {
        for (Map.Entry<String, String> node : nodes.entrySet()) {
            Boolean deviceAvailableStf = StfUtilities.checkDeviceStf(node.getKey(), stfURL, stfToken);
            if(!deviceAvailableStf) {
                try {
                    //TODO be smarter about this, check if tests are running then kill
                    stopNodeByPort(node.getValue());
                    nodes.remove(node.getKey());
                    //no longer need to remove use in stf as there is a button to take over automation
                    //removeInUseSTF(node.getKey());
                } catch (Exception e) {

                }
            }
        }
    }



    private Boolean checkStfEnabled(JsonObject config) {
        if(config.has("stfInfo")){
            JsonObject stf = config.getAsJsonObject("stfInfo");
            if(stf.has("url")&&stf.has("token")) {
                stfURL = stf.get("url").getAsString();
                stfToken = stf.get("token").getAsString();

                if(stfURL.endsWith("/"))
                    stfURL = stfURL.substring(0,stfURL.length()-1);
                return true;
            }
        }
        return false;
    }

    private void stopNodeByPort(String port) {
        //Run task KillPort
        Map<String, String> parameter = new HashMap<String, String>();
        parameter.put(JsonCodec.OS.KillCommands.PORT, port);
        logger.debug("killing port " + parameter);
        new KillPort().execute(parameter);
    }

    public void restartADB() throws IOException, URISyntaxException {
        //Run task KillPort
        Map<String, String> parameter = new HashMap<String, String>();
        logger.debug("killing adb");
        new KillADB().execute();
    }

    private void createNodeForDevice(File configFile, Map<String, String> deviceInfoToSet, String attachedDevice) throws Exception {

        final JsonObject config = editNodeConfig(JsonFileReader.getJsonObject(configFile), deviceInfoToSet);

        logger.debug(config.get("nodeconfig").toString());
        System.out.println("2 " + config.get("nodeconfig").toString());
        String name = configFile.getName();
        String nameForConfig = attachedDevice;
        if(nameForConfig!=null)
            name = nameForConfig+".json";
        File nodeConfigFile = Env.current().dumpTmpFile(name, config.get("nodeconfig").toString());
        new CreateAndroidAppiumNode(configFile, deviceInfoToSet,  name, nodeConfigFile, config).start();
    }

    private JsonObject editNodeConfig(JsonObject config, Map<String, String> deviceInfo) {
        if(deviceInfo!=null){
            //change Appium Options
            //need to do something smarter with AVD vs udid
            JsonObject cap = config.getAsJsonObject("appiumOptions");
            for(String prop:appiumProperties){
                if(deviceInfo.containsKey(prop)){
                    cap.addProperty(prop, deviceInfo.get(prop));
                }
            }
            //change nodeconfig options


            cap = config.getAsJsonObject("nodeconfig").getAsJsonObject("configuration");
            for(String prop:configProperties){
                if(deviceInfo.containsKey(prop)){
                    cap.addProperty(prop, deviceInfo.get(prop));
                }else if(prop.equals("url")){
                    //System.out.println("contains url");
                    if(deviceInfo.containsKey("port")){
                        //System.out.println("has port");
                        String url = "";
                        try{
                            String host = cap.get("host").getAsString();
                            //System.out.println("host = " + host);
                            url = "http://"+host+":"+deviceInfo.get("port")+"/wd/hub";
                            //System.out.println("url =" + url);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        if(url.equals("")){
                            try{
                                url = cap.get("url").getAsString();
                                url.replaceAll(":[0-9][0-9][0-9][0-9]",":"+deviceInfo.get("port"));
                            }catch(Exception e){
                                e.printStackTrace();
                            }
                        }
                        if(!url.equals(""))
                            cap.addProperty(prop, url);
                    }
                }
            }

            JsonArray capabilities =  config.getAsJsonObject("nodeconfig").getAsJsonArray("capabilities");
            for(JsonElement capability : capabilities) {
                cap = capability.getAsJsonObject();
                for (String prop : capProperties) {
                    if (deviceInfo.containsKey(prop)) {
                        cap.addProperty(prop, deviceInfo.get(prop));
                    }
                }
            }
        }
        return config;
    }

    private boolean createDevice(String attachedDevice, JsonObject config, File file) {
        //does the device exist in the config file
        JsonArray devices = config.getAsJsonArray("devices");

        Map<String, String> deviceInfoToSet = new HashMap<String, String>();

        for(JsonElement device : devices){
            JsonObject deviceInfo = device.getAsJsonObject();
            //check udid

            if(deviceInfo.has("udid")){
                String udid = deviceInfo.get("udid").getAsString();
                if (udid.equals(attachedDevice)){
                    for(String option: deviceOptions) {
                        if (deviceInfo.has(option)) {
                            deviceInfoToSet.put(option, deviceInfo.get(option).getAsString());

                        }
                    }
                    //we have the device information now create a config for it and start it...
                    logger.info("Creating node for " + attachedDevice);
                    //System.out.println("1 " + "Creating node for " + attachedDevice);
                    try{
                        createNodeForDevice(file, deviceInfoToSet, attachedDevice);
                        nodes.put(attachedDevice, deviceInfo.get("port").getAsString());
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    return true;
                }
            }else{
            }
        }
        return false;
    }




    private ArrayList<String> getAttachedDevices() {
        ArrayList<String> attachedDevices = new ArrayList();
        Process cmdProc = null;
        try {
            cmdProc = Runtime.getRuntime().exec("adb devices");
            Thread.sleep(10000);
        } catch (IOException e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        }

        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        //TODO add additional lines for adb.  when it ends in device then the device is online and operational.
        try {
            while ((line = stdoutReader.readLine()) != null) {
                if(line.contains("\tdevice")){
                    attachedDevices.add(line.substring(0,line.indexOf("\tdevice")));
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        }

        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        try {
            while ((line = stderrReader.readLine()) != null) {
                logger.debug(line);
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        }

        try {
            stderrReader.close();
            stdoutReader.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
            //e.printStackTrace();
        }
        int retValue = cmdProc.exitValue();

        return attachedDevices;
    }




    @Override
    protected void afterRun() {

    }
}