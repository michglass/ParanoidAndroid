package com.abq.paranoidandroid.paranoidandroid;


import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Email {
    String TAG = "Email Activity";
    Button btnSendSMS;
    private static final String username = "mglass481@gmail.com";
    private static final String password = "eecs4812014";
    private String emailAddress;
    private String subject;
    private String message;
    private Context mContext;

    public Email(String emailAddress, String subject, String message, Context mContext) {
        this.emailAddress = emailAddress;
        this.subject = subject;
        this.message = message;
        this.mContext = mContext;
    }
    public void sendEmail(){
        sendMail(emailAddress, subject, message);
    }

    /** Called when the activity is first created. */
/*    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button sendButton = (Button) findViewById(R.id.email);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v(TAG, "email button was clicked");
                String email = "danfrancken@gmail.com";
                String subject = "Send From Google Glass";
                String message = "You're a homosexual";

            }
        });

        btnSendSMS = (Button) findViewById(R.id.message);
        btnSendSMS.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage("7346459032", null, "Hi You got a message!", null, null);
            }
        });
    }
    */
    private void sendMail(String email, String subject, String messageBody) {
        Session session = createSessionObject();
        Log.v(TAG, "THIS HAPPENS!");
        try {
            Message message = createMessage(email, subject, messageBody, session);
            new SendMailTask().execute(message);
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }



    private Message createMessage(String email, String subject, String messageBody, Session session) throws MessagingException, UnsupportedEncodingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("googleGlass@umich.edu", "The google glass team"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(email, email));
        message.setSubject(subject);
        message.setText(messageBody);
        return message;
    }

    private Session createSessionObject() {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");

        return Session.getInstance(properties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    private class SendMailTask extends AsyncTask<Message, Void, Void> {
        //private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //progressDialog = ProgressDialog.show(mContext, "Please wait", "Sending mail", true, false);
            Toast toast = Toast.makeText(mContext, "Sending Email", Toast.LENGTH_SHORT);
            toast.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            //progressDialog.dismiss();
        }

        @Override
        protected Void doInBackground(Message... messages) {
            try {
                Transport.send(messages[0]);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}