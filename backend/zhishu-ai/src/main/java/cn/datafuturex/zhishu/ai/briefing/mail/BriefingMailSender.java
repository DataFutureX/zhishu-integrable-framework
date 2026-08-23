package cn.datafuturex.zhishu.ai.briefing.mail;

import cn.datafuturex.zhishu.ai.briefing.mail.SysConfigMailSettingsService.MailSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 简报邮件发送（jakarta.mail，配置来自 sys_config）。
 */
@Service
@ConditionalOnBean(SysConfigMailSettingsService.class)
@RequiredArgsConstructor
@Slf4j
public class BriefingMailSender {

    private final SysConfigMailSettingsService mailSettingsService;

    public void send(String to, String subject, String textBody) {
        MailSettings settings = mailSettingsService.getMailSettings();
        if (!settings.enabled()) {
            throw new IllegalStateException("邮件服务未启用，请先在系统设置中开启邮件服务");
        }
        if (!StringUtils.hasText(settings.host()) || !StringUtils.hasText(settings.from())) {
            throw new IllegalStateException("邮件配置不完整，请完善 SMTP 主机与发件人");
        }
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("收件人不能为空");
        }
        try {
            java.util.Properties props = new java.util.Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", settings.host());
            props.put("mail.smtp.port", String.valueOf(settings.port() != null ? settings.port() : 465));
            if (settings.ssl()) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            if (settings.starttls()) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            final String username = settings.username();
            final String password = settings.password();
            jakarta.mail.Session session = jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(username, password);
                }
            });
            jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(session);
            message.setFrom(new jakarta.mail.internet.InternetAddress(settings.from()));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    jakarta.mail.internet.InternetAddress.parse(to.trim()));
            message.setSubject(subject != null ? subject : "【AI简报】", "UTF-8");
            message.setText(textBody != null ? textBody : "", "UTF-8");
            jakarta.mail.Transport.send(message);
            log.info("简报邮件已发送 to={}", to);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("简报邮件发送失败: {}", e.getMessage(), e);
            throw new IllegalStateException("简报邮件发送失败: " + e.getMessage(), e);
        }
    }
}
