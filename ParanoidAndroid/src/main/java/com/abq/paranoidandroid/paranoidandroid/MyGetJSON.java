package com.abq.paranoidandroid.paranoidandroid;


import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;


@TargetApi(Build.VERSION_CODES.CUPCAKE)
class MyGETJSON extends AsyncTask<String, Void, String> {

    public String urlString = "";
    int serverHeight = -1;
    String contactString;
    String messageString;
    String settingString;
    //mSettings = new JSONObject();

    public JSONArray handleContacts(String input) {
        //MainActivity mActivity = new MainActivity();
        try {
            System.out.println("handleContacts ="+input);
            JSONArray json = new JSONArray(input);
            for(int i = 0; i <json.length();i++){
                //mActivity.sendToGlass(json.getJSONObject(i));
                String name = json.getJSONObject(i).getString("contactName");
                String email = json.getJSONObject(i).getString("contactEmail");
                String number = json.getJSONObject(i).getString("contactNumber");
                contactString = "Contact("+i+") Name: "+name + " Number: "+ number + " Email: " + email;
                System.out.println(contactString);
            }
            //mActivity.sendToGlass(json.getJSONObject(1));
            /*
            System.out.println("array="+json);

            contactString = "Contacts:";
            for(int i = 0 ; i < json.length(); i++){
                String name = "contact_" + json.getJSONObject(i).getString("contactName") + "_name";
                String number = "contact_" + json.getJSONObject(i).getString("contactNumber") + "_number";
                String email = "contact_" + json.getJSONObject(i).getString("contactEmail") + "_email";
                //Integer games = json.getJSONObject(i).getInt("games");
                contactString = contactString + "Contact Name: " + name + EOL + "Contact Number " + number + EOL + "Contact Email: "+ email + EOL;
                System.out.println(contactString);
            }
            */
        } catch (Exception e) {
            System.out.println("Exception "+e.getMessage());
        }
       return null;
    }
    /*
    private void handleMessages(String input) {
        try {
            System.out.println("handleMessages ="+input);
            JSONArray json = new JSONArray(input);
            System.out.println("array="+json);
            messageString = "Messages:"+EOL;
            for(int i = 0 ; i < json.length(); i++){
                String message = json.getJSONObject(i).getString("message");
                //Integer games = json.getJSONObject(i).getInt("games");
                messageString = "Message: " + message + EOL;
                System.out.println(messageString);
            }
        } catch (Exception e) {
            System.out.println("Exception "+e.getMessage());
        }
    }
    */


    @Override
    public String doInBackground(String... params) {
        String script = null;
        String urlString = "http://glassbackend-12366.onmodulus.net/api";
        String theUrl = "";
        String responseString = "";
        //contactArray = new JSONArray;
        for(String whatever : params){
            System.out.println("P="+whatever);
            script = whatever;
        }
        try {
            HttpClient httpclient = new DefaultHttpClient();
            if ( script.startsWith("contacts")){
                theUrl = urlString + "/contacts";

            }
            else if(script.startsWith("messages")){
                theUrl = urlString + "/messages";
            }
            else if (script.startsWith("settings")){
                theUrl = urlString + "/glassSettings";
            }
            System.out.println("theUrl="+ theUrl);
            URI website = new URI(theUrl);
            HttpGet get = new HttpGet();
            get.setURI(website);
            HttpResponse response = httpclient.execute(get);
            StatusLine statusLine = response.getStatusLine();
            System.out.println("SL="+statusLine);
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                responseString = out.toString();
                System.out.println("Response\n");
                System.out.println(responseString);
                if ( script.startsWith("contacts"))
                    handleContacts(responseString);
                //if ( script.startsWith("messages")) handleMessages(responseString);
                //if ( script.startsWith("settings")) handleSettings(responseString);
            } else {
                //Closes the connection.
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
            System.out.println("Exception "+e.getMessage());
        }
        return responseString;
    }


    @Override
    protected void onPostExecute(String result) {

    }
}

