package com.company;

import org.apache.commons.io.IOUtils;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.AsyncResponse;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.MailerRegularBuilder;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

        List<Future> futures = new ArrayList<>(maxMails);
        Mailer mailer = newMailer(threads);

        long startTime = System.currentTimeMillis();

        try {
            if (threads > 0) {
                for (int i = 0; i < maxMails; i++) {
                    AsyncResponse resp = mailer.sendMail(newMail(i), true);
                    resp.onException(Throwable::printStackTrace);
                    futures.add(resp.getFuture());
                }
                for (Future future : futures) {
                    future.get();
                }
            } else {
                for (int i = 0; i < maxMails; i++) {
                    mailer.sendMail(newMail(i), false);
                }
            }

            long stopTime = System.currentTimeMillis();
            long elapsedTime = stopTime - startTime;

            // shutdown mailer after elapsing mails send time
            mailer.shutdownConnectionPool().get();

            return elapsedTime;
        } catch (Throwable t) {
            throw new RuntimeException("Sending error!", t);
        }
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
        MailerRegularBuilder builder = MailerBuilder
                .withSMTPServer(smtpHost, smtpPort)
                .withTransportStrategy(TransportStrategy.SMTP)
                .withSessionTimeout(10 * 1000)
                .clearEmailAddressCriteria();
//                .withDebugLogging(true)

        if (threads > 0) {
            builder.resetThreadPoolSize();
            System.out.println("**Using " + builder.getThreadPoolSize() + " threads.");
        } else {
            System.out.println("**Using connection pool.");
            builder.withThreadPoolSize(0);
            // wait max 1 minute for available connection (default forever)
//            builder.withConnectionPoolClaimTimeoutMillis((int) TimeUnit.MINUTES.toMillis(1));
            // keep connections spinning for half an hour (default 5 seconds)
//            builder.withConnectionPoolExpireAfterMillis((int) TimeUnit.MINUTES.toMillis(30));
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

        // variant #1
//        Transport.send(msg);

        // variant #2
//        try (Transport t = session.getTransport()) {
//            t.connect();
//            t.sendMessage(msg, msg.getAllRecipients());
//        } finally {
//            // do nothing
//        }

        // variant #3
        if (!transport.isConnected())
            transport.connect();

        transport.sendMessage(msg, msg.getAllRecipients());
    }

}
