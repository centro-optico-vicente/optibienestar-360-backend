# 09 — SMTP / Email (Spring Mail + Thymeleaf)

## Dependencias

`build.gradle`:
```groovy
implementation 'org.springframework.boot:spring-boot-starter-mail'
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
```

## Configuración

`application.properties`:
```properties
spring.mail.host=${SMTP_HOST}
spring.mail.port=${SMTP_PORT:587}
spring.mail.username=${SMTP_USER}
spring.mail.password=${SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000

mail.from=${MAIL_FROM:noreply@dominio.com}
mail.from-name=${MAIL_FROM_NAME:OptiSalud Plus}
```

## EmailService

`common/service/EmailService.java`:

```java
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationRepository notificationRepository;

    @Value("${mail.from}") private String from;
    @Value("${mail.from-name}") private String fromName;

    @Async
    public CompletableFuture<Boolean> sendTemplated(
        String to,
        String subject,
        String templateName,
        Map<String, Object> variables
    ) {
        try {
            Context context = new Context(new Locale("es", "VE"));
            context.setVariables(variables);
            String html = templateEngine.process(templateName, context);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(msg);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    public void sendSimple(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
```

## NotificationService (cola persistente)

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final EmailService emailService;

    @Transactional
    public void enqueue(NotificationCreateDTO dto) {
        Notification n = new Notification();
        n.setRecipient(dto.recipient());
        n.setChannel("EMAIL");
        n.setTemplate(dto.template());
        n.setPayload(dto.payload());
        n.setStatus("PENDING");
        n.setRetries(0);
        repository.save(n);
        // se procesa async por job
    }

    @Scheduled(fixedDelay = 30_000)  // cada 30s
    public void processPending() {
        List<Notification> pending = repository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");
        for (Notification n : pending) {
            try {
                emailService.sendTemplated(
                    n.getRecipient(),
                    extractSubject(n),
                    n.getTemplate(),
                    n.getPayload()
                ).thenAccept(success -> {
                    if (success) {
                        n.setStatus("SENT");
                        n.setSentAt(Instant.now());
                    } else {
                        n.setRetries(n.getRetries() + 1);
                        if (n.getRetries() >= 3) {
                            n.setStatus("FAILED");
                        }
                    }
                    repository.save(n);
                });
            } catch (Exception e) {
                n.setStatus("FAILED");
                repository.save(n);
            }
        }
    }
}
```

## Templates Thymeleaf

Ubicación: `src/main/resources/templates/`

```
templates/
├── contact-form-received.html      # admin recibe form contacto landing
├── welcome.html                     # nueva membresía
├── payment-received.html
├── payment-approved.html
├── payment-rejected.html
├── payment-reminder.html
├── payment-overdue.html
├── membership-suspended.html
├── membership-expired.html
├── referral-reward.html
├── commission-payout.html
└── shared/
    ├── header.html
    └── footer.html
```

### Ejemplo: `welcome.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="es">
<head>
    <meta charset="UTF-8">
    <title>Bienvenido a OptiSalud Plus</title>
</head>
<body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
    <div th:replace="~{shared/header :: header}"></div>

    <main style="padding: 20px;">
        <h1>¡Bienvenido/a, <span th:text="${memberName}">[Nombre]</span>!</h1>

        <p>Tu membresía OptiSalud Plus está activa.</p>

        <div style="background: #F2F9DF; padding: 15px; border-radius: 8px;">
            <p><strong>Plan:</strong> <span th:text="${planName}">[Plan]</span></p>
            <p><strong>Próxima fecha de pago:</strong> <span th:text="${nextDueDate}">[Fecha]</span></p>
            <p><strong>Tu carnet digital:</strong>
                <a th:href="${cardUrl}" style="color: #245E9E;">Ver carnet</a>
            </p>
        </div>

        <p>Cualquier consulta: <a href="mailto:hola@dominio.com">hola@dominio.com</a></p>
    </main>

    <div th:replace="~{shared/footer :: footer}"></div>
</body>
</html>
```

## Async config

`core/config/AsyncConfig.java`:

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "emailExecutor")
    Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
```

## Jobs scheduleados que enqueuean notificaciones

`notifications/service/ReminderJobService.java`:

```java
@Service
@RequiredArgsConstructor
public class ReminderJobService {

    private final MembershipRepository membershipRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *")  // 9 AM diario
    public void sendPaymentReminders() {
        LocalDate target = LocalDate.now().plusDays(3);
        List<Membership> upcoming = membershipRepository.findByNextDueDateAndStatus(target, "ACTIVE");
        for (Membership m : upcoming) {
            notificationService.enqueue(NotificationCreateDTO.builder()
                .recipient(m.getMember().getEmail())
                .template("payment-reminder")
                .payload(Map.of(
                    "memberName", m.getMember().getFullName(),
                    "amount", m.getPlan().getMonthlyFee(),
                    "dueDate", target.toString()
                ))
                .build());
        }
    }

    @Scheduled(cron = "0 0 10 * * *")  // 10 AM diario
    public void sendOverdueAlerts() {
        // similar, para vencidos en gracia
    }
}
```

## Variables de entorno

```
SMTP_HOST=smtp.gmail.com  o  email-smtp.us-east-1.amazonaws.com (SES) o servidor local
SMTP_PORT=587
SMTP_USER=...
SMTP_PASSWORD=...
MAIL_FROM=noreply@dominio.com
MAIL_FROM_NAME=OptiSalud Plus
```

## Decisión SMTP provider

Ver ADR pendiente (Tarea 1.8): comparar SMTP self-hosted (Postfix), Mailtrap (limitado), AWS SES (pay-as-you-go).

Para volúmenes de FASE 5 (~1.500 emails/día = ~45K/mes), SES está en pay-as-you-go free tier hasta 62K/mes desde EC2 — o $0.10/1000 emails general.

## Testing

Para tests: usar **GreenMail** (servidor SMTP en memoria):

```groovy
testImplementation 'com.icegreen:greenmail-spring:2.0.0'
```

```java
@SpringBootTest
class EmailServiceTest {
    @RegisterExtension static GreenMailExtension greenMail =
        new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired EmailService service;

    @Test
    void sendsTemplatedEmail() throws Exception {
        service.sendTemplated("test@x.com", "Test", "welcome", Map.of("memberName", "Juan")).get();
        assertEquals(1, greenMail.getReceivedMessages().length);
    }
}
```

## Referencias

- Spring Email docs
- Thymeleaf Spring integration
- Templates en `src/main/resources/templates/`
