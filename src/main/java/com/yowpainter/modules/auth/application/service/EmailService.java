package com.yowpainter.modules.auth.application.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@yowpainter.com}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String to, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String subject = "Réinitialisation de votre mot de passe - YowPainter";
        String content = "<p>Bonjour,</p>"
                + "<p>Vous avez demandé la réinitialisation de votre mot de passe sur YowPainter.</p>"
                + "<p>Veuillez cliquer sur le lien ci-dessous pour changer votre mot de passe (ce lien expire dans 1 heure) :</p>"
                + "<p><a href=\"" + resetLink + "\">Changer mon mot de passe</a></p>"
                + "<p>Si vous n'avez pas demandé cette réinitialisation, ignorez cet e-mail.</p>"
                + "<p>L'équipe YowPainter</p>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("E-mail de réinitialisation envoyé à {}", to);
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi de l'e-mail à {}", to, e);
            // On log aussi le lien en cas d'échec d'envoi réel pour le dev/test
            log.info("LIEN DE SECOURS (DEBUG) : {}", resetLink);
        }
    }

    public void sendPaymentConfirmation(String to, String reference, java.math.BigDecimal amount) {
        String subject = "Confirmation de paiement - YowPainter";
        String content = "<p>Bonjour,</p>"
                + "<p>Merci pour votre achat sur YowPainter !</p>"
                + "<p>Votre paiement de <strong>" + amount + " XAF</strong> pour la référence <strong>" + reference + "</strong> a été validé.</p>"
                + "<p>Vous pouvez consulter vos achats dans votre espace personnel.</p>"
                + "<p>L'équipe YowPainter</p>";

        sendEmail(to, subject, content);
    }

    public void sendNewSaleNotification(String to, String reference, java.math.BigDecimal amount) {
        String subject = "Nouvelle vente sur YowPainter ! 🎨";
        String content = "<p>Félicitations !</p>"
                + "<p>Vous venez d'effectuer une nouvelle vente sur votre boutique YowPainter.</p>"
                + "<p>Référence : <strong>" + reference + "</strong><br/>"
                + "Montant : <strong>" + amount + " XAF</strong></p>"
                + "<p>Rendez-vous dans votre tableau de bord pour gérer la commande.</p>"
                + "<p>L'équipe YowPainter</p>";

        sendEmail(to, subject, content);
    }

    public void sendEventCancellationEmail(String to, String eventName, String artistName) {
        String subject = "Evenement annule - YowPainter";
        String content = "<p>Bonjour,</p>"
                + "<p>Nous vous informons que l'evenement <strong>" + escapeHtml(eventName) + "</strong>"
                + (artistName != null && !artistName.isBlank()
                ? " organise par <strong>" + escapeHtml(artistName) + "</strong>"
                : "")
                + " a ete annule par l'organisateur.</p>"
                + "<p>Votre reservation et votre billet ne sont plus valides.</p>"
                + "<p>Si vous aviez effectue un paiement, l'organisateur ou notre equipe vous contactera pour le remboursement le cas echeant.</p>"
                + "<p>L'equipe YowPainter</p>";
        sendEmail(to, subject, content);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("E-mail '{}' envoyé à {}", subject, to);
        } catch (MessagingException e) {
            log.error("Erreur l'envoi de l'e-mail '{}' à {}", subject, to, e);
        }
    }
}
