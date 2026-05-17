package com.jobemailer;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Properties;

@Component
public class EmailSender {
    private final JobEmailerProperties properties;

    public EmailSender(JobEmailerProperties properties) {
        this.properties = properties;
    }

    public void sendEmail(String to, String subject, String body, String attachmentPath) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", properties.getSmtpHost());
        props.put("mail.smtp.port", properties.getSmtpPort());
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", properties.getSmtpPort());
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(properties.getSmtpEmail(), properties.getSmtpPassword());
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(properties.getSmtpEmail()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(body, "UTF-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);

        if (attachmentPath != null && !attachmentPath.isEmpty()) {
            File attachmentFile = new File(attachmentPath);
            if (!attachmentFile.exists()) {
                throw new IllegalStateException("Attachment file not found: " + attachmentPath);
            }
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(attachmentFile);
            multipart.addBodyPart(attachmentPart);
        }

        message.setContent(multipart);
        Transport.send(message);
    }
}
