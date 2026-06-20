package com.financialapigateway.fraudservice.service;

import com.financialapigateway.fraudservice.enumeration.Reason;
import com.financialapigateway.fraudservice.enumeration.Severity;
import com.financialapigateway.fraudservice.event.TransactionEvent;
import com.financialapigateway.fraudservice.feignclient.AccountClient;
import com.financialapigateway.fraudservice.feignclient.TransactionClient;
import com.financialapigateway.fraudservice.repository.FraudAlertRepository;
import com.financialapigateway.fraudservice.response.AccountResponse;
import com.financialapigateway.fraudservice.response.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
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
        ReflectionTestUtils.setField(fraudAlertService, "largeAmountThreshold", 10000f);
        ReflectionTestUtils.setField(fraudAlertService, "frequencyLimit", 10f);
        ReflectionTestUtils.setField(fraudAlertService, "frequencyWindow", 10);
    }

    @Test
    void shouldFlagLargeAmountTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(15000f);

        // event shouldn't be already flagged
        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(argThat(alert ->
                alert.getReason() == Reason.LARGE_AMOUNT
                && alert.getSeverity() == Severity.HIGH
        ));
        verifyNoInteractions(this.accountClient, this.transactionClient);
    }

    @Test
    void shouldFlagHighPercentageTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(90f);

        AccountResponse sender = new AccountResponse();
        sender.setBalance(100f); // 90 / 100 = 0.9 >= 0.8 will flag high percentage

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(argThat(alert ->
                alert.getReason() == Reason.SUSPICIOUS_PATTERN
                && alert.getSeverity() == Severity.MEDIUM
        ));

        verifyNoInteractions(this.transactionClient);
    }

    @Test
    void shouldFlagUnusualFrequenciesTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(5f);
        event.setCreatedAt(LocalDateTime.now());

        AccountResponse sender = new AccountResponse();
        sender.setBalance(100f);

        List<TransactionResponse> transactions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            TransactionResponse transaction = new TransactionResponse();
            transaction.setCreationDate(LocalDateTime.now().minusMinutes(1));
            transactions.add(transaction);
        }

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);
        when(this.transactionClient.getAllTransactionsByAccountNumber(event.getSenderId(), null)).thenReturn(transactions);

        this.fraudAlertService.processTransaction(event);

        verify(this.fraudAlertRepository).save(argThat(alert ->
                alert.getReason() == Reason.UNUSUAL_FREQUENCY
                && alert.getSeverity() == Severity.LOW
        ));
    }

    @Test
    void shouldNotFlagNormalAmountTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(5f);

        AccountResponse sender = new AccountResponse();
        sender.setBalance(1000f);

        when(this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())).thenReturn(false);
        when(this.accountClient.getAccountByAccountNumber(event.getSenderId())).thenReturn(sender);
        when(this.transactionClient.getAllTransactionsByAccountNumber(event.getSenderId(), null)).thenReturn(List.of());

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
}
