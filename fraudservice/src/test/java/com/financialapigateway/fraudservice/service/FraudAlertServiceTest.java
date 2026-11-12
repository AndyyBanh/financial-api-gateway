package com.financialapigateway.fraudservice.service;

import com.financialapigateway.fraudservice.entity.FraudAlert;
import com.financialapigateway.fraudservice.enumeration.Reason;
import com.financialapigateway.fraudservice.enumeration.Severity;
import com.financialapigateway.fraudservice.event.TransactionEvent;
import com.financialapigateway.fraudservice.feignclient.AccountClient;
import com.financialapigateway.fraudservice.feignclient.TransactionClient;
import com.financialapigateway.fraudservice.repository.FraudAlertRepository;
import com.financialapigateway.fraudservice.response.AccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FraudAlertServiceTest {

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private TransactionClient transactionClient;

    @InjectMocks
    private FraudAlertService fraudAlertService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fraudAlertService, "largeAmountThreshold", new BigDecimal("10000"));
        ReflectionTestUtils.setField(fraudAlertService, "frequencyLimit", 10f);
        ReflectionTestUtils.setField(fraudAlertService, "frequencyWindow", 10);
    }

    @Test
    void shouldFlagLargeAmountTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("15000"));

        // event shouldn't be already flagged
        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(alertWith(Reason.LARGE_AMOUNT, Severity.HIGH));
        verifyNoInteractions(this.accountClient, this.transactionClient);
    }

    @Test
    void shouldFlagHighPercentageTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("90"));

        AccountResponse sender = new AccountResponse();
        sender.setBalance(new BigDecimal("100"));

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(alertWith(Reason.SUSPICIOUS_PATTERN, Severity.MEDIUM));

        verifyNoInteractions(this.transactionClient);
    }

    @Test
    void shouldFlagExactlyEightyPercentTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("80.00"));

        AccountResponse sender = new AccountResponse();
        sender.setBalance(new BigDecimal("100.00"));

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(alertWith(Reason.SUSPICIOUS_PATTERN, Severity.MEDIUM));
        verifyNoInteractions(this.transactionClient);
    }

    @Test
    void shouldFlagHighPercentageWhenSenderBalanceIsZero() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("5"));

        AccountResponse sender = new AccountResponse();
        sender.setBalance(BigDecimal.ZERO);

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(alertWith(Reason.SUSPICIOUS_PATTERN, Severity.MEDIUM));
        verifyNoInteractions(this.transactionClient);
    }

    @Test
    void shouldFlagUnusualFrequenciesTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("5"));
        event.setCreatedAt(LocalDateTime.now());

        AccountResponse sender = new AccountResponse();
        sender.setBalance(new BigDecimal("100"));

        LocalDateTime before = LocalDateTime.now();
        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);
        when(this.transactionClient.countSentTransactionsSince(eq(event.getSenderId()), anyString())).thenReturn(10L);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(alertWith(Reason.UNUSUAL_FREQUENCY, Severity.LOW));
        // Asks for the count over the last 10 minutes (frequencyWindow)
        verify(this.transactionClient).countSentTransactionsSince(eq(event.getSenderId()), argThat(since -> {
            LocalDateTime windowStart = LocalDateTime.parse(since);
            return !windowStart.isBefore(before.minusMinutes(10)) && !windowStart.isAfter(LocalDateTime.now().minusMinutes(10));
        }));
    }

    @Test
    void shouldNotFlagNormalAmountTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("5"));

        AccountResponse sender = new AccountResponse();
        sender.setBalance(new BigDecimal("1000"));

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);
        when(this.transactionClient.countSentTransactionsSince(eq(event.getSenderId()), anyString())).thenReturn(9L);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository, never()).save(any());
    }

    @Test
    void shouldSkipIfAlertAlreadyExists() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(true);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository, never()).save(any());
        verifyNoInteractions(this.accountClient, this.transactionClient);
    }

    private static FraudAlert alertWith(Reason reason, Severity severity) {
        return argThat(alert -> alert.getReason() == reason && alert.getSeverity() == severity);
    }
}
