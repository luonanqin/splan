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
        sendEmail("java发送邮件测试2", "测试成功");
    }

    public static void sendEmail(String subject, String message) throws Exception {
        String userName = "1321271684@qq.com";
        String password = "blxcxmcerhxbhbfc";
        Properties properties = new Properties();

        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        //        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.qq.com");
        properties.put("mail.smtp.port", 25);

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password);
            }
        });

        MimeMessage mimeMessage = new MimeMessage(session);

        mimeMessage.addFrom(new InternetAddress[] { new InternetAddress(userName) });
        mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress("qinnanluo@sina.com"));
        mimeMessage.setSubject(subject);
        mimeMessage.setText(message);

        Transport.send(mimeMessage);
    }
}
