package luonq.test;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Created by Luonanqin on 2023/3/26.
 */
public class Test2 {

    public static void main(String[] args) throws Exception {
        //        sendEmail("smtp.163.com", 465, "qinnanluo@163.com", "chusan5ban", "qinnanluo@sina.com", "java发送邮件测试", "测试成功");
//        sendEmail("java发送邮件测试2", "测试成功");
        send("qinnanluo@gmail.com", "chusan5ban", "qinnanluo@sina.com", "test", "test");
    }

    public static void sendEmail(String subject, String message) throws Exception {
        //        String userName = "1321271684@qq.com";
        //        String password = "blxcxmcerhxbhbfc";
        String userName = "qinnanluo@gmail.com";
        String password = "mukmspnabamhqxsf";
        Properties properties = new Properties();

        //        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password);
            }
        });

        MimeMessage mimeMessage = new MimeMessage(session);

        mimeMessage.addFrom(new InternetAddress[] { new InternetAddress(userName) });
        mimeMessage.addRecipients(Message.RecipientType.TO, InternetAddress.parse("qinnanluo@sina.com"));
        mimeMessage.setSubject(subject);
        mimeMessage.setText(message);

        Transport.send(mimeMessage);
    }

    public static void send(String from, String password, String to, String sub, String msg) {
        //Get properties object
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class",
          "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", 465);
        //get Session
        Session session = Session.getDefaultInstance(props,
          new javax.mail.Authenticator() {
              protected PasswordAuthentication getPasswordAuthentication() {
                  return new PasswordAuthentication(from, password);
              }
          });
        //compose message
        try {
            MimeMessage message = new MimeMessage(session);
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(sub);
            message.setText(msg);
            //send message
            Transport.send(message);
            System.out.println("message sent successfully");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
