package com.company;

import org.apache.commons.io.IOUtils;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.config.TransportStrategy;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;

public class Main {

    private byte[] pdfData = null;
    private int maxMails;
    private String smtpHost;
    private int smtpPort;

    public static void main(String[] args) {
        try {
            if (args.length > 3) {
                new Main().start(args[0], Integer.parseInt(args[1]), args[2], args[3]);
            } else {
                System.err.println("Insufficient parameters!");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void start(String type, int maxMails, String host, String port) throws Exception {
        pdfData = IOUtils.toByteArray(this.getClass().getResourceAsStream("/binary.dat"));
        this.maxMails = maxMails;
        smtpHost = host;
        smtpPort = Integer.parseInt(port);

        long elapsed;

        if (type.equals("sm")) {
            elapsed = sendViaSimpleMail(-1);
        } else if (type.equals("smt")) { // threaded simple-mail
            elapsed = sendViaSimpleMail(10);
        } else {
            elapsed = sendViaJavaMail();
        }

        System.out.println(String.format("***ELAPSED TIME: %.2f seconds", elapsed / 1000d));
    }

    private long sendViaSimpleMail(int threads) {
        System.out.println(String.format("Sending %d mails using SimpleMail...", maxMails));
        long startTime = System.currentTimeMillis();

        Mailer mailer = newMailer(threads);
        for (int i = 0; i < maxMails; i++) {
            mailer.sendMail(newMail(i));
        }

        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        return elapsedTime;
    }

    private Email newMail(int idx) {
        Email email = EmailBuilder.startingBlank()
                .from("testuser@example.org")
                .to("lollypop", "lolly.pop@somemail.com")
                .withReplyTo("lollypop", "lolly.pop@simplemail.com")
                .withSubject("hey this is test #" + idx)
                .withPlainText("This is a sample body text. Not too long.")
                .withAttachment("binary10k.dat", pdfData, "application/octet-stream")
                .withHeader("X-Priority", 5)
                .buildEmail();
        return email;
    }

    private Mailer newMailer(int threads) {
        MailerBuilder.MailerRegularBuilder builder = MailerBuilder
                .withSMTPServer(smtpHost, smtpPort)
                .withTransportStrategy(TransportStrategy.SMTP)
                .withSessionTimeout(10 * 1000)
                .clearEmailAddressCriteria();

        if (threads > 0) {
            System.out.println("**Using " + threads + " threads.");
            builder.withThreadPoolSize(threads);
        }

        return builder.buildMailer();
    }

    private long sendViaJavaMail() throws Exception {
        System.out.println(String.format("Sending %d mails using JavaMail...", maxMails));
        Properties props = System.getProperties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", Integer.toString(smtpPort));
        props.put("mail.smtp.auth", "false");

        long startTime = System.currentTimeMillis();

        final Session session = Session.getInstance(props, null);
        final Transport transport = session.getTransport("smtp");

        for (int i = 0; i < maxMails; i++) {
            sendAttachmentEmail(session, transport, "hey this is test #" + i);
        }

        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        return elapsedTime;
    }

    private void sendAttachmentEmail(Session session, Transport transport, String subject) throws Exception {
        MimeMessage msg = new MimeMessage(session);
        msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
        msg.addHeader("format", "flowed");
        msg.addHeader("Content-Transfer-Encoding", "8bit");

        msg.setFrom(new InternetAddress("no_reply@example.com", "NoReply-JD"));
        msg.setReplyTo(InternetAddress.parse("no_reply@example.com", false));
        msg.setSubject(subject, "UTF-8");
        msg.setSentDate(new Date());
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse("lolly.pop@javamail.com", false));

        // Create the message body part
        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText("This is a sample body text. Not too long.");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);

        // Second part is attachment
        messageBodyPart = new MimeBodyPart();
        final String filename = "binary10k.dat";
        messageBodyPart.setDataHandler(new DataHandler(new DataSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(pdfData);
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return null;
            }

            @Override
            public String getContentType() {
                return "application/octet-stream";
            }

            @Override
            public String getName() {
                return filename;
            }
        }));
        messageBodyPart.setFileName(filename);
        multipart.addBodyPart(messageBodyPart);

        msg.setContent(multipart);

        // Send message
        if (!transport.isConnected())
            transport.connect();

        transport.sendMessage(msg, InternetAddress.parse("lolly.pop@javamail.com", false));
    }

}
